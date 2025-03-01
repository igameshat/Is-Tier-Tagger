package com.example.tag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.function.BiConsumer;

/**
 * Service class to handle all API requests to Israel Tiers and Mojang APIs
 */
public class IsrealTiersApiService {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;

    private final Logger logger;
    private final PlayerDataCache cache;

    // Special UUID for hardcoded player data
    private static final String SPECIAL_UUID = "ca10edbe-9313-4fb1-95ee-534c2fed5f02";

    // Tier points mapping
    private static final JsonObject TIER_POINTS = GSON.fromJson(
            "{ \"HT1\": 60, \"LT1\": 44, \"HT2\": 28, \"LT2\": 16, \"HT3\": 10, \"LT3\": 6, \"HT4\": 4, \"LT4\": 3, \"HT5\": 2, \"LT5\": 1, \"LT69\": 69 }",
            JsonObject.class
    );

    public IsrealTiersApiService(Logger logger) {
        this.logger = logger;
        this.cache = new PlayerDataCache(logger);

        // Initialize HTTP client with timeout from config
        ModConfig config = ModConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .build();
    }

    /**
     * Create an API request to the Israel Tiers API
     */
    private HttpRequest.Builder createApiRequest(String uuid) {
        return HttpRequest.newBuilder()
                .uri(URI.create("https://israeltiers.com/api/user/" + uuid))
                .header("accept", "application/json, text/plain, */*")
                .header("accept-language", "en-US,en;q=0.9")
                .header("referer", "https://israeltiers.com/p/" + uuid)
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36")
                .timeout(Duration.ofSeconds(20));
    }

    /**
     * Fetch UUID from username using Mojang API
     */
    public String fetchUUID(String username) throws Exception {
        // Check cache first
        String cachedUuid = cache.getCachedUUID(username);
        if (cachedUuid != null) {
            logger.debug("Using cached UUID for {}: {}", username, cachedUuid);
            return cachedUuid;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject jsonObject = GSON.fromJson(response.body(), JsonObject.class);
            String uuid = jsonObject.get("id").getAsString();
            uuid = uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");

            // Cache the result
            cache.cacheUUID(username, uuid);

            return uuid;
        }
        return null;
    }

    /**
     * Fetch username from UUID using Mojang API
     */
    public String fetchUsernameFromUUID(String uuid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/user/profile/" + uuid))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject profile = GSON.fromJson(response.body(), JsonObject.class);
            return profile.get("name").getAsString();
        }
        return uuid;
    }

    /**
     * Generate hardcoded player data with LT69 tier for the special UUID
     */
    private JsonObject generateHardcodedPlayerData(String uuid, String username) {
        // Current timestamp in seconds
        long timestamp = System.currentTimeMillis() / 1000;

        // Create empty player data structure
        JsonObject playerData = new JsonObject();
        playerData.addProperty("id", uuid);

        // Create userData object
        JsonObject userData = new JsonObject();
        userData.addProperty("discordId", "42069");

        // Create stats array with a single item
        JsonArray stats = new JsonArray();
        JsonObject gameStats = new JsonObject();

        // Add tier data for each game mode
        for (String gameMode : new String[]{"crystal", "sword", "uhc", "pot", "smp"}) {
            JsonArray modeStats = new JsonArray();
            JsonObject tierData = new JsonObject();
            tierData.addProperty("tier", "LT69");
            tierData.addProperty("lastupdate", String.valueOf(timestamp));
            modeStats.add(tierData);
            gameStats.add(gameMode, modeStats);
        }

        stats.add(gameStats);
        userData.add("stats", stats);
        playerData.add("userData", userData);

        logger.info("Generated hardcoded LT69 player data for {}", username);

        return playerData;
    }

    /**
     * Fetch player data from Israel Tiers API
     * @param uuid Player UUID
     * @param callback Callback with the fetched data and success status
     */
    public void fetchPlayerData(String uuid, BiConsumer<JsonObject, Boolean> callback) {
        // Check for special UUID for hardcoded player data
        if (SPECIAL_UUID.equalsIgnoreCase(uuid)) {
            try {
                String username = fetchUsernameFromUUID(uuid);
                JsonObject hardcodedData = generateHardcodedPlayerData(uuid, username);

                // Cache the hardcoded data
                cache.cachePlayerData(uuid, hardcodedData);

                // Return via callback
                callback.accept(hardcodedData, true);
                return;
            } catch (Exception e) {
                logger.error("Error generating hardcoded player data", e);
                // Fall through to normal API request if hardcoding fails
            }
        }

        // Check cache first
        JsonObject cachedData = cache.getCachedPlayerData(uuid);
        if (cachedData != null) {
            logger.debug("Using cached player data for {}", uuid);
            callback.accept(cachedData, true);
            return;
        }

        try {
            HttpRequest request = createApiRequest(uuid)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject data = GSON.fromJson(response.body(), JsonObject.class);

                // Cache the result
                cache.cachePlayerData(uuid, data);

                callback.accept(data, true);
            } else {
                callback.accept(null, false);
            }
        } catch (Exception e) {
            logger.error("Error fetching player data", e);
            callback.accept(null, false);
        }
    }

    /**
     * Fetch tier list from Israel Tiers API
     * @param filter Game mode filter
     * @param callback Callback with the fetched tiers and success status
     */
    public void fetchTierList(String filter, BiConsumer<JsonArray, Boolean> callback) {
        // Check cache first
        Object cachedTierList = cache.getCachedTierList(filter);
        if (cachedTierList != null) {
            logger.debug("Using cached tier list for filter {}", filter);
            JsonArray tierList = (JsonArray) cachedTierList;

            // Always ensure our special player is in cached results too
            ensureSpecialPlayerInTierList(tierList, filter);

            callback.accept(tierList, true);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.israeltiers.com/api/tiers?filter=" + filter))
                    .header("accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray tiers = GSON.fromJson(response.body(), JsonArray.class);

                // Add our special player to the tier list
                ensureSpecialPlayerInTierList(tiers, filter);

                // Cache the result
                cache.cacheTierList(filter, tiers);

                callback.accept(tiers, true);
            } else {
                callback.accept(null, false);
            }
        } catch (Exception e) {
            logger.error("Error fetching tier list", e);
            callback.accept(null, false);
        }
    }

    /**
     * Ensure special player is in the tier list
     * This is the simplest approach - just add them at the beginning
     */
    private void ensureSpecialPlayerInTierList(JsonArray tiers, String filter) {
        try {
            // First check if player already exists - if so, update their tier
            for (int i = 0; i < tiers.size(); i++) {
                JsonObject player = tiers.get(i).getAsJsonObject();
                if (player.has("minecraftUUID") &&
                        SPECIAL_UUID.equalsIgnoreCase(player.get("minecraftUUID").getAsString())) {

                    // Player exists - make sure they have LT69 tier
                    updatePlayerTierToLT69(player, filter);

                    // If they're not at index 0, we need to create a new list
                    if (i > 0) {
                        // Get username
                        String username = player.has("username") ?
                                player.get("username").getAsString() : "SpecialPlayer";

                        // Remove from current position by creating new array
                        JsonArray newTiers = new JsonArray();

                        // Add special player first
                        newTiers.add(player);

                        // Then add all other players
                        for (int j = 0; j < tiers.size(); j++) {
                            if (j != i) { // Skip the player we already added
                                newTiers.add(tiers.get(j));
                            }
                        }

                        // Replace the original tier list by modifying each index
                        for (int j = 0; j < tiers.size(); j++) {
                            if (j < newTiers.size()) {
                                // Replace with new element
                                tiers.set(j, newTiers.get(j));
                            }
                        }
                    }

                    return;
                }
            }

            // Player doesn't exist - create and add at beginning
            String username;
            try {
                username = fetchUsernameFromUUID(SPECIAL_UUID);
            } catch (Exception e) {
                logger.error("Error fetching username for special UUID", e);
                username = "SpecialPlayer";
            }

            // Create player object
            JsonObject specialPlayer = new JsonObject();
            specialPlayer.addProperty("minecraftUUID", SPECIAL_UUID);
            specialPlayer.addProperty("username", username);

            // Add tier data
            JsonArray filterArray = new JsonArray();
            JsonObject tierData = new JsonObject();
            tierData.addProperty("tier", "LT69");
            tierData.addProperty("lastupdate", String.valueOf(System.currentTimeMillis() / 1000));
            filterArray.add(tierData);
            specialPlayer.add(filter, filterArray);

            // Create new array with special player at the beginning
            JsonArray newTiers = new JsonArray();
            newTiers.add(specialPlayer);

            // Add all existing players
            for (int i = 0; i < tiers.size(); i++) {
                newTiers.add(tiers.get(i));
            }

            // Replace the original tier list content
            // First, ensure we have the right number of elements
            while (tiers.size() < newTiers.size()) {
                // Add dummy elements if needed to make the arrays the same size
                tiers.add(new JsonObject());
            }

            // Now replace each element
            for (int i = 0; i < newTiers.size(); i++) {
                tiers.set(i, newTiers.get(i));
            }

            logger.info("Added special player to {} tier list", filter);
        } catch (Exception e) {
            logger.error("Error adding special player to tier list", e);
        }
    }

    /**
     * Update a player's tier to LT69
     */
    private void updatePlayerTierToLT69(JsonObject player, String filter) {
        try {
            // Check if player has the requested filter
            if (!player.has(filter) || !player.get(filter).isJsonArray()) {
                // Create new filter array
                JsonArray filterArray = new JsonArray();
                JsonObject tierData = new JsonObject();
                tierData.addProperty("tier", "LT69");
                tierData.addProperty("lastupdate", String.valueOf(System.currentTimeMillis() / 1000));
                filterArray.add(tierData);
                player.add(filter, filterArray);
            } else {
                // Update existing filter array
                JsonArray filterArray = player.getAsJsonArray(filter);
                if (filterArray.size() > 0) {
                    JsonObject tierData = filterArray.get(0).getAsJsonObject();
                    tierData.addProperty("tier", "LT69");
                } else {
                    // Array exists but is empty
                    JsonObject tierData = new JsonObject();
                    tierData.addProperty("tier", "LT69");
                    tierData.addProperty("lastupdate", String.valueOf(System.currentTimeMillis() / 1000));
                    filterArray.add(tierData);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating player tier", e);
        }
    }

    /**
     * Get points for a tier
     */
    public int getPointsForTier(String tier) {
        try {
            if (tier != null && !tier.isEmpty() && TIER_POINTS.has(tier)) {
                return TIER_POINTS.get(tier).getAsInt();
            }
        } catch (Exception e) {
            logger.error("Error getting points for tier: {}", tier, e);
        }
        return 0;
    }

    public void clearCaches() {
        cache.clearAllCaches();
    }

    public String getCacheStats() {
        return cache.getStatistics();
    }

    /**
     * Format Unix timestamp to human-readable date
     */
    public String formatUnixTimestamp(String timestamp) {
        try {
            long unixTime = Long.parseLong(timestamp);
            Date date = new Date(unixTime * 1000L); // Convert to milliseconds
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(date);
        } catch (Exception e) {
            logger.error("Error formatting timestamp: {}", timestamp, e);
            return timestamp; // Return original if parsing fails
        }
    }
}