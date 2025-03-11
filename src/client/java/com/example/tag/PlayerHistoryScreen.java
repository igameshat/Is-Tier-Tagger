package com.example.tag;

import com.example.tag.PlayerHistoryTracker.PlayerHistory;
import com.example.tag.PlayerHistoryTracker.TierSnapshot;
import com.example.tag.fix.DirectTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen to display historical tier data for a player
 * Enhanced with pixel-perfect rendering for maximum clarity
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

        // Calculate exact pixel positions for sharp rendering
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

    // Replace the render method in PlayerHistoryScreen.java with this implementation:

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background with pixel-perfect fill
        DirectTextRenderer.drawRect(context, 0, 0, this.width, this.height, 0x88000000);

        // Calculate exact pixel positions
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);

        // Draw window background with pixel-perfect edges
        DirectTextRenderer.drawRect(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, backgroundColor);

        // Draw border with pixel-perfect rendering
        DirectTextRenderer.drawBorder(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title with sharp text
        DirectTextRenderer.drawCenteredText(
                context,
                this.title.getString(),
                centerX,
                windowY + 10,
                titleColor
        );

        // Render history data with sharp text
        renderHistoryData(context, windowX, windowY);

        // Render widgets
        renderWidgets(context, mouseX, mouseY, delta);
    }

    // Add this private helper method to render widgets with crisp text
    private void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        // First render all drawable elements
        for (Element element : this.children()) {
            if (element instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        // Then re-render button text for sharpness
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

    // Replace the renderHistoryData method with this implementation:
    private void renderHistoryData(DrawContext context, int windowX, int windowY) {
        PlayerHistory history = historyTracker.getPlayerHistory(playerUuid);

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int textColor = config.getColor("text_primary", 0xFFFFFF);
        int secondaryColor = config.getColor("text_secondary", 0xAAAAAA);
        int tierColor = config.getColor("tier_text", 0x4080FF);
        int pointsColor = config.getColor("points_text", 0xFFAA00);

        if (history == null) {
            DirectTextRenderer.drawCenteredText(
                    context,
                    "No history data available for this player",
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    secondaryColor
            );
            return;
        }

        List<TierSnapshot> snapshots = history.getTierSnapshots(selectedTab);

        if (snapshots.isEmpty()) {
            DirectTextRenderer.drawCenteredText(
                    context,
                    "No history data for " + selectedTab,
                    windowX + WINDOW_WIDTH / 2,
                    windowY + 120,
                    secondaryColor
            );
            return;
        }

        // Draw current tier with sharp text
        TierSnapshot latest = snapshots.getLast();
        DirectTextRenderer.drawText(
                context,
                "Current Tier: " + latest.getTier() + " (" + latest.getPoints() + " points)",
                windowX + 20,
                windowY + 60,
                textColor
        );

        DirectTextRenderer.drawText(
                context,
                "Last Updated: " + latest.getFormattedDate(),
                windowX + 20,
                windowY + 75,
                secondaryColor
        );

        // Draw table headers with sharp text
        int tableY = windowY + 100;
        DirectTextRenderer.drawText(
                context,
                "Date",
                windowX + 30,
                tableY,
                secondaryColor
        );

        DirectTextRenderer.drawText(
                context,
                "Tier",
                windowX + 150,
                tableY,
                secondaryColor
        );

        DirectTextRenderer.drawText(
                context,
                "Points",
                windowX + 250,
                tableY,
                secondaryColor
        );

        // Draw history table with sharp text
        int entryHeight = 15;
        int startY = tableY + 20;
        int maxEntries = 10; // Show last 10 entries

        // Start from the most recent snapshots
        int startIndex = Math.max(0, snapshots.size() - maxEntries);

        for (int i = startIndex; i < snapshots.size(); i++) {
            TierSnapshot snapshot = snapshots.get(i);
            int entryY = startY + (i - startIndex) * entryHeight;

            // Draw date with sharp text
            DirectTextRenderer.drawText(
                    context,
                    snapshot.getFormattedDate(),
                    windowX + 30,
                    entryY,
                    textColor
            );

            // Draw tier with color based on tier
            int tierTextColor = getTierColor(snapshot.getTier());
            DirectTextRenderer.drawText(
                    context,
                    snapshot.getTier(),
                    windowX + 150,
                    entryY,
                    tierTextColor
            );

            // Draw points with sharp text
            DirectTextRenderer.drawText(
                    context,
                    String.valueOf(snapshot.getPoints()),
                    windowX + 250,
                    entryY,
                    pointsColor
            );
        }

        // If we have enough data, draw a small trend indicator with sharp text
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

            DirectTextRenderer.drawText(
                    context,
                    "Trend: " + trendText,
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
}