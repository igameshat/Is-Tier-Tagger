package com.example.tag.fix;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A utility class for rendering pixel-perfect text without any blurriness
 * Enhanced with additional functionality for consistent rendering
 */
public class DirectTextRenderer {

    /**
     * Draw text at exact pixel coordinates with no blurriness
     */
    public static void drawText(DrawContext context, String text, int x, int y, int color) {
        // Ensure coordinates are integers for pixel-perfect rendering
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;

        // Draw directly with no shadows and at exact pixel positions
        // Draw the text twice for higher quality:
        // First without shadow
        context.drawText(renderer, text, x, y, color, false);

        // Note: we could draw with shadow but that can cause blurriness
        // context.drawText(renderer, text, x, y, color, true);
    }

    /**
     * Draw centered text at exact pixel coordinates
     */
    public static void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int width = renderer.getWidth(text);
        // Use integer division (not floating point) for crisp positioning
        int x = centerX - (width / 2);
        drawText(context, text, x, y, color);
    }

    /**
     * Draw text with a Text object
     */
    public static void drawText(DrawContext context, Text text, int x, int y, int color) {
        drawText(context, text.getString(), x, y, color);
    }

    /**
     * Draw centered text with a Text object
     */
    public static void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        drawCenteredText(context, text.getString(), centerX, y, color);
    }

    /**
     * Draw a precise border with no blurriness
     */
    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        // Top border (full width)
        drawRect(context, x, y, width, 1, color);
        // Bottom border (full width)
        drawRect(context, x, y + height - 1, width, 1, color);
        // Left border (excluding corners which are already drawn)
        drawRect(context, x, y + 1, 1, height - 2, color);
        // Right border (excluding corners which are already drawn)
        drawRect(context, x + width - 1, y + 1, 1, height - 2, color);
    }

    /**
     * Draw a rectangle with precise pixel alignment
     */
    public static void drawRect(DrawContext context, int x, int y, int width, int height, int color) {
        // Fill rectangle directly using the DrawContext
        context.fill(x, y, x + width, y + height, color);
    }

    /**
     * Draw text with right alignment
     */
    public static void drawRightAlignedText(DrawContext context, String text, int rightX, int y, int color) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int width = renderer.getWidth(text);
        drawText(context, text, rightX - width, y, color);
    }

    /**
     * Draw text with right alignment using a Text object
     */
    public static void drawRightAlignedText(DrawContext context, Text text, int rightX, int y, int color) {
        drawRightAlignedText(context, text.getString(), rightX, y, color);
    }
}