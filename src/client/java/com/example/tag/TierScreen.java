package com.example.tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TierScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("TierScreen");

    // Constants
    private static final int WINDOW_WIDTH = 320;
    private static final int WINDOW_HEIGHT = 240;

    // Services
    private final IsrealTiersApiService apiService;

    // Components
    private TextFieldWidget searchField;
    private ButtonWidget searchButton;
    private List<TabButton> gameTabs = new ArrayList<>();
    private String selectedTab = "crystal";

    private ButtonWidget viewHistoryButton;
    public static PlayerHistoryTracker historyTracker = new PlayerHistoryTracker(LoggerFactory.getLogger("PlayerHistoryTracker"));

    private ButtonWidget customizeThemeButton;

    // State
    private JsonObject playerData;
    private String currentUsername;
    private String currentUuid;
    private boolean isLoading = false;

    private LeaderboardWidget leaderboardWidget;

    // Tab positions
    private static final String[] GAME_MODES = {"crystal", "sword", "uhc", "pot", "smp"};
    private static final String[] TAB_LABELS = {"Crystal", "Sword", "UHC", "Pot", "SMP"};

    public TierScreen() {
        super(Text.literal("Israel Tier Tagger"));
        this.apiService = new IsrealTiersApiService(LOGGER);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        int leaderboardX = windowX + WINDOW_WIDTH + 10;
        int leaderboardY = windowY;
        this.leaderboardWidget = new LeaderboardWidget(leaderboardX, leaderboardY, this.selectedTab, this.apiService);

        ModConfig config = ModConfig.getInstance();
        this.leaderboardWidget.setVisible(config.isShowLeaderboard());

        // Customize theme button
        this.customizeThemeButton = ButtonWidget.builder(
                Text.literal("Customize Theme"),
                (button) -> {
                    this.client.setScreen(new ThemeSettingsScreen(this));
                }
        ).dimensions(windowX + 200, windowY + WINDOW_HEIGHT - 30, 100, 20).build();
        this.addDrawableChild(this.customizeThemeButton);

        // Search field
        this.searchField = new TextFieldWidget(
                this.textRenderer,
                windowX + 20,
                windowY + 20,
                200,
                20,
                Text.literal("Enter username")
        );
        this.searchField.setMaxLength(16);
        this.addDrawableChild(this.searchField);

        // Search button
        this.searchButton = ButtonWidget.builder(
                Text.literal("Search"),
                (button) -> this.searchPlayer(this.searchField.getText())
        ).dimensions(windowX + 230, windowY + 20, 70, 20).build();
        this.addDrawableChild(this.searchButton);

        // Settings button
        ButtonWidget settingsButton = ButtonWidget.builder(
                Text.literal("Settings"),
                (button) -> {
                    this.client.setScreen(new SettingsScreen(this));
                }
        ).dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(settingsButton);

        // Game mode tabs
        for (int i = 0; i < GAME_MODES.length; i++) {
            final int index = i;
            TabButton tabButton = new TabButton(
                    windowX + 20 + (i * 60),
                    windowY + 50,
                    55,
                    20,
                    Text.literal(TAB_LABELS[i]),
                    (button) -> {
                        this.selectedTab = GAME_MODES[index];
                        updateTabSelection();
                        if (this.leaderboardWidget != null) {
                            this.leaderboardWidget.updateGameMode(this.selectedTab);
                        }
                    },
                    GAME_MODES[i]
            );
            this.gameTabs.add(tabButton);
            this.addDrawableChild(tabButton);
        }

        // Add history button if history tracking is enabled
        if (historyTracker != null && config.isTrackPlayerHistory()) {
            this.viewHistoryButton = ButtonWidget.builder(
                    Text.literal("View History"),
                    button -> {
                        if (this.currentUsername != null && this.playerData != null) {
                            String uuid = this.playerData.get("id").getAsString();
                            this.client.setScreen(new PlayerHistoryScreen(this, uuid, this.currentUsername, historyTracker));
                        }
                    }
            ).dimensions(windowX + 110, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
            this.addDrawableChild(this.viewHistoryButton);

            // Disable by default, only enable when a player is loaded
            this.viewHistoryButton.active = false;
        }

        updateTabSelection();
    }

    private void updateTabSelection() {
        for (TabButton button : gameTabs) {
            button.setSelected(button.getGameMode().equals(selectedTab));
        }
    }

    private void searchPlayer(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }

        this.isLoading = true;
        this.currentUsername = username;

        // Run asynchronously to avoid freezing the game
        CompletableFuture.runAsync(() -> {
            try {
                String uuid = apiService.fetchUUID(username);
                if (uuid == null) {
                    this.isLoading = false;
                    MinecraftClient.getInstance().execute(() -> {
                        this.playerData = null;
                        this.currentUuid = null;
                    });
                    return;
                }

                this.currentUuid = uuid;

                apiService.fetchPlayerData(uuid, (data, success) -> {
                    // Execute on main thread to avoid threading issues
                    MinecraftClient.getInstance().execute(() -> {
                        this.playerData = data;
                        this.isLoading = false;

                        // Record player data for history if tracking is enabled
                        if (data != null && ModConfig.getInstance().isTrackPlayerHistory() && historyTracker != null) {
                            historyTracker.recordPlayerData(uuid, username, data);
                        }

                        // Enable history button if we have data
                        if (this.viewHistoryButton != null) {
                            this.viewHistoryButton.active = true;
                        }
                    });
                });
            } catch (Exception e) {
                LOGGER.error("Error searching for player", e);
                // Execute on main thread to avoid threading issues
                MinecraftClient.getInstance().execute(() -> {
                    this.isLoading = false;
                    this.playerData = null;
                    this.currentUuid = null;
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background with minimum processing
        context.fill(0, 0, this.width, this.height, 0x88000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Apply theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);

        // Draw window background with precise filling
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, backgroundColor);

        // Draw border manually for sharpness
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + 1, borderColor);
        context.fill(windowX, windowY + WINDOW_HEIGHT - 1, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, borderColor);
        context.fill(windowX, windowY, windowX + 1, windowY + WINDOW_HEIGHT, borderColor);
        context.fill(windowX + WINDOW_WIDTH - 1, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, borderColor);

        // Draw title with precise rendering
        String title = this.title.getString();
        int titleWidth = this.textRenderer.getWidth(title);
        int titleX = centerX - titleWidth / 2;
        int titleY = windowY + 6;

        // Render title without shadow for sharpness
        context.drawText(
                this.textRenderer,
                title,
                titleX,
                titleY,
                titleColor,
                false
        );

        // Draw loading text precisely
        if (this.isLoading) {
            String loadingText = "Loading...";
            int loadingWidth = this.textRenderer.getWidth(loadingText);
            int loadingX = centerX - loadingWidth / 2;

            context.drawText(
                    this.textRenderer,
                    loadingText,
                    loadingX,
                    centerY + 40,
                    0xFFFFFF,
                    false
            );
        }

        // Render player data if available
        if (this.playerData != null && !this.isLoading && this.currentUsername != null) {
            renderPlayerData(context, windowX, windowY);
        } else if (!this.isLoading && this.currentUsername != null) {
            // Player not found text
            String notFoundText = "Player not found: " + this.currentUsername;
            int notFoundWidth = this.textRenderer.getWidth(notFoundText);
            int notFoundX = centerX - notFoundWidth / 2;

            context.drawText(
                    this.textRenderer,
                    notFoundText,
                    notFoundX,
                    centerY + 40,
                    0xFF5555,
                    false
            );
        }

        // Render all child widgets
        super.render(context, mouseX, mouseY, delta);

        // Render leaderboard widget
        if (this.leaderboardWidget != null && this.leaderboardWidget.isVisible()) {
            this.leaderboardWidget.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderPlayerData(DrawContext context, int windowX, int windowY) {
        try {
            JsonObject userData = this.playerData.get("userData").getAsJsonObject();
            JsonArray stats = userData.getAsJsonArray("stats");

            if (stats != null && !stats.isEmpty()) {
                JsonObject gameStats = stats.get(0).getAsJsonObject();

                // Get theme colors
                ModConfig config = ModConfig.getInstance();
                int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
                int textSecondaryColor = config.getColor("text_secondary", 0xAAAAAA);
                int tierTextColor = config.getColor("tier_text", 0x4080FF);
                int pointsTextColor = config.getColor("points_text", 0xFFAA00);

                // Calculate precise positions
                int startX = windowX + 20;
                int startY = windowY + 80;

                // Render player name precisely
                context.drawText(
                        this.textRenderer,
                        "Player: " + this.currentUsername,
                        startX,
                        startY,
                        textPrimaryColor,
                        false
                );

                // Get selected game mode stats
                JsonArray modeStats = gameStats.has(this.selectedTab) ?
                        gameStats.getAsJsonArray(this.selectedTab) : null;

                if (modeStats != null && !modeStats.isEmpty()) {
                    JsonObject stat = modeStats.get(0).getAsJsonObject();
                    String tier = stat.has("tier") ? stat.get("tier").getAsString() : "Unknown";
                    String lastUpdate = stat.has("lastupdate") ? stat.get("lastupdate").getAsString() : "0";

                    int points = apiService.getPointsForTier(tier);
                    String formattedTime = apiService.formatUnixTimestamp(lastUpdate);

                    // Render tier with precise positioning
                    context.drawText(
                            this.textRenderer,
                            "Tier: " + tier,
                            startX,
                            startY + 20,
                            tierTextColor,
                            false
                    );

                    // Render points with precise positioning
                    context.drawText(
                            this.textRenderer,
                            "Points: " + points,
                            startX,
                            startY + 40,
                            pointsTextColor,
                            false
                    );

                    // Render last updated with precise positioning
                    context.drawText(
                            this.textRenderer,
                            "Last updated: " + formattedTime,
                            startX,
                            startY + 60,
                            textSecondaryColor,
                            false
                    );

                    // Render rank if leaderboard is available
                    if (this.leaderboardWidget != null) {
                        int rank = this.leaderboardWidget.getPlayerRank(this.currentUsername);
                        if (rank > 0) {
                            context.drawText(
                                    this.textRenderer,
                                    "Rank: #" + rank + " in " + this.selectedTab,
                                    startX,
                                    startY + 80,
                                    pointsTextColor,
                                    false
                            );
                        }
                    }
                } else {
                    // No data for this game mode
                    context.drawText(
                            this.textRenderer,
                            "No data for " + TAB_LABELS[getTabIndex(this.selectedTab)],
                            startX,
                            startY + 20,
                            config.getColor("text_error", 0xFF5555),
                            false
                    );
                }
            } else {
                // No tier data at all
                context.drawText(
                        this.textRenderer,
                        "No tier data available",
                        windowX + 20,
                        windowY + 100,
                        ModConfig.getInstance().getColor("text_error", 0xFF5555),
                        false
                );
            }
        } catch (Exception e) {
            // Error displaying data
            context.drawText(
                    this.textRenderer,
                    "Error displaying player data: " + e.getMessage(),
                    windowX + 20,
                    windowY + 100,
                    ModConfig.getInstance().getColor("text_error", 0xFF5555),
                    false
            );
            LOGGER.error("Error rendering player data", e);
        }
    }

    /**
     * Get color for a tier
     */
    private int getTierColor(String tier) {
        if (tier == null) return 0xFF4080FF; // Default

        if (tier.equals("LT69")) return 0xFFAA00FF; // Special color for LT69
        if (tier.startsWith("HT1")) return 0xFFFF55FF;
        if (tier.startsWith("LT1")) return 0xFFFF5555;
        if (tier.startsWith("HT2")) return 0xFFFF8800;
        if (tier.startsWith("LT2")) return 0xFFFFAA00;
        if (tier.startsWith("HT3")) return 0xFF55FF55;
        if (tier.startsWith("LT3")) return 0xFF55FFFF;
        if (tier.startsWith("HT4")) return 0xFF5555FF;
        if (tier.startsWith("LT4")) return 0xFF9955FF;
        if (tier.startsWith("HT5")) return 0xFF555555;
        if (tier.startsWith("LT5")) return 0xFFAAAAAA;

        return 0xFF4080FF; // Default
    }

    private int getTabIndex(String gameMode) {
        for (int i = 0; i < GAME_MODES.length; i++) {
            if (GAME_MODES[i].equals(gameMode)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void setHistoryTracker(PlayerHistoryTracker tracker) {
        historyTracker = tracker;
    }

    // Custom tab button class with precise rendering
    private static class TabButton extends ButtonWidget {
        private final String gameMode;
        private boolean selected;

        public TabButton(int x, int y, int width, int height, Text text, PressAction onPress, String gameMode) {
            super(x, y, width, height, text, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.gameMode = gameMode;
            this.selected = false;
        }

        public String getGameMode() {
            return gameMode;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            // Get theme colors precisely
            ModConfig config = ModConfig.getInstance();
            int activeTabColor = config.getColor("tab_active", 0xFF4080FF);
            int inactiveTabColor = config.getColor("tab_inactive", 0xFF303030);

            // Precise tab button rendering
            if (this.selected) {
                // Selected tab with full opacity
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), activeTabColor);
            } else {
                // Inactive tab with full opacity
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), inactiveTabColor);
            }

            // Precise text rendering
            String buttonText = this.getMessage().getString();
            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(buttonText);
            int textX = this.getX() + (this.getWidth() - textWidth) / 2;
            int textY = this.getY() + (this.getHeight() - 8) / 2;

            // Render text without shadow for maximum sharpness
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    buttonText,
                    textX,
                    textY,
                    this.selected ? 0xFFFFFF : 0xE0E0E0,
                    false
            );
        }
    }
}