package com.example.tag.fix;

import com.example.tag.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A tab button that renders with absolutely no blurriness
 */
public class SharpTabButton extends ButtonWidget {
    private final String gameMode;
    private boolean selected;

    public SharpTabButton(int x, int y, int width, int height, Text text, PressAction onPress, String gameMode) {
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
        // Get theme colors from config
        ModConfig config = ModConfig.getInstance();
        int activeTabColor = config.getColor("tab_active", 0xFF4080FF);
        int inactiveTabColor = config.getColor("tab_inactive", 0xFF303030);

        // Use exact integer coordinates
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        // Draw background with pixel-perfect alignment
        context.fill(x, y, x + width, y + height, this.selected ? activeTabColor : inactiveTabColor);

        // Draw text directly with no transformations and no shadows
        String buttonText = this.getMessage().getString();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = textRenderer.getWidth(buttonText);

        // Calculate exact center
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;

        // CRITICAL: Draw text directly using our custom renderer
        DirectTextRenderer.drawText(context, buttonText, textX, textY, this.selected ? 0xFFFFFF : 0xE0E0E0);
    }
}