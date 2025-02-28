package com.example.tag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tracks player tier history over time
 */
public class PlayerHistoryTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File HISTORY_FILE = FabricLoader.getInstance().getConfigDir().resolve("is-tier-tagger-history.json").toFile();
    private final Logger logger;

    // Map of player UUID to their historical data
    private Map<String, PlayerHistory> playerHistories = new HashMap<>();

    public PlayerHistoryTracker(Logger logger) {
        this.logger = logger;
        loadHistory();
    }

    /**
     * Stores player tier data for a given point in time
     */
    public static class TierSnapshot {
        private final long timestamp;
        private final String tier;
        private final int points;
        private final String gameMode;

        public TierSnapshot(long timestamp, String tier, int points, String gameMode) {
            this.timestamp = timestamp;
            this.tier = tier;
            this.points = points;
            this.gameMode = gameMode;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getTier() {
            return tier;
        }

        public int getPoints() {
            return points;
        }

        public String getGameMode() {
            return gameMode;
        }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            Date date = new Date(timestamp);
            return sdf.format(date);
        }
    }

    /**
     * Stores all historical data for a single player
     */
    public static class PlayerHistory {
        private final String uuid;
        private final String username;
        private final Map<String, List<TierSnapshot>> gameModeHistory = new HashMap<>();

        public PlayerHistory(String uuid, String username) {
            this.uuid = uuid;
            this.username = username;

            // Initialize lists for each game mode
            gameModeHistory.put("crystal", new ArrayList<>());
            gameModeHistory.put("sword", new ArrayList<>());
            gameModeHistory.put("uhc", new ArrayList<>());
            gameModeHistory.put("pot", new ArrayList<>());
            gameModeHistory.put("smp", new ArrayList<>());
        }

        public String getUuid() {
            return uuid;
        }

        public String getUsername() {
            return username;
        }

        public Map<String, List<TierSnapshot>> getGameModeHistory() {
            return gameModeHistory;
        }

        /**
         * Add a new tier snapshot for a specific game mode
         */
        public void addTierSnapshot(String gameMode, TierSnapshot snapshot) {
            if (!gameModeHistory.containsKey(gameMode)) {
                gameModeHistory.put(gameMode, new ArrayList<>());
            }

            List<TierSnapshot> history = gameModeHistory.get(gameMode);

            // Check if we already have a snapshot with the same tier for this day
            boolean skipAdd = false;
            if (!history.isEmpty()) {
                TierSnapshot lastSnapshot = history.get(history.size() - 1);

                // Convert timestamps to dates to compare just the day
                Calendar cal1 = Calendar.getInstance();
                Calendar cal2 = Calendar.getInstance();
                cal1.setTimeInMillis(lastSnapshot.getTimestamp());
                cal2.setTimeInMillis(snapshot.getTimestamp());

                boolean sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);

                // If same day and same tier, don't add a new entry
                if (sameDay && lastSnapshot.getTier().equals(snapshot.getTier())) {
                    skipAdd = true;
                }
            }

            if (!skipAdd) {
                history.add(snapshot);

                // Keep history size manageable (limit to last 30 entries per game mode)
                if (history.size() > 30) {
                    history.remove(0);
                }
            }
        }

        /**
         * Get tier snapshots for a game mode
         */
        public List<TierSnapshot> getTierSnapshots(String gameMode) {
            return gameModeHistory.getOrDefault(gameMode, new ArrayList<>());
        }
    }

    /**
     * Record player data from API response
     */
    public void recordPlayerData(String uuid, String username, JsonObject playerData) {
        try {
            if (playerData == null) {
                logger.error("Failed to record player data - null data");
                return;
            }

            // Log the playerData structure to debug
            logger.debug("Recording player data for {}: {}", username, playerData.toString().substring(0, Math.min(100, playerData.toString().length())));

            // Check if playerData has userData
            if (!playerData.has("userData")) {
                logger.error("Player data missing userData field");
                return;
            }

            JsonElement userDataElement = playerData.get("userData");
            if (!userDataElement.isJsonObject()) {
                logger.error("userData is not a JsonObject");
                return;
            }

            JsonObject userData = userDataElement.getAsJsonObject();

            // Check if userData has stats
            if (!userData.has("stats")) {
                logger.error("userData missing stats field");
                return;
            }

            JsonElement statsElement = userData.get("stats");
            if (!statsElement.isJsonArray()) {
                logger.error("stats is not a JsonArray");
                return;
            }

            JsonArray stats = statsElement.getAsJsonArray();

            if (stats.size() == 0) {
                logger.error("stats array is empty");
                return;
            }

            // Get or create player history
            PlayerHistory history = playerHistories.getOrDefault(uuid, new PlayerHistory(uuid, username));

            JsonElement statsObjectElement = stats.get(0);
            if (!statsObjectElement.isJsonObject()) {
                logger.error("stats[0] is not a JsonObject");
                return;
            }

            JsonObject gameStats = statsObjectElement.getAsJsonObject();
            IsrealTiersApiService apiService = new IsrealTiersApiService(logger);
            long currentTime = System.currentTimeMillis();

            // Process each game mode
            for (String gameMode : new String[]{"crystal", "sword", "uhc", "pot", "smp"}) {
                if (gameStats.has(gameMode)) {
                    JsonElement modeStatsElement = gameStats.get(gameMode);

                    if (!modeStatsElement.isJsonArray()) {
                        logger.debug("Game mode {} is not a JsonArray or does not exist", gameMode);
                        continue;
                    }

                    JsonArray modeStats = modeStatsElement.getAsJsonArray();

                    if (modeStats.size() > 0) {
                        JsonElement statElement = modeStats.get(0);

                        if (!statElement.isJsonObject()) {
                            logger.debug("Game mode {} stats[0] is not a JsonObject", gameMode);
                            continue;
                        }

                        JsonObject stat = statElement.getAsJsonObject();

                        if (stat.has("tier")) {
                            String tier = stat.get("tier").getAsString();

                            if (!tier.isEmpty()) {
                                int points = apiService.getPointsForTier(tier);

                                // Create and add a new snapshot
                                TierSnapshot snapshot = new TierSnapshot(currentTime, tier, points, gameMode);
                                history.addTierSnapshot(gameMode, snapshot);

                                logger.debug("Added tier snapshot for {}, game mode {}: {}", username, gameMode, tier);
                            }
                        }
                    }
                }
            }

            // Save the player history
            playerHistories.put(uuid, history);
            saveHistory();

        } catch (Exception e) {
            logger.error("Error recording player history data", e);
        }
    }

    /**
     * Get player history by UUID
     */
    public PlayerHistory getPlayerHistory(String uuid) {
        return playerHistories.get(uuid);
    }

    /**
     * Load history from file
     */
    private void loadHistory() {
        if (HISTORY_FILE.exists()) {
            try (FileReader reader = new FileReader(HISTORY_FILE)) {
                Type type = new TypeToken<Map<String, PlayerHistory>>(){}.getType();
                Map<String, PlayerHistory> loaded = GSON.fromJson(reader, type);

                if (loaded != null) {
                    playerHistories = loaded;
                    logger.info("Loaded history data for {} players", playerHistories.size());
                } else {
                    logger.warn("Loaded history data was null, starting with empty history");
                    playerHistories = new HashMap<>();
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                logger.error("Failed to load player history", e);
                playerHistories = new HashMap<>();
            }
        } else {
            logger.info("No history file found, starting with empty history");
            playerHistories = new HashMap<>();
        }
    }

    /**
     * Save history to file
     */
    public void saveHistory() {
        try {
            if (!HISTORY_FILE.exists()) {
                HISTORY_FILE.getParentFile().mkdirs();
                HISTORY_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                GSON.toJson(playerHistories, writer);
            }

            logger.info("Saved history data for {} players", playerHistories.size());
        } catch (IOException e) {
            logger.error("Failed to save player history", e);
        }
    }

    /**
     * Get highest tier for a player across all game modes
     */
    public String getHighestTier(String uuid) {
        PlayerHistory history = playerHistories.get(uuid);
        if (history == null) {
            return null;
        }

        String highestTier = null;
        int highestPoints = -1;
        IsrealTiersApiService apiService = new IsrealTiersApiService(logger);

        for (String gameMode : new String[]{"crystal", "sword", "uhc", "pot", "smp"}) {
            List<TierSnapshot> snapshots = history.getTierSnapshots(gameMode);
            if (!snapshots.isEmpty()) {
                TierSnapshot latest = snapshots.get(snapshots.size() - 1);
                int points = latest.getPoints();

                if (points > highestPoints) {
                    highestPoints = points;
                    highestTier = latest.getTier();
                }
            }
        }

        return highestTier;
    }
}