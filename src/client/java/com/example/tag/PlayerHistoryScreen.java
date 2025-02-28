package com.example.tag;

import com.example.tag.PlayerHistoryTracker.PlayerHistory;
import com.example.tag.PlayerHistoryTracker.TierSnapshot;
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

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 0xCC000000);
        context.drawBorder(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFFFFFFF);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, windowY + 10, 0xFFFFFF);

        // Render history data
        renderHistoryData(context, windowX, windowY);

        // Call parent render which will render all children (buttons, etc.)
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHistoryData(DrawContext context, int windowX, int windowY) {
        PlayerHistory history = historyTracker.getPlayerHistory(playerUuid);

        if (history == null) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No history data available for this player"),
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    0xAAAAAA
            );
            return;
        }

        List<TierSnapshot> snapshots = history.getTierSnapshots(selectedTab);

        if (snapshots.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No history data for " + selectedTab),
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    0xAAAAAA
            );
            return;
        }

        // Draw current tier
        TierSnapshot latest = snapshots.get(snapshots.size() - 1);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Current Tier: " + latest.getTier() + " (" + latest.getPoints() + " points)"),
                windowX + 20,
                windowY + 60,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Last Updated: " + latest.getFormattedDate()),
                windowX + 20,
                windowY + 75,
                0xAAAAAA
        );

        // Draw table headers
        int tableY = windowY + 100;
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Date"),
                windowX + 30,
                tableY,
                0xAAAAAA
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Tier"),
                windowX + 150,
                tableY,
                0xAAAAAA
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Points"),
                windowX + 250,
                tableY,
                0xAAAAAA
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
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(snapshot.getFormattedDate()),
                    windowX + 30,
                    entryY,
                    0xFFFFFF
            );

            // Draw tier with color based on tier
            int tierColor = getTierColor(snapshot.getTier());
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(snapshot.getTier()),
                    windowX + 150,
                    entryY,
                    tierColor
            );

            // Draw points
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(String.valueOf(snapshot.getPoints())),
                    windowX + 250,
                    entryY,
                    0xFFAA00
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

            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal("Trend: " + trendText),
                    windowX + 20,
                    windowY + WINDOW_HEIGHT - 60,
                    trendColor
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
            // Custom rendering for tab buttons
            if (this.selected) {
                // Selected tab styling
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF4080FF);
            } else {
                // Normal tab styling
                context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xFF303030);
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