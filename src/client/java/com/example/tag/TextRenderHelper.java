package com.example.tag.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Simplified helper class for text rendering
 * Compatible with your specific Minecraft version
 */
public class TextRenderHelper {

    public static void drawSharpText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        if (context == null || textRenderer == null) return;

        try {
            // Ensure x and y are integers
            int intX = (int) Math.round(x);
            int intY = (int) Math.round(y);

            context.drawText(textRenderer, text, intX, intY, color, false);
        } catch (Exception e) {
            // Fallback rendering
            context.drawText(textRenderer, text, x, y, color, false);
        }
    }
    /**
     * Draw text using DrawContext
     */
    public static void drawText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow) {
        if (context == null || textRenderer == null) return;

        try {
            if (shadow) {
                context.drawTextWithShadow(textRenderer, text, x, y, color);
            } else {
                context.drawText(textRenderer, text, x, y, color, false);
            }
        } catch (Throwable e) {
            // If the above fails, we can't render but at least we won't crash
        }
    }

    /**
     * Draw Text object with DrawContext
     */
    public static void drawText(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow) {
        if (text == null) return;
        drawText(context, textRenderer, text.getString(), x, y, color, shadow);
    }

    /**
     * Get text width
     */
    public static int getTextWidth(TextRenderer textRenderer, String text) {
        if (textRenderer == null) {
            textRenderer = MinecraftClient.getInstance().textRenderer;
            if (textRenderer == null) {
                return text.length() * 6; // Approximate if no renderer available
            }
        }

        try {
            return textRenderer.getWidth(text);
        } catch (Throwable e) {
            return text.length() * 6; // Approximate as fallback
        }
    }

    /**
     * Draw centered text
     */
    public static void drawCenteredText(DrawContext context, TextRenderer textRenderer, String text, int centerX, int y, int color) {
        int width = getTextWidth(textRenderer, text);
        drawText(context, textRenderer, text, centerX - width / 2, y, color, true);
    }

    /**
     * Draw centered Text
     */
    public static void drawCenteredText(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
        if (text == null) return;
        drawCenteredText(context, textRenderer, text.getString(), centerX, y, color);
    }

    /**
     * Draw border with compatibility fallback
     */
    public static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        if (context == null) return;

        try {
            // Try built-in method first
            context.drawBorder(x, y, width, height, color);
        } catch (Throwable e) {
            // Fallback to simple rectangle outlining
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
}