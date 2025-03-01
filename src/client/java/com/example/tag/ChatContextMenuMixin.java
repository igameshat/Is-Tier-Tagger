package com.example.tag.mixin.client;

import com.example.tag.ContextMenuHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(ChatHud.class)
public abstract class ChatContextMenuMixin {
    // Shadow the visible messages field
    @Shadow private List<ChatHudLine.Visible> visibleMessages;

    /**
     * Inject into the render method to render our context menu
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        // Render the context menu if it's open
        ContextMenuHandler menuHandler = ContextMenuHandler.getInstance();
        if (menuHandler.isMenuOpen()) {
            menuHandler.render(context, mouseX, mouseY);
        }
    }

    /**
     * Inject into the mouseClicked method to handle clicks on chat messages
     * Using a more generic signature that will work across different Fabric API versions
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        ContextMenuHandler menuHandler = ContextMenuHandler.getInstance();

        // If menu is open, let it handle the click first
        if (menuHandler.isMenuOpen()) {
            if (menuHandler.mouseClicked(mouseX, mouseY, 0)) {
                cir.setReturnValue(true);
                return;
            } else {
                // If clicked outside the menu, close it
                menuHandler.closeMenu();
            }
        }

        // Check if the click was on a chat message
        if (mouseY >= 0 && mouseY < getVisibleLinesHeight()) {
            try {
                // Find message that was clicked
                int lineIndex = (int)(mouseY / 9); // 9 pixels per line
                if (lineIndex < 0 || lineIndex >= visibleMessages.size()) {
                    return;
                }

                // Check if we found a username
                String username = extractUsernameFromMessage(
                        (Text) visibleMessages.get(lineIndex).content(), (int)mouseX);

                if (username != null) {
                    // Show the context menu at the click position
                    menuHandler.showMenu((int)mouseX, (int)mouseY, username);
                    cir.setReturnValue(true);
                }
            } catch (Exception e) {
                // Log the error but don't crash the game
                System.err.println("Error processing chat click: " + e.getMessage());
            }
        }
    }

    /**
     * Get the height of visible chat lines
     */
    private int getVisibleLinesHeight() {
        return visibleMessages.size() * 9; // 9 is the line height
    }

    /**
     * Extract username from a chat message
     * This is our own implementation that doesn't rely on shadowed methods
     */
    private String extractUsernameFromMessage(Text message, int clickPositionX) {
        ContextMenuHandler menuHandler = ContextMenuHandler.getInstance();

        // Use the handler's own username extraction logic
        // The character index is approximated based on horizontal position
        int charIndex = (int)(clickPositionX / 5); // Rough approximation, 5 pixels per character
        return menuHandler.getUsernameFromChatClick(message, charIndex);
    }
}