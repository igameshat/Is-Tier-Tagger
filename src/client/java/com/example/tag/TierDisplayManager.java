package com.example.tag;

import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages display of tier information including emojis and formatting
 */
public class TierDisplayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TierDisplayManager");
    private static PlayerHistoryTracker historyTracker;
    private final IsrealTiersApiService apiService;

    // Cache mapping player UUIDs to their emoji representation
    private static final Map<String, Text> playerEmojiCache = new HashMap<>();

    // Define emoji symbols for each game mode
    private static final Map<String, String> GAME_MODE_EMOJIS = new HashMap<>();
    static {
        GAME_MODE_EMOJIS.put("crystal", "{}"); // Crystal emoji
        GAME_MODE_EMOJIS.put("sword", "\uD83D\uDDE1"); // Sword emoji
        GAME_MODE_EMOJIS.put("uhc", "♥"); // Heart emoji
        GAME_MODE_EMOJIS.put("pot", "⚗"); // Potion emoji
        GAME_MODE_EMOJIS.put("smp", "⛨");
    }

    public TierDisplayManager(Logger logger, PlayerHistoryTracker historyTracker, IsrealTiersApiService apiService) {
        LOGGER.info("Initializing TierDisplayManager");
        TierDisplayManager.historyTracker = historyTracker;
        this.apiService = apiService;
    }

    /**
     * Get the appropriate emoji text for a player
     */
    public static Text getPlayerTierEmoji(String uuid, String username) {
        // Initialize historyTracker if it hasn't been set yet
        if (historyTracker == null) {
            LOGGER.info("HistoryTracker was null, initializing from TierScreen");
            historyTracker = TierScreen.historyTracker;

            // If still null, create a new one
            if (historyTracker == null) {
                LOGGER.info("Creating new HistoryTracker");
                historyTracker = new PlayerHistoryTracker(LOGGER);
            }
        }

        // Check cache first
        if (playerEmojiCache.containsKey(uuid)) {
            return playerEmojiCache.get(uuid);
        }

        PlayerHistoryTracker.PlayerHistory history = historyTracker.getPlayerHistory(uuid);
        if (history == null) {
            // No history available yet
            return Text.literal("");
        }

        // Find player's best tier
        String bestGameMode = null;
        String bestTier = null;
        int highestPoints = -1;

        for (String gameMode : GAME_MODE_EMOJIS.keySet()) {
            var snapshots = history.getTierSnapshots(gameMode);
            if (!snapshots.isEmpty()) {
                var latestSnapshot = snapshots.get(snapshots.size() - 1);
                int points = latestSnapshot.getPoints();
                if (points > highestPoints) {
                    highestPoints = points;
                    bestTier = latestSnapshot.getTier();
                    bestGameMode = gameMode;
                }
            }
        }

        if (bestTier == null || bestGameMode == null) {
            // No tier data found
            return Text.literal("");
        }

        // Get emoji for the game mode
        String emoji = GAME_MODE_EMOJIS.getOrDefault(bestGameMode, "");

        // Create the formatted text
        Text tierEmoji = formatTierEmoji(emoji, bestTier, bestGameMode, highestPoints, username);

        // Cache the result
        playerEmojiCache.put(uuid, tierEmoji);

        return tierEmoji;
    }

    /**
     * Format tier emoji text with color and hover information
     */
    private static Text formatTierEmoji(String emoji, String tier, String gameMode, int points, String username) {
        // Choose color based on tier
        Formatting tierFormatting = getTierFormatting(tier);

        // Game mode capitalized
        String gameModeDisplay = gameMode.substring(0, 1).toUpperCase() + gameMode.substring(1);

        // Create the main emoji text
        Text emojiText = Text.literal(emoji + tier + emoji)
                .setStyle(Style.EMPTY.withFormatting(tierFormatting));

        // Add hover text with more information
        Text hoverText = Text.literal(username + "\n")
                .append(Text.literal(gameModeDisplay + ": " + tier + " (" + points + " points)"));

        // Apply hover event
        return emojiText.copy().setStyle(
                emojiText.getStyle().withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
                )
        );
    }

    /**
     * Get color formatting based on tier
     */
    private static Formatting getTierFormatting(String tier) {
        if (tier.startsWith("HT1")) return Formatting.LIGHT_PURPLE;
        if (tier.startsWith("LT1")) return Formatting.RED;
        if (tier.startsWith("HT2")) return Formatting.GOLD;
        if (tier.startsWith("LT2")) return Formatting.YELLOW;
        if (tier.startsWith("HT3")) return Formatting.GREEN;
        if (tier.startsWith("LT3")) return Formatting.AQUA;
        if (tier.startsWith("HT4")) return Formatting.BLUE;
        if (tier.startsWith("LT4")) return Formatting.DARK_PURPLE;
        if (tier.startsWith("HT5")) return Formatting.DARK_GRAY;
        if (tier.startsWith("LT5")) return Formatting.GRAY;
        return Formatting.WHITE; // Default
    }

    /**
     * Clear cached emoji data
     */
    public void clearCache() {
        playerEmojiCache.clear();
    }
}