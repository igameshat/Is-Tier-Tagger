package com.example.tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages UI display and formatting for the Israel Tiers mod
 */
public class TierUIManager {
    private final Logger logger;
    private JDA jda;

    public TierUIManager(Logger logger) {
        this.logger = logger;
    }

    public void setJda(JDA jda) {
        this.jda = jda;
    }

    /**
     * Create a simple feedback message
     */
    public Text createFeedbackMessage(String message) {
        // Use colored text if enabled in config
        ModConfig config = ModConfig.getInstance();
        if (config.isColorfulOutput()) {
            return Text.literal(message);
        } else {
            // Strip color codes if colorful output is disabled
            return Text.literal(message.replaceAll("§[0-9a-fk-or]", ""));
        }
    }

    /**
     * Open the Israel Tiers profile in browser
     */
    public void openInBrowser(String username, FabricClientCommandSource source) {
        // Check if auto-open browser is enabled
        ModConfig config = ModConfig.getInstance();
        if (!config.isAutoOpenBrowser()) {
            source.sendFeedback(Text.literal("API request failed. View profile at: https://israeltiers.com/p/" + username));
            return;
        }

        try {
            String url = "https://israeltiers.com/p/" + username;

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }

            source.sendFeedback(Text.literal("Opened in browser: " + url));
        } catch (Exception e) {
            logger.error("Error opening browser", e);
            source.sendFeedback(Text.literal("Failed to open browser: " + e.getMessage()));
        }
    }

    /**
     * Fetch Discord user information asynchronously
     * @param discordId Discord user ID
     * @return CompletableFuture that will resolve with the Text to display
     */
    public CompletableFuture<Text> fetchDiscordInfo(String discordId) {
        CompletableFuture<Text> future = new CompletableFuture<>();

        if (jda != null && IstiertaggerClient.isDiscordConnected()) {
            try {
                jda.retrieveUserById(discordId).queue(
                        user -> {
                            if (user != null) {
                                Text discordText = formatDiscordText(user.getName(), discordId);
                                future.complete(discordText);
                            } else {
                                future.complete(formatDiscordIdText(discordId));
                            }
                        },
                        error -> {
                            logger.error("Error fetching Discord user", error);
                            future.complete(formatDiscordIdText(discordId));
                        }
                );
            } catch (Exception e) {
                logger.error("Error with Discord lookup", e);
                future.complete(formatDiscordIdText(discordId));
            }
        } else {
            future.complete(formatDiscordIdText(discordId));
        }

        return future;
    }

    /**
     * Format Discord text with username
     */
    private Text formatDiscordText(String username, String discordId) {
        return Text.literal("§7Discord: ")
                .append(Text.literal("§f" + username)
                        .styled(style -> style.withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                                        discordId
                                )
                        ))
                        .styled(style -> style.withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("§7Click to copy Discord ID")
                                )
                        ))
                );
    }

    /**
     * Format Discord ID text as fallback
     */
    private Text formatDiscordIdText(String discordId) {
        return Text.literal("§7Discord ID: ")
                .append(Text.literal("§f" + discordId)
                        .styled(style -> style.withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                                        discordId
                                )
                        ))
                        .styled(style -> style.withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("§7Click to copy Discord ID")
                                )
                        ))
                );
    }

    /**
     * Display player data in chat
     */
    public void displayPlayerData(FabricClientCommandSource source, String username, JsonObject data, String filter) {
        try {
            // Main header
            source.sendFeedback(Text.literal("§6=== Player Data for " + username + " ==="));

            // Parse userData
            JsonObject userData = data.get("userData").getAsJsonObject();
            String discordId = userData.get("discordId").getAsString();

            // Basic info section
            String uuid = data.get("id").getAsString();

            // Create all texts in a section
            Text uuidText = Text.literal("§7UUID: ")
                    .append(Text.literal("§f" + uuid)
                            .styled(style -> style.withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.COPY_TO_CLIPBOARD,
                                            uuid
                                    )
                            ))
                            .styled(style -> style.withHoverEvent(
                                    new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Text.literal("§7Click to copy UUID")
                                    )
                            ))
                    );

            Text usernameText = Text.literal("§7Username: ")
                    .append(Text.literal("§f" + username)
                            .styled(style -> style.withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.OPEN_URL,
                                            "https://namemc.com/profile/" + username
                                    )
                            ))
                            .styled(style -> style.withHoverEvent(
                                    new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Text.literal("§7Click to view on NameMC")
                                    )
                            ))
                    );

            // Display basic info immediately
            source.sendFeedback(uuidText);
            source.sendFeedback(usernameText);

            // Fetch and display Discord info in the same section
            fetchDiscordInfo(discordId).thenAccept(discordText -> {
                source.sendFeedback(discordText);

                // Continue with displaying game stats
                displayGameStats(source, userData, filter, username);
            });
        } catch (Exception e) {
            logger.error("Error formatting player data", e);
            source.sendFeedback(Text.literal("§cError formatting data: " + e.getMessage()));
        }
    }

    /**
     * Display game statistics
     */
    private void displayGameStats(FabricClientCommandSource source, JsonObject userData, String filter, String username) {
        try {
            // Parse stats
            JsonArray stats = userData.getAsJsonArray("stats");
            if (!stats.isEmpty()) {
                JsonObject gameStats = stats.get(0).getAsJsonObject();

                if (filter == null) {
                    source.sendFeedback(Text.literal("\n§6=== Game Stats ==="));

                    // Calculate total points
                    int totalPoints = 0;
                    IsrealTiersApiService apiService = new IsrealTiersApiService(logger);

                    for (String gameMode : new String[]{"crystal", "pot", "sword", "uhc", "smp"}) {
                        JsonArray modeStats = gameStats.getAsJsonArray(gameMode);
                        if (modeStats != null && !modeStats.isEmpty()) {
                            JsonObject stat = modeStats.get(0).getAsJsonObject();
                            String tier = stat.get("tier").getAsString();
                            totalPoints += apiService.getPointsForTier(tier);
                        }
                    }


                    source.sendFeedback(Text.literal("§6Total Points: §d" + totalPoints));

                    // Display all game modes
                    displayGameMode(source, gameStats, "crystal", "Crystal", username);
                    displayGameMode(source, gameStats, "pot", "Pot", username);
                    displayGameMode(source, gameStats, "sword", "Sword", username);
                    displayGameMode(source, gameStats, "uhc", "UHC", username);
                    displayGameMode(source, gameStats, "smp", "SMP", username);
                } else {
                    // Display only the filtered game mode
                    displayGameMode(source, gameStats, filter,
                            filter.substring(0, 1).toUpperCase() + filter.substring(1), username);
                }
            }
        } catch (Exception e) {
            logger.error("Error displaying game stats", e);
            source.sendFeedback(Text.literal("§cError displaying game stats: " + e.getMessage()));
        }
    }

    /**
     * Display game mode stats
     */
    private void displayGameMode(FabricClientCommandSource source, JsonObject gameStats,
                                 String gameMode, String displayName, String username) {
        try {
            JsonArray modeStats = gameStats.getAsJsonArray(gameMode);
            if (modeStats != null && !modeStats.isEmpty()) {
                JsonObject stat = modeStats.get(0).getAsJsonObject();
                String tier = stat.get("tier").getAsString();
                String lastUpdate = stat.get("lastupdate").getAsString();

                // Only display if there's actual data
                if (!tier.isEmpty() || !lastUpdate.isEmpty()) {
                    IsrealTiersApiService apiService = new IsrealTiersApiService(logger);
                    String formattedTime = apiService.formatUnixTimestamp(lastUpdate);
                    int points = apiService.getPointsForTier(tier);

                    // Create clickable game mode stats
                    source.sendFeedback(
                            Text.literal("§e" + displayName + ": ")
                                    .append(Text.literal("§b" + tier)
                                            .styled(style -> style.withClickEvent(
                                                    new ClickEvent(
                                                            ClickEvent.Action.OPEN_URL,
                                                            "https://israeltiers.com/p/" + username
                                                    )
                                            ))
                                            .styled(style -> style.withHoverEvent(
                                                    new HoverEvent(
                                                            HoverEvent.Action.SHOW_TEXT,
                                                            Text.literal("§7Click to view profile")
                                                    )
                                            ))
                                    )
                                    .append(Text.literal(" §d(" + points + " points) "))
                                    .append(Text.literal("§7(Last updated: §f" + formattedTime + "§7)"))
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error displaying game mode: {}", gameMode, e);
        }
    }

    /**
     * Display tier list in chat
     */
    public void displayTierList(FabricClientCommandSource source, String filter, JsonArray tiers,
                                IsrealTiersApiService apiService) {
        source.sendFeedback(Text.literal("\n§6=== " + filter.toUpperCase() + " Tier List ==="));

        // Sort players by points
        List<Map.Entry<String, Integer>> sortedPlayers = new ArrayList<>();

        for (int i = 0; i < tiers.size(); i++) {
            JsonObject player = tiers.get(i).getAsJsonObject();
            String uuid = player.get("minecraftUUID").getAsString();
            JsonArray filterStats = player.getAsJsonArray(filter);

            if (filterStats != null && !filterStats.isEmpty()) {
                JsonObject stat = filterStats.get(0).getAsJsonObject();
                String tier = stat.get("tier").getAsString();
                int points = apiService.getPointsForTier(tier);
                sortedPlayers.add(new AbstractMap.SimpleEntry<>(uuid, points));
            }
        }

        // Sort by points (highest first)
        sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Display sorted list
        for (int i = 0; i < Math.min(sortedPlayers.size(), 50); i++) { // Limit to top 50 for performance
            Map.Entry<String, Integer> entry = sortedPlayers.get(i);
            String uuid = entry.getKey();
            int points = entry.getValue();

            // Find player data again
            for (int j = 0; j < tiers.size(); j++) {
                JsonObject player = tiers.get(j).getAsJsonObject();
                if (player.get("minecraftUUID").getAsString().equals(uuid)) {
                    JsonArray filterStats = player.getAsJsonArray(filter);
                    JsonObject stat = filterStats.get(0).getAsJsonObject();
                    String tier = stat.get("tier").getAsString();
                    String lastUpdate = stat.get("lastupdate").getAsString();

                    try {
                        String username = apiService.fetchUsernameFromUUID(uuid);
                        String formattedTime = apiService.formatUnixTimestamp(lastUpdate);
                        source.sendFeedback(
                                Text.literal(String.format("#%d §e%s: §b%s §d(%d points) §7(Last updated: §f%s§7)",
                                                i + 1, username, tier, points, formattedTime))
                                        .styled(style -> style.withClickEvent(
                                                new ClickEvent(
                                                        ClickEvent.Action.SUGGEST_COMMAND,
                                                        "/istagger " + username
                                                )
                                        ))
                                        .styled(style -> style.withHoverEvent(
                                                new HoverEvent(
                                                        HoverEvent.Action.SHOW_TEXT,
                                                        Text.literal("§7Click to view player details")
                                                )
                                        ))
                        );
                    } catch (Exception e) {
                        logger.error("Error fetching username for UUID: {}", uuid, e);
                    }
                    break;
                }
            }
        }
    }
}