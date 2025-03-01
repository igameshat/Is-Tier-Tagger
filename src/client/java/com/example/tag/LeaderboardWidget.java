package com.example.tag;

import com.example.tag.util.TextRenderHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A widget to display the leaderboard for a specific game mode
 */
public class LeaderboardWidget {
    private static final Logger LOGGER = LoggerFactory.getLogger("LeaderboardWidget");

    // Constants
    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;
    private static final int ENTRY_HEIGHT = 20;
    private static final int MAX_ENTRIES = 8; // Number of entries to show

    // Position
    private int x;
    private int y;

    // Data
    private final IsrealTiersApiService apiService;
    private String gameMode;
    private List<LeaderboardEntry> entries = new ArrayList<>();
    private boolean isLoading = false;
    private boolean isVisible = true;

    // UI elements
    private ButtonWidget refreshButton;
    private ButtonWidget closeButton;

    public LeaderboardWidget(int x, int y, String gameMode, IsrealTiersApiService apiService) {
        this.x = x;
        this.y = y;
        this.gameMode = gameMode;
        this.apiService = apiService;

        // Initialize buttons
        this.refreshButton = ButtonWidget.builder(
                Text.literal("↻"),
                button -> loadData()
        ).dimensions(x + WIDTH - 40, y + 5, 15, 15).build();

        this.closeButton = ButtonWidget.builder(
                Text.literal("×"),
                button -> this.isVisible = false
        ).dimensions(x + WIDTH - 20, y + 5, 15, 15).build();

        // Load data immediately
        loadData();
    }

    private void loadData() {
        this.isLoading = true;
        this.entries.clear();

        CompletableFuture.runAsync(() -> {
            apiService.fetchTierList(gameMode, (tiers, success) -> {
                if (success && tiers != null) {
                    try {
                        // Process data on a background thread
                        List<LeaderboardEntry> newEntries = new ArrayList<>();

                        for (int i = 0; i < tiers.size(); i++) {
                            JsonObject player = tiers.get(i).getAsJsonObject();
                            String uuid = player.get("minecraftUUID").getAsString();
                            JsonArray filterStats = player.getAsJsonArray(gameMode);

                            if (filterStats != null && !filterStats.isEmpty()) {
                                JsonObject stat = filterStats.get(0).getAsJsonObject();
                                String tier = stat.get("tier").getAsString();
                                int points = apiService.getPointsForTier(tier);

                                try {
                                    String username = apiService.fetchUsernameFromUUID(uuid);
                                    newEntries.add(new LeaderboardEntry(username, tier, points));
                                } catch (Exception e) {
                                    LOGGER.error("Error fetching username for UUID: {}", uuid, e);
                                }
                            }
                        }

                        // Sort by points (highest first)
                        newEntries.sort(Comparator.comparingInt(LeaderboardEntry::getPoints).reversed());

                        // Update the entries on the main thread
                        MinecraftClient.getInstance().execute(() -> {
                            entries = newEntries;
                            isLoading = false;
                        });
                    } catch (Exception e) {
                        LOGGER.error("Error processing tier list data", e);
                        MinecraftClient.getInstance().execute(() -> isLoading = false);
                    }
                } else {
                    MinecraftClient.getInstance().execute(() -> isLoading = false);
                }
            });
        });
    }

    /**
     * Get the rank of a specific player in the current leaderboard
     * @param username Player username to look for
     * @return Rank (1-based) or -1 if not found
     */
    public int getPlayerRank(String username) {
        if (isLoading || entries.isEmpty() || username == null) {
            return -1;
        }

        // Case-insensitive comparison
        String lowerUsername = username.toLowerCase();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getUsername().toLowerCase().equals(lowerUsername)) {
                return i + 1; // 1-based rank
            }
        }

        return -1; // Not found
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible) {
            return;
        }

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int textColor = config.getColor("text_primary", 0xFFFFFF);
        int secondaryTextColor = config.getColor("text_secondary", 0xAAAAAA);
        int tierTextColor = config.getColor("tier_text", 0x4080FF);
        int pointsTextColor = config.getColor("points_text", 0xFFAA00);

        // Draw background
        context.fill(x, y, x + WIDTH, y + HEIGHT, backgroundColor);

        // Draw border manually for compatibility
        drawBorder(context, x, y, WIDTH, HEIGHT, borderColor);

        // Draw title
        String title = gameMode.substring(0, 1).toUpperCase() + gameMode.substring(1) + " Leaderboard";
        TextRenderHelper.drawCenteredText(
                context,
                MinecraftClient.getInstance().textRenderer,
                title,
                x + WIDTH / 2,
                y + 10,
                textColor
        );

        // Render buttons
        refreshButton.setX(x + WIDTH - 40);
        refreshButton.setY(y + 5);
        refreshButton.render(context, mouseX, mouseY, delta);

        closeButton.setX(x + WIDTH - 20);
        closeButton.setY(y + 5);
        closeButton.render(context, mouseX, mouseY, delta);

        // Draw loading indicator or entries
        if (isLoading) {
            TextRenderHelper.drawCenteredText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "Loading...",
                    x + WIDTH / 2,
                    y + 80,
                    secondaryTextColor
            );
        } else if (entries.isEmpty()) {
            TextRenderHelper.drawCenteredText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "No data available",
                    x + WIDTH / 2,
                    y + 80,
                    secondaryTextColor
            );
        } else {
            // Draw column headers
            TextRenderHelper.drawText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "#",
                    x + 10,
                    y + 30,
                    secondaryTextColor,
                    true
            );

            TextRenderHelper.drawText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "Player",
                    x + 30,
                    y + 30,
                    secondaryTextColor,
                    true
            );

            TextRenderHelper.drawText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "Tier",
                    x + 120,
                    y + 30,
                    secondaryTextColor,
                    true
            );

            TextRenderHelper.drawText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    "Points",
                    x + 160,
                    y + 30,
                    secondaryTextColor,
                    true
            );

            // Draw entries
            int startY = y + 45;
            for (int i = 0; i < Math.min(entries.size(), MAX_ENTRIES); i++) {
                LeaderboardEntry entry = entries.get(i);
                int entryY = startY + (i * ENTRY_HEIGHT);

                // Draw rank
                TextRenderHelper.drawText(
                        context,
                        MinecraftClient.getInstance().textRenderer,
                        "#" + (i + 1),
                        x + 10,
                        entryY,
                        textColor,
                        true
                );

                // Draw player name (truncated if needed)
                String name = entry.getUsername();
                if (name.length() > 12) {
                    name = name.substring(0, 10) + "..";
                }

                TextRenderHelper.drawText(
                        context,
                        MinecraftClient.getInstance().textRenderer,
                        name,
                        x + 30,
                        entryY,
                        textColor,
                        true
                );

                // Draw tier
                TextRenderHelper.drawText(
                        context,
                        MinecraftClient.getInstance().textRenderer,
                        entry.getTier(),
                        x + 120,
                        entryY,
                        tierTextColor,
                        true
                );

                // Draw points
                TextRenderHelper.drawText(
                        context,
                        MinecraftClient.getInstance().textRenderer,
                        String.valueOf(entry.getPoints()),
                        x + 160,
                        entryY,
                        pointsTextColor,
                        true
                );
            }
        }
    }

    /**
     * Draw a border around a rectangle
     */
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        try {
            // Try the newer method first
            context.drawBorder(x, y, width, height, color);
        } catch (NoSuchMethodError e) {
            // Fall back to manual border drawing
            // Top
            context.fill(x, y, x + width, y + 1, color);
            // Bottom
            context.fill(x, y + height - 1, x + width, y + height, color);
            // Left
            context.fill(x, y, x + 1, y + height, color);
            // Right
            context.fill(x + width - 1, y, x + width, y + height, color);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible) {
            return false;
        }

        // Check if the click was inside our bounds
        boolean insideWidget = mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;

        // Handle button clicks
        if (refreshButton.isMouseOver(mouseX, mouseY)) {
            refreshButton.onPress();
            return true;
        }

        if (closeButton.isMouseOver(mouseX, mouseY)) {
            closeButton.onPress();
            return true;
        }

        return insideWidget;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void updateGameMode(String gameMode) {
        if (!this.gameMode.equals(gameMode)) {
            // Only reload if the game mode changed
            this.gameMode = gameMode;
            loadData();
        }
    }

    /**
     * Simple data class to hold leaderboard entry information
     */
    private static class LeaderboardEntry {
        private final String username;
        private final String tier;
        private final int points;

        public LeaderboardEntry(String username, String tier, int points) {
            this.username = username;
            this.tier = tier;
            this.points = points;
        }

        public String getUsername() {
            return username;
        }

        public String getTier() {
            return tier;
        }

        public int getPoints() {
            return points;
        }
    }
}
