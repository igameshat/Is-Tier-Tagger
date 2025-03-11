package com.example.tag;

import com.example.tag.fix.DirectTextRenderer;
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

import java.util.concurrent.CompletableFuture;

/**
 * Screen for comparing two players side-by-side
 * Enhanced with pixel-perfect rendering for maximum clarity
 */
public class PlayerComparisonScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerComparisonScreen");
    private static final int WINDOW_WIDTH = 500;
    private static final int WINDOW_HEIGHT = 300;

    // Services
    private final IsrealTiersApiService apiService;

    // Components
    public TextFieldWidget player1Field;
    public TextFieldWidget player2Field;
    private ButtonWidget compareButton;
    private ButtonWidget backButton;

    // State
    private JsonObject player1Data;
    private JsonObject player2Data;
    private String player1Username;
    private String player2Username;
    private boolean isLoading = false;

    // Game modes for comparison
    private static final String[] GAME_MODES = {"crystal", "sword", "uhc", "pot", "smp"};
    private static final String[] MODE_LABELS = {"Crystal", "Sword", "UHC", "Pot", "SMP"};

    public PlayerComparisonScreen() {
        super(Text.literal("Player Comparison"));
        this.apiService = new IsrealTiersApiService(LOGGER);
    }

    @Override
    protected void init() {
        super.init();

        // Calculate exact pixel positions for sharp rendering
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Player 1 field
        this.player1Field = new TextFieldWidget(
                this.textRenderer,
                windowX + 20,
                windowY + 30,
                180,
                20,
                Text.literal("Player 1")
        );
        this.player1Field.setMaxLength(16);
        this.addDrawableChild(this.player1Field);

        // Player 2 field
        this.player2Field = new TextFieldWidget(
                this.textRenderer,
                windowX + WINDOW_WIDTH - 200,
                windowY + 30,
                180,
                20,
                Text.literal("Player 2")
        );
        this.player2Field.setMaxLength(16);
        this.addDrawableChild(this.player2Field);

        // Compare button
        this.compareButton = ButtonWidget.builder(
                        Text.literal("Compare"),
                        (button) -> this.comparePlayers()
                )
                .dimensions(centerX - 40, windowY + 60, 80, 20)
                .build();
        this.addDrawableChild(this.compareButton);

        // Back button
        this.backButton = ButtonWidget.builder(
                        Text.literal("Back"),
                        (button) -> this.close()
                )
                .dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20)
                .build();
        this.addDrawableChild(this.backButton);
    }

    public void comparePlayers() {
        player1Username = this.player1Field.getText();
        player2Username = this.player2Field.getText();

        if (player1Username.isEmpty() || player2Username.isEmpty()) {
            return;
        }

        isLoading = true;
        player1Data = null;
        player2Data = null;

        // Fetch data for both players concurrently
        CompletableFuture.runAsync(() -> {
            try {
                String uuid1 = apiService.fetchUUID(player1Username);
                if (uuid1 == null) {
                    MinecraftClient.getInstance().execute(() -> {
                        player1Data = null;
                        checkComparisonReady();
                    });
                } else {
                    apiService.fetchPlayerData(uuid1, (data, success) -> {
                        MinecraftClient.getInstance().execute(() -> {
                            player1Data = success ? data : null;
                            checkComparisonReady();
                        });
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching data for player 1", e);
                MinecraftClient.getInstance().execute(() -> {
                    player1Data = null;
                    checkComparisonReady();
                });
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                String uuid2 = apiService.fetchUUID(player2Username);
                if (uuid2 == null) {
                    MinecraftClient.getInstance().execute(() -> {
                        player2Data = null;
                        checkComparisonReady();
                    });
                } else {
                    apiService.fetchPlayerData(uuid2, (data, success) -> {
                        MinecraftClient.getInstance().execute(() -> {
                            player2Data = success ? data : null;
                            checkComparisonReady();
                        });
                    });
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching data for player 2", e);
                MinecraftClient.getInstance().execute(() -> {
                    player2Data = null;
                    checkComparisonReady();
                });
            }
        });
    }

    private void checkComparisonReady() {
        // Check if both requests completed (success or failure)
        if ((player1Data != null || player1Data == null) && (player2Data != null || player2Data == null)) {
            isLoading = false;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background with pixel-perfect fill
        DirectTextRenderer.drawRect(context, 0, 0, this.width, this.height, 0x88000000);

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

        // Draw window background with pixel-perfect edges
        DirectTextRenderer.drawRect(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, backgroundColor);
        DirectTextRenderer.drawBorder(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title with sharp text
        DirectTextRenderer.drawCenteredText(
                context,
                this.title.getString(),
                centerX,
                windowY + 10,
                titleColor
        );

        // Draw player labels with sharp text
        DirectTextRenderer.drawText(
                context,
                "Player 1:",
                windowX + 20,
                windowY + 20,
                config.getColor("text_primary", 0xFFFFFF)
        );

        DirectTextRenderer.drawText(
                context,
                "Player 2:",
                windowX + WINDOW_WIDTH - 200,
                windowY + 20,
                config.getColor("text_primary", 0xFFFFFF)
        );

        // Draw loading indicator with sharp text
        if (isLoading) {
            DirectTextRenderer.drawCenteredText(
                    context,
                    "Loading...",
                    centerX,
                    centerY,
                    config.getColor("text_secondary", 0xAAAAAA)
            );
        } else if (player1Data != null || player2Data != null) {
            // Draw comparison data with sharp text
            renderComparisonData(context, windowX, windowY, config);
        }

        // Render all widgets with crisp text
        renderWidgets(context, mouseX, mouseY, delta);
    }

    private void renderComparisonData(DrawContext context, int windowX, int windowY, ModConfig config) {
        // Get screen center for alignment
        int centerX = this.width / 2;

        // Column headers with centered divider - using exact pixel positions
        int dividerX = windowX + WINDOW_WIDTH / 2;
        int startY = windowY + 90;
        int rowHeight = 20;

        // Draw headers with sharp text
        String player1Name = player1Username;
        String player2Name = player2Username;

        if (player1Data != null) {
            DirectTextRenderer.drawText(
                    context,
                    player1Name,
                    dividerX - 100,
                    startY - rowHeight,
                    config.getColor("text_primary", 0xFFFFFF)
            );
        } else {
            DirectTextRenderer.drawText(
                    context,
                    "Player not found: " + player1Name,
                    dividerX - 150,
                    startY - rowHeight,
                    config.getColor("text_error", 0xFF5555)
            );
        }

        if (player2Data != null) {
            DirectTextRenderer.drawText(
                    context,
                    player2Name,
                    dividerX + 50,
                    startY - rowHeight,
                    config.getColor("text_primary", 0xFFFFFF)
            );
        } else {
            DirectTextRenderer.drawText(
                    context,
                    "Player not found: " + player2Name,
                    dividerX + 10,
                    startY - rowHeight,
                    config.getColor("text_error", 0xFF5555)
            );
        }

        // Draw divider line with pixel-perfect edges
        DirectTextRenderer.drawRect(context, dividerX, windowY + 80, 1, WINDOW_HEIGHT - 110, config.getColor("border", 0xFFFFFFFF));

        // Draw game mode comparisons with sharp text
        int totalPlayer1Points = 0;
        int totalPlayer2Points = 0;

        for (int i = 0; i < GAME_MODES.length; i++) {
            String gameMode = GAME_MODES[i];
            String displayName = MODE_LABELS[i];
            int rowY = startY + (i * rowHeight);

            // Draw game mode label with sharp text
            DirectTextRenderer.drawCenteredText(
                    context,
                    displayName,
                    dividerX,
                    rowY,
                    config.getColor("text_secondary", 0xAAAAAA)
            );

            // Get and render player 1 tier for this game mode
            if (player1Data != null) {
                String tier1 = "N/A";
                int points1 = 0;

                try {
                    JsonObject userData = player1Data.get("userData").getAsJsonObject();
                    JsonArray stats = userData.getAsJsonArray("stats");
                    if (stats != null && !stats.isEmpty()) {
                        JsonObject gameStats = stats.get(0).getAsJsonObject();
                        JsonArray modeStats = gameStats.getAsJsonArray(gameMode);

                        if (modeStats != null && !modeStats.isEmpty()) {
                            JsonObject stat = modeStats.get(0).getAsJsonObject();
                            tier1 = stat.get("tier").getAsString();
                            points1 = apiService.getPointsForTier(tier1);
                            totalPlayer1Points += points1;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error parsing player 1 data for " + gameMode, e);
                }

                // Display tier and points with sharp text
                DirectTextRenderer.drawText(
                        context,
                        tier1,
                        dividerX - 100,
                        rowY,
                        config.getColor("tier_text", 0x4080FF)
                );

                DirectTextRenderer.drawText(
                        context,
                        "(" + points1 + " pts)",
                        dividerX - 70,
                        rowY,
                        config.getColor("points_text", 0xFFAA00)
                );
            }

            // Get and render player 2 tier for this game mode
            if (player2Data != null) {
                String tier2 = "N/A";
                int points2 = 0;

                try {
                    JsonObject userData = player2Data.get("userData").getAsJsonObject();
                    JsonArray stats = userData.getAsJsonArray("stats");
                    if (stats != null && !stats.isEmpty()) {
                        JsonObject gameStats = stats.get(0).getAsJsonObject();
                        JsonArray modeStats = gameStats.getAsJsonArray(gameMode);

                        if (modeStats != null && !modeStats.isEmpty()) {
                            JsonObject stat = modeStats.get(0).getAsJsonObject();
                            tier2 = stat.get("tier").getAsString();
                            points2 = apiService.getPointsForTier(tier2);
                            totalPlayer2Points += points2;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error parsing player 2 data for " + gameMode, e);
                }

                // Display tier and points with sharp text
                DirectTextRenderer.drawText(
                        context,
                        tier2,
                        dividerX + 50,
                        rowY,
                        config.getColor("tier_text", 0x4080FF)
                );

                DirectTextRenderer.drawText(
                        context,
                        "(" + points2 + " pts)",
                        dividerX + 80,
                        rowY,
                        config.getColor("points_text", 0xFFAA00)
                );
            }
        }

        // Draw total points with sharp text
        int summaryY = startY + (GAME_MODES.length * rowHeight) + 10;

        if (player1Data != null) {
            DirectTextRenderer.drawText(
                    context,
                    "Total Points: " + totalPlayer1Points,
                    dividerX - 120,
                    summaryY,
                    0xFFFFFF
            );
        }

        if (player2Data != null) {
            DirectTextRenderer.drawText(
                    context,
                    "Total Points: " + totalPlayer2Points,
                    dividerX + 30,
                    summaryY,
                    0xFFFFFF
            );
        }

        // Draw point difference if both players have data with sharp text
        if (player1Data != null && player2Data != null) {
            int pointDiff = totalPlayer1Points - totalPlayer2Points;
            String compareText;
            int compareColor;

            if (pointDiff > 0) {
                compareText = player1Name + " has " + Math.abs(pointDiff) + " more points";
                compareColor = 0x55FF55; // Green
            } else if (pointDiff < 0) {
                compareText = player2Name + " has " + Math.abs(pointDiff) + " more points";
                compareColor = 0xFF5555; // Red
            } else {
                compareText = "Players have equal points";
                compareColor = 0xFFFFFF; // White
            }

            DirectTextRenderer.drawCenteredText(
                    context,
                    compareText,
                    centerX,
                    summaryY + 20,
                    compareColor
            );
        }
    }

    private void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        // First render all drawable elements
        for (Element element : this.children()) {
            if (element instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        // Re-render button text for sharpness
        for (Element element : this.children()) {
            if (element instanceof ButtonWidget button) {
                int buttonCenterX = button.getX() + button.getWidth() / 2;
                int buttonTextY = button.getY() + (button.getHeight() - 8) / 2;
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

    @Override
    public void close() {
        this.client.setScreen(new TierScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}