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
    // Create our own logger for this class
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
    // Initialize historyTracker statically
    public static PlayerHistoryTracker historyTracker = new PlayerHistoryTracker(LoggerFactory.getLogger("PlayerHistoryTracker"));

    private ButtonWidget customizeThemeButton;

    // State
    private JsonObject playerData;
    private String currentUsername;
    private boolean isLoading = false;

    private LeaderboardWidget leaderboardWidget;

    // Tab positions
    private static final String[] GAME_MODES = {"crystal", "sword", "uhc", "pot", "smp"};
    private static final String[] TAB_LABELS = {"Crystal", "Sword", "UHC", "Pot", "SMP"};

    public TierScreen() {
        super(Text.literal("Israel Tier Tagger"));
        // Use our own logger instead of the one from IstiertaggerClient
        this.apiService = new IsrealTiersApiService(LOGGER);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        int leaderboardX = windowX + WINDOW_WIDTH + 10; // Position it to the right of the main window
        int leaderboardY = windowY;
        this.leaderboardWidget = new LeaderboardWidget(leaderboardX, leaderboardY, this.selectedTab, this.apiService);

        // Set leaderboard visibility based on config
        ModConfig config = ModConfig.getInstance();
        this.leaderboardWidget.setVisible(config.isShowLeaderboard());

        // Add customize theme button
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
        this.searchField.setMaxLength(16); // Minecraft username max length
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
            final int index = i; // Need final variable for lambda
            TabButton tabButton = new TabButton(
                    windowX + 20 + (i * 60),
                    windowY + 50,
                    55,
                    20,
                    Text.literal(TAB_LABELS[i]),
                    (button) -> {
                        this.selectedTab = GAME_MODES[index];
                        updateTabSelection();
                        // Update leaderboard with new game mode
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
                    });
                    return;
                }

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
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use the proper method for your fabric version - if this one doesn't work,
        // try super.renderBackground(context) or just fill the screen with a color
        try {
            renderBackground(context, mouseX, mouseY, delta);
        } catch (NoSuchMethodError e) {
            // Fallback for different API versions
            context.fill(0, 0, this.width, this.height, 0x88000000);
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Apply theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, backgroundColor);
        context.drawBorder(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, windowY + 6, titleColor);

        // Draw loading indicator
        if (this.isLoading) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Loading..."),
                    centerX,
                    centerY + 40,
                    0xFFFFFF
            );
        }

        // Render player data if available
        if (this.playerData != null && !this.isLoading && this.currentUsername != null) {
            renderPlayerData(context, windowX, windowY);
        } else if (!this.isLoading && this.currentUsername != null && this.playerData == null) {
            // If search completed but no data found
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Player not found: " + this.currentUsername),
                    centerX,
                    centerY + 40,
                    0xFF5555
            );
        }

        // Call parent render which will render all children (buttons, etc.)
        super.render(context, mouseX, mouseY, delta);

        // Render the leaderboard widget after all other elements
        if (this.leaderboardWidget != null && this.leaderboardWidget.isVisible()) {
            this.leaderboardWidget.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the leaderboard handles this click
        if (this.leaderboardWidget != null && this.leaderboardWidget.isVisible() &&
                this.leaderboardWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Otherwise, pass to default handling
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        super.removed();

        // Save leaderboard visibility state to config
        ModConfig config = ModConfig.getInstance();
        config.setShowLeaderboard(this.leaderboardWidget != null && this.leaderboardWidget.isVisible());
        config.save();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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

                // Draw player name and UUID
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal("Player: " + this.currentUsername),
                        windowX + 20,
                        windowY + 80,
                        textPrimaryColor
                );

                // Draw selected game mode stats
                JsonArray modeStats = gameStats.has(this.selectedTab) ?
                        gameStats.getAsJsonArray(this.selectedTab) : null;

                if (modeStats != null && !modeStats.isEmpty()) {
                    JsonObject stat = modeStats.get(0).getAsJsonObject();
                    String tier = stat.has("tier") ? stat.get("tier").getAsString() : "Unknown";
                    String lastUpdate = stat.has("lastupdate") ? stat.get("lastupdate").getAsString() : "0";

                    int points = apiService.getPointsForTier(tier);
                    String formattedTime = apiService.formatUnixTimestamp(lastUpdate);

                    // Draw the tier information
                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal("Tier: " + tier),
                            windowX + 20,
                            windowY + 100,
                            tierTextColor
                    );

                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal("Points: " + points),
                            windowX + 20,
                            windowY + 115,
                            pointsTextColor
                    );

                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal("Last updated: " + formattedTime),
                            windowX + 20,
                            windowY + 130,
                            textSecondaryColor
                    );

                    // Get rank from leaderboard if available
                    if (this.leaderboardWidget != null) {
                        int rank = this.leaderboardWidget.getPlayerRank(this.currentUsername);
                        if (rank > 0) {
                            context.drawTextWithShadow(
                                    this.textRenderer,
                                    Text.literal("Rank: #" + rank + " in " + this.selectedTab),
                                    windowX + 20,
                                    windowY + 145,
                                    pointsTextColor
                            );
                        }
                    }
                } else {
                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal("No data for " + TAB_LABELS[getTabIndex(this.selectedTab)]),
                            windowX + 20,
                            windowY + 100,
                            config.getColor("text_error", 0xFF5555)
                    );
                }
            } else {
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal("No tier data available for this player"),
                        windowX + 20,
                        windowY + 100,
                        ModConfig.getInstance().getColor("text_error", 0xFF5555)
                );
            }
        } catch (Exception e) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal("Error displaying player data: " + e.getMessage()),
                    windowX + 20,
                    windowY + 100,
                    ModConfig.getInstance().getColor("text_error", 0xFF5555)
            );
            LOGGER.error("Error rendering player data", e);
        }
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

    // Custom tab button class
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
            // Apply theme colors from config
            ModConfig config = ModConfig.getInstance();
            int activeTabColor = config.getColor("tab_active", 0xFF4080FF);
            int inactiveTabColor = config.getColor("tab_inactive", 0xFF303030);

            // Custom rendering for tab buttons
            if (this.selected) {
                // Selected tab styling
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), activeTabColor);
            } else {
                // Normal tab styling
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), inactiveTabColor);
            }

            context.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    this.getMessage(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    this.selected ? 0xFFFFFF : 0xE0E0E0
            );
        }
    }
}