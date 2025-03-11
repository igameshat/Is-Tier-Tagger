package com.example.tag;

import com.example.tag.fix.DirectTextRenderer;
import com.example.tag.fix.SharpTabButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main screen for Israel Tier Tagger mod with zero blurriness
 */
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
    private List<SharpTabButton> gameTabs = new ArrayList<>();
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

        // Calculate exact pixel positions
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
                    assert this.client != null;
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
                    assert this.client != null;
                    this.client.setScreen(new SettingsScreen(this));
                }
        ).dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(settingsButton);

        // Game mode tabs - USING OUR SHARP TAB BUTTON
        for (int i = 0; i < GAME_MODES.length; i++) {
            final int index = i;
            SharpTabButton tabButton = new SharpTabButton(
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
                            assert this.client != null;
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
        for (SharpTabButton button : gameTabs) {
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
        // Render background with clean fill
        context.fill(0, 0, this.width, this.height, 0x88000000);

        // Calculate exact pixel positions
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Apply theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);
        int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
        int textSecondaryColor = config.getColor("text_secondary", 0xAAAAAA);

        // Draw window background
        DirectTextRenderer.drawRect(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, backgroundColor);

        // Draw border with pixel-perfect edges
        DirectTextRenderer.drawBorder(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title with DIRECT crisp text rendering
        DirectTextRenderer.drawCenteredText(
                context,
                this.title.getString(),
                centerX,
                windowY + 6,
                titleColor
        );

        // Draw loading text with DIRECT crisp text rendering
        if (this.isLoading) {
            DirectTextRenderer.drawCenteredText(
                    context,
                    "Loading...",
                    centerX,
                    centerY + 40,
                    textPrimaryColor
            );
        }

        // Render player data if available
        if (this.playerData != null && !this.isLoading && this.currentUsername != null) {
            renderPlayerData(context, windowX, windowY);
        } else if (!this.isLoading && this.currentUsername != null) {
            // Player not found text with DIRECT crisp text rendering
            DirectTextRenderer.drawCenteredText(
                    context,
                    "Player not found: " + this.currentUsername,
                    centerX,
                    centerY + 40,
                    config.getColor("text_error", 0xFF5555)
            );
        }

        // Render all child widgets (force rendering of text fields and buttons)
        renderWidgets(context, mouseX, mouseY, delta);

        // Render leaderboard widget
        if (this.leaderboardWidget != null && this.leaderboardWidget.isVisible()) {
            this.leaderboardWidget.render(context, mouseX, mouseY, delta);
        }
    }

    // Add this private helper method to render widgets with crisp text
    private void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render buttons and other widgets
        for (Element element : this.children()) {
            if (element instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        // Re-render button text for crispness if needed
        for (Element element : this.children()) {
            if (element instanceof ButtonWidget button) {
                // Just to ensure the text stays sharp, we could re-render it here
                int buttonCenterX = button.getX() + button.getWidth() / 2;
                int buttonTextY = button.getY() + (button.getHeight() - 8) / 2;

                if (!(button instanceof SharpTabButton)) {
                    DirectTextRenderer.drawCenteredText(
                            context,
                            button.getMessage().getString(),
                            buttonCenterX,
                            buttonTextY,
                            button.active ? 0xFFFFFF : 0xAAAAAA
                    );
                }
            }
        }
    }

    private void renderPlayerData(DrawContext context, int windowX, int windowY) {
        try {
            JsonObject userData = this.playerData.get("userData").getAsJsonObject();
            JsonArray stats = userData.getAsJsonArray("stats");

            ModConfig config;
            config = null;
            if (stats != null && !stats.isEmpty()) {
                JsonObject gameStats = stats.get(0).getAsJsonObject();

                // Get theme colors
                config = ModConfig.getInstance();
                int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
                int textSecondaryColor = config.getColor("text_secondary", 0xAAAAAA);
                int tierTextColor = config.getColor("tier_text", 0x4080FF);
                int pointsTextColor = config.getColor("points_text", 0xFFAA00);
                int errorTextColor = config.getColor("text_error", 0xFF5555);

                // Calculate precise positions
                int startX = windowX + 20;
                int startY = windowY + 80;

                // Render player name with DIRECT crisp text
                DirectTextRenderer.drawText(
                        context,
                        "Player: " + this.currentUsername,
                        startX,
                        startY,
                        textPrimaryColor
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

                    // Render tier with DIRECT crisp text
                    DirectTextRenderer.drawText(
                            context,
                            "Tier: " + tier,
                            startX,
                            startY + 20,
                            tierTextColor
                    );

                    // Render points with DIRECT crisp text
                    DirectTextRenderer.drawText(
                            context,
                            "Points: " + points,
                            startX,
                            startY + 40,
                            pointsTextColor
                    );

                    // Render last updated with DIRECT crisp text
                    DirectTextRenderer.drawText(
                            context,
                            "Last updated: " + formattedTime,
                            startX,
                            startY + 60,
                            textSecondaryColor
                    );

                    // Render rank if leaderboard is available
                    if (this.leaderboardWidget != null) {
                        int rank = this.leaderboardWidget.getPlayerRank(this.currentUsername);
                        if (rank > 0) {
                            DirectTextRenderer.drawText(
                                    context,
                                    "Rank: #" + rank + " in " + this.selectedTab,
                                    startX,
                                    startY + 80,
                                    pointsTextColor
                            );
                        }
                    }
                } else {
                    // No data for this game mode
                    DirectTextRenderer.drawText(
                            context,
                            "No data for " + TAB_LABELS[getTabIndex(this.selectedTab)],
                            startX,
                            startY + 20,
                            errorTextColor
                    );
                }
            } else {
                // No tier data at all
                DirectTextRenderer.drawText(
                        context,
                        "No tier data available",
                        windowX + 20,
                        windowY + 100,
                        config.getColor("text_error", 0xFF5555)
                );
            }
        } catch (Exception e) {
            // Error displaying data
            DirectTextRenderer.drawText(
                    context,
                    "Error displaying player data: " + e.getMessage(),
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the leaderboard was clicked first
        if (this.leaderboardWidget != null && this.leaderboardWidget.isVisible() &&
                this.leaderboardWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Otherwise handle normal clicks
        return super.mouseClicked(mouseX, mouseY, button);
    }
}