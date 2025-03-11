package com.example.tag;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the context menu for player names in chat
 */
public class ContextMenuHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ContextMenuHandler");

    // Singleton instance
    private static ContextMenuHandler instance;

    // State
    private boolean isMenuOpen = false;
    private int menuX;
    private int menuY;
    private String targetUsername;
    private final List<MenuOption> menuOptions = new ArrayList<>();

    // Regular expression to extract username from chat
    private static final Pattern USERNAME_PATTERN = Pattern.compile("<([A-Za-z0-9_]{1,16})>");

    private ContextMenuHandler() {
        // Initialize menu options
        initializeMenuOptions();
    }

    /**
     * Get the singleton instance
     */
    public static ContextMenuHandler getInstance() {
        if (instance == null) {
            instance = new ContextMenuHandler();
        }
        return instance;
    }

    /**
     * Initialize the menu options
     */
    private void initializeMenuOptions() {
        menuOptions.clear();

        // Add menu options
        menuOptions.add(new MenuOption("Look up Player", (username) -> {
            lookupPlayer(username, null);
        }));

        menuOptions.add(new MenuOption("Compare Players", (username) -> {
            openPlayerComparison(username);
        }));

        // Add game mode specific options
        menuOptions.add(new MenuOption("View Crystal PvP Stats", (username) -> {
            lookupPlayer(username, "crystal");
        }));

        menuOptions.add(new MenuOption("View Sword Stats", (username) -> {
            lookupPlayer(username, "sword");
        }));

        menuOptions.add(new MenuOption("View UHC Stats", (username) -> lookupPlayer(username, "uhc")));

        menuOptions.add(new MenuOption("View Pot Stats", (username) -> {
            lookupPlayer(username, "pot");
        }));

        menuOptions.add(new MenuOption("View SMP Stats", (username) -> {
            lookupPlayer(username, "smp");
        }));

        menuOptions.add(new MenuOption("Open in Browser", this::openInBrowser));
    }

    /**
     * Show the context menu at the specified position
     */
    public void showMenu(int x, int y, String username) {
        this.menuX = x;
        this.menuY = y;
        this.targetUsername = username;
        this.isMenuOpen = true;
    }

    /**
     * Close the context menu
     */
    public void closeMenu() {
        this.isMenuOpen = false;
    }

    /**
     * Check if the menu is open
     */
    public boolean isMenuOpen() {
        return isMenuOpen;
    }

    /**
     * Render the context menu
     */
    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!isMenuOpen || targetUsername == null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int textColor = config.getColor("text_primary", 0xFFFFFF);
        int hoverColor = config.getColor("tab_active", 0x4080FF);

        // Calculate menu dimensions
        int menuWidth = 180;
        int optionHeight = 20;
        int menuHeight = menuOptions.size() * optionHeight + 30; // +30 for title

        // Adjust position if menu would go off screen
        MinecraftClient client = MinecraftClient.getInstance();
        if (menuX + menuWidth > client.getWindow().getScaledWidth()) {
            menuX = client.getWindow().getScaledWidth() - menuWidth;
        }

        if (menuY + menuHeight > client.getWindow().getScaledHeight()) {
            menuY = client.getWindow().getScaledHeight() - menuHeight;
        }

        // Draw menu background
        context.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, backgroundColor);
        context.drawBorder(menuX, menuY, menuWidth, menuHeight, borderColor);

        // Draw title
        String title = "Actions for " + targetUsername;
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(title),
                menuX + menuWidth / 2,
                menuY + 10,
                textColor
        );

        // Draw options
        for (int i = 0; i < menuOptions.size(); i++) {
            MenuOption option = menuOptions.get(i);
            int optionY = menuY + 30 + (i * optionHeight);

            // Check if mouse is hovering this option
            boolean isHovering = mouseX >= menuX && mouseX < menuX + menuWidth &&
                    mouseY >= optionY && mouseY < optionY + optionHeight;

            // Draw option background on hover
            if (isHovering) {
                context.fill(menuX + 1, optionY, menuX + menuWidth - 1, optionY + optionHeight, hoverColor);
            }

            // Draw option text
            context.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(option.getLabel()),
                    menuX + 10,
                    optionY + 6,
                    textColor
            );
        }
    }

    /**
     * Handle mouse click on the context menu
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMenuOpen) {
            return false;
        }

        // Check if click is outside the menu
        int menuWidth = 180;
        int optionHeight = 20;
        int menuHeight = menuOptions.size() * optionHeight + 30;

        if (mouseX < menuX || mouseX >= menuX + menuWidth ||
                mouseY < menuY || mouseY >= menuY + menuHeight) {
            closeMenu();
            return false;
        }

        // Check if click is on an option
        if (mouseY >= menuY + 30) {
            int optionIndex = (int) ((mouseY - (menuY + 30)) / optionHeight);

            if (optionIndex >= 0 && optionIndex < menuOptions.size()) {
                // Execute the option action
                MenuOption option = menuOptions.get(optionIndex);
                option.execute(targetUsername);

                // Close the menu
                closeMenu();
                return true;
            }
        }

        return false;
    }

    /**
     * Try to extract a username from a chat message at the click position
     */
    public String getUsernameFromChatClick(Text message, int clickedIndex) {
        // Get the full text content
        String fullText = message.getString();

        // Check if click is within a reasonable text range
        if (clickedIndex < 0 || clickedIndex >= fullText.length()) {
            return null;
        }

        // Find potential username patterns
        Matcher matcher = USERNAME_PATTERN.matcher(fullText);
        while (matcher.find()) {
            int startPos = matcher.start();
            int endPos = matcher.end();

            if (clickedIndex >= startPos && clickedIndex < endPos) {
                // Return the captured username
                return matcher.group(1);
            }
        }

        // If not found with pattern, try to extract from Style
        if (message.getStyle() != null) {
            Style style = message.getStyle();
            if (style.getClickEvent() != null && style.getClickEvent().getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                String command = style.getClickEvent().getValue();
                if (command.startsWith("/msg ") || command.startsWith("/tell ") || command.startsWith("/w ")) {
                    String[] parts = command.split(" ", 3);
                    if (parts.length >= 2) {
                        return parts[1];
                    }
                }
            }
        }

        return null;
    }

    /**
     * Look up a player's stats
     */
    private void lookupPlayer(String username, String filter) {
        LOGGER.info("Looking up stats for {}", username);

        // Execute the /istagger command
        MinecraftClient client = MinecraftClient.getInstance();
        assert client.player != null;
        if (filter == null) {
            client.player.networkHandler.sendChatCommand("istagger " + username);
        } else {
            client.player.networkHandler.sendChatCommand("istagger " + username + " " + filter);
        }
    }

    /**
     * Open player comparison screen
     */
    private void openPlayerComparison(String username) {
        LOGGER.info("Opening player comparison with {}", username);

        // Create and show the comparison screen
        PlayerComparisonScreen screen = new PlayerComparisonScreen();
        MinecraftClient.getInstance().setScreen(screen);

        // Pre-fill the first player field with the target username
        if (screen.player1Field != null) {
            screen.player1Field.setText(username);
        }
    }

    /**
     * Open player profile in browser
     */
    private void openInBrowser(String username) {
        LOGGER.info("Opening browser for {}", username);

        String url = "https://israeltiers.com/p/" + username;

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            LOGGER.error("Error opening browser", e);
        }
    }

    /**
     * Menu option class
     */
    private static class MenuOption {
        private final String label;
        private final MenuAction action;

        public MenuOption(String label, MenuAction action) {
            this.label = label;
            this.action = action;
        }

        public String getLabel() {
            return label;
        }

        public void execute(String username) {
            if (action != null) {
                action.execute(username);
            }
        }
    }

    /**
     * Functional interface for menu actions
     */
    @FunctionalInterface
    private interface MenuAction {
        void execute(String username);
    }
}