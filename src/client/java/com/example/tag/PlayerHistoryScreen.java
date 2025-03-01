package com.example.tag;

import com.example.tag.PlayerHistoryTracker.PlayerHistory;
import com.example.tag.PlayerHistoryTracker.TierSnapshot;
import com.example.tag.util.TextRenderHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen to display historical tier data for a player
 */
public class PlayerHistoryScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerHistoryScreen");
    private static final int WINDOW_WIDTH = 350;
    private static final int WINDOW_HEIGHT = 300;

    private final Screen parent;
    private final String playerUuid;
    private final String playerName;
    private final PlayerHistoryTracker historyTracker;

    // UI components
    private ButtonWidget backButton;
    private List<TabButton> gameTabs = new ArrayList<>();
    private String selectedTab = "crystal";

    // Tab labels
    private static final String[] GAME_MODES = {"crystal", "sword", "uhc", "pot", "smp"};
    private static final String[] TAB_LABELS = {"Crystal", "Sword", "UHC", "Pot", "SMP"};

    public PlayerHistoryScreen(Screen parent, String playerUuid, String playerName, PlayerHistoryTracker historyTracker) {
        super(Text.literal(playerName + "'s Tier History"));
        this.parent = parent;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.historyTracker = historyTracker;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Back button
        this.backButton = ButtonWidget.builder(
                Text.literal("Back"),
                button -> this.client.setScreen(this.parent)
        ).dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.backButton);

        // Game mode tabs
        for (int i = 0; i < GAME_MODES.length; i++) {
            final int index = i;
            TabButton tabButton = new TabButton(
                    windowX + 20 + (i * 60),
                    windowY + 30,
                    55,
                    20,
                    Text.literal(TAB_LABELS[i]),
                    (button) -> {
                        this.selectedTab = GAME_MODES[index];
                        updateTabSelection();
                    },
                    GAME_MODES[i]
            );
            this.gameTabs.add(tabButton);
            this.addDrawableChild(tabButton);
        }

        updateTabSelection();
    }

    private void updateTabSelection() {
        for (TabButton button : gameTabs) {
            button.setSelected(button.getGameMode().equals(selectedTab));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background
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

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, backgroundColor);
        drawBorder(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title
        TextRenderHelper.drawCenteredText(context, this.textRenderer, this.title.getString(), centerX, windowY + 10, titleColor);

        // Render history data
        renderHistoryData(context, windowX, windowY);

        // Call parent render which will render all children (buttons, etc.)
        super.render(context, mouseX, mouseY, delta);
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

    private void renderHistoryData(DrawContext context, int windowX, int windowY) {
        PlayerHistory history = historyTracker.getPlayerHistory(playerUuid);

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int textColor = config.getColor("text_primary", 0xFFFFFF);
        int secondaryColor = config.getColor("text_secondary", 0xAAAAAA);
        int tierColor = config.getColor("tier_text", 0x4080FF);
        int pointsColor = config.getColor("points_text", 0xFFAA00);

        if (history == null) {
            TextRenderHelper.drawCenteredText(
                    context,
                    this.textRenderer,
                    "No history data available for this player",
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    secondaryColor
            );
            return;
        }

        List<TierSnapshot> snapshots = history.getTierSnapshots(selectedTab);

        if (snapshots.isEmpty()) {
            TextRenderHelper.drawCenteredText(
                    context,
                    this.textRenderer,
                    "No history data for " + selectedTab,
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    secondaryColor
            );
            return;
        }

        // Draw current tier
        TierSnapshot latest = snapshots.get(snapshots.size() - 1);
        TextRenderHelper.drawText(
                context,
                this.textRenderer,
                "Current Tier: " + latest.getTier() + " (" + latest.getPoints() + " points)",
                windowX + 20,
                windowY + 60,
                textColor,
                true
        );

        TextRenderHelper.drawText(
                context,
                this.textRenderer,
                "Last Updated: " + latest.getFormattedDate(),
                windowX + 20,
                windowY + 75,
                secondaryColor,
                true
        );

        // Draw table headers
        int tableY = windowY + 100;
        TextRenderHelper.drawText(
                context,
                this.textRenderer,
                "Date",
                windowX + 30,
                tableY,
                secondaryColor,
                true
        );

        TextRenderHelper.drawText(
                context,
                this.textRenderer,
                "Tier",
                windowX + 150,
                tableY,
                secondaryColor,
                true
        );

        TextRenderHelper.drawText(
                context,
                this.textRenderer,
                "Points",
                windowX + 250,
                tableY,
                secondaryColor,
                true
        );

        // Draw history table
        int entryHeight = 15;
        int startY = tableY + 20;
        int maxEntries = 10; // Show last 10 entries

        // Start from the most recent snapshots
        int startIndex = Math.max(0, snapshots.size() - maxEntries);

        for (int i = startIndex; i < snapshots.size(); i++) {
            TierSnapshot snapshot = snapshots.get(i);
            int entryY = startY + (i - startIndex) * entryHeight;

            // Draw date
            TextRenderHelper.drawText(
                    context,
                    this.textRenderer,
                    snapshot.getFormattedDate(),
                    windowX + 30,
                    entryY,
                    textColor,
                    true
            );

            // Draw tier with color based on tier
            int tierTextColor = getTierColor(snapshot.getTier());
            TextRenderHelper.drawText(
                    context,
                    this.textRenderer,
                    snapshot.getTier(),
                    windowX + 150,
                    entryY,
                    tierTextColor,
                    true
            );

            // Draw points
            TextRenderHelper.drawText(
                    context,
                    this.textRenderer,
                    String.valueOf(snapshot.getPoints()),
                    windowX + 250,
                    entryY,
                    pointsColor,
                    true
            );
        }

        // If we have enough data, draw a small trend indicator
        if (snapshots.size() >= 2) {
            TierSnapshot first = snapshots.get(0);
            TierSnapshot last = snapshots.get(snapshots.size() - 1);

            String trendText;
            int trendColor;

            if (last.getPoints() > first.getPoints()) {
                trendText = "▲ Improving";
                trendColor = 0x55FF55; // Green
            } else if (last.getPoints() < first.getPoints()) {
                trendText = "▼ Declining";
                trendColor = 0xFF5555; // Red
            } else {
                trendText = "◆ Stable";
                trendColor = 0xFFFF55; // Yellow
            }

            TextRenderHelper.drawText(
                    context,
                    this.textRenderer,
                    "Trend: " + trendText,
                    windowX + 20,
                    windowY + WINDOW_HEIGHT - 60,
                    trendColor,
                    true
            );
        }
    }

    private int getTierColor(String tier) {
        // Return color based on tier
        if (tier.startsWith("HT1")) return 0xFF55FF; // Pink
        if (tier.startsWith("LT1")) return 0xFF5555; // Red
        if (tier.startsWith("HT2")) return 0xFF5500; // Orange
        if (tier.startsWith("LT2")) return 0xFFAA00; // Gold
        if (tier.startsWith("HT3")) return 0xFFFF55; // Yellow
        if (tier.startsWith("LT3")) return 0x55FF55; // Green
        if (tier.startsWith("HT4")) return 0x55FFFF; // Aqua
        if (tier.startsWith("LT4")) return 0x5555FF; // Blue
        if (tier.startsWith("HT5")) return 0xAA00AA; // Purple
        if (tier.startsWith("LT5")) return 0xAAAAAA; // Gray
        return 0xFFFFFF; // White (default)
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // Custom tab button class (copied from TierScreen)
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
            // Get theme colors
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

            TextRenderHelper.drawCenteredText(
                    context,
                    MinecraftClient.getInstance().textRenderer,
                    this.getMessage().getString(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    this.selected ? 0xFFFFFF : 0xE0E0E0
            );
        }
    }
}