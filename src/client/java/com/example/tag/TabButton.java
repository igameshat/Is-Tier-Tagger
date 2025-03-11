package com.example.tag;

import com.example.tag.fix.DirectTextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Custom tab button with pixel-perfect rendering
 */
public class TabButton extends ButtonWidget {
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

        // Get exact pixel coordinates
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        // Draw background with pixel-perfect edges
        if (this.selected) {
            // Selected tab with full opacity and sharp edges
            DirectTextRenderer.drawRect(context, x, y, width, height, activeTabColor);
        } else {
            // Inactive tab with full opacity and sharp edges
            DirectTextRenderer.drawRect(context, x, y, width, height, inactiveTabColor);
        }

        // Use our sharp text rendering for maximum clarity
        String buttonText = this.getMessage().getString();

        // Calculate exact center position
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(buttonText);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;

        // Draw text with direct method for maximum sharpness
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                buttonText,
                textX,
                textY,
                this.selected ? 0xFFFFFF : 0xE0E0E0,
                false  // No shadow
        );
    }
}