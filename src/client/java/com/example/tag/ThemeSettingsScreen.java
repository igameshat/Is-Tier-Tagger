package com.example.tag;

import com.example.tag.fix.DirectTextRenderer;
import com.example.tag.fix.DirectTextRenderer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for managing themes - fixed to have no blurry text and correct border calls
 */
public class ThemeSettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThemeSettingsScreen");
    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 330; // Increased height for better spacing

    private final Screen parent;
    private final ThemeManager themeManager;

    private ButtonWidget editThemeButton;
    private ButtonWidget deleteThemeButton;

    // Theme list scrolling
    private final List<ThemeEntry> themeEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedThemeIndex = -1;

    public ThemeSettingsScreen(Screen parent) {
        super(Text.literal("Theme Settings"));
        this.parent = parent;
        this.themeManager = ThemeManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();

        // Calculate exact integer positions for sharpness
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Load theme entries
        loadThemeEntries();

        // Back button
        ButtonWidget backButton = ButtonWidget.builder(
                Text.literal("Back"),
                button -> close()
        ).dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(backButton);

        // Create new theme button
        ButtonWidget createThemeButton = ButtonWidget.builder(
                Text.literal("Create New"),
                button -> createNewTheme()
        ).dimensions(windowX + 120, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(createThemeButton);

        // Edit button
        this.editThemeButton = ButtonWidget.builder(
                Text.literal("Edit"),
                button -> editSelectedTheme()
        ).dimensions(windowX + 210, windowY + WINDOW_HEIGHT - 30, 70, 20).build();
        this.addDrawableChild(this.editThemeButton);

        // Delete button
        this.deleteThemeButton = ButtonWidget.builder(
                Text.literal("Delete"),
                button -> deleteSelectedTheme()
        ).dimensions(windowX + WINDOW_WIDTH - 90, windowY + WINDOW_HEIGHT - 30, 70, 20).build();
        this.addDrawableChild(this.deleteThemeButton);

        // Start with current theme selected
        String currentThemeName = themeManager.getCurrentTheme().getName();
        selectThemeByName(currentThemeName);

        // Update button states
        updateButtonStates();
    }

    /**
     * Load all themes into selectable entries
     */
    private void loadThemeEntries() {
        themeEntries.clear();

        List<ThemeManager.Theme> themes = themeManager.getThemes();
        for (ThemeManager.Theme theme : themes) {
            themeEntries.add(new ThemeEntry(theme.getName(), theme.getColors()));
        }
    }

    /**
     * Find and select a theme by name
     */
    private void selectThemeByName(String themeName) {
        for (int i = 0; i < themeEntries.size(); i++) {
            if (themeEntries.get(i).getName().equals(themeName)) {
                selectedThemeIndex = i;
                return;
            }
        }
        selectedThemeIndex = -1;
    }

    /**
     * Update button states based on selection
     */
    private void updateButtonStates() {
        boolean hasSelection = selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size();
        boolean isSelectedDefault = hasSelection && themeEntries.get(selectedThemeIndex).getName().equals("Default");

        // Can't edit or delete the Default theme
        this.editThemeButton.active = hasSelection && !isSelectedDefault;
        this.deleteThemeButton.active = hasSelection && !isSelectedDefault;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw a darkened solid background with pixel-perfect rendering
        DirectTextRenderer.drawRect(context, 0, 0, this.width, this.height, 0xCC000000);

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);
        int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
        int textSecondaryColor = config.getColor("text_secondary", 0xAAAAAA);
        int activeColor = config.getColor("tab_active", 0x4080FF);

        // Calculate exact pixel positions
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Draw window background with pixel-perfect rendering
        DirectTextRenderer.drawRect(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, backgroundColor);

        // Draw border with pixel-perfect edges
        DirectTextRenderer.drawBorder(context, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title with sharp text
        DirectTextRenderer.drawCenteredText(
                context,
                this.title.getString(),
                centerX,
                windowY + 10,
                titleColor
        );

        // Draw theme list header with sharp text
        DirectTextRenderer.drawText(
                context,
                "Available Themes:",
                windowX + 20,
                windowY + 30,
                textPrimaryColor
        );

        // Draw list area background and border with pixel-perfect rendering
        int listX = windowX + 20;
        int listY = windowY + 45;
        int listWidth = WINDOW_WIDTH - 40;
        int listHeight = WINDOW_HEIGHT - 100;

        DirectTextRenderer.drawRect(context, listX, listY, listWidth, listHeight, 0x80000000);
        DirectTextRenderer.drawBorder(context, listX, listY, listWidth, listHeight, 0xFFAAAAAA);

        // Draw theme entries with increased spacing for better readability
        int entryHeight = 30;
        int maxVisibleEntries = listHeight / entryHeight;

        for (int i = 0; i < maxVisibleEntries && i + scrollOffset < themeEntries.size(); i++) {
            int index = i + scrollOffset;
            ThemeEntry entry = themeEntries.get(index);

            int entryY = listY + (i * entryHeight);

            // Highlight selected entry with pixel-perfect rendering
            if (index == selectedThemeIndex) {
                DirectTextRenderer.drawRect(context, listX + 1, entryY, listWidth - 2, entryHeight, activeColor);
            }

            // Draw theme name with sharp text
            DirectTextRenderer.drawText(
                    context,
                    entry.getName(),
                    listX + 10,
                    entryY + 10,
                    index == selectedThemeIndex ? 0xFFFFFF : textSecondaryColor
            );

            // Draw active indicator if this is the current theme
            if (entry.getName().equals(themeManager.getCurrentTheme().getName())) {
                DirectTextRenderer.drawText(
                        context,
                        "✓ Active",
                        listX + listWidth - 70,
                        entryY + 10,
                        0x80FF80
                );
            }
        }

        // Draw help text with sharp rendering
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);
            String activeMessage = selected.getName().equals(themeManager.getCurrentTheme().getName())
                    ? "Current active theme"
                    : "Click again to activate theme";

            DirectTextRenderer.drawText(
                    context,
                    activeMessage,
                    windowX + 20,
                    windowY + WINDOW_HEIGHT - 55,
                    activeColor
            );
        }

        // Render all widgets first
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // First pass the click to widgets
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            return true;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Check if click is in the theme list area
        int listX = windowX + 20;
        int listY = windowY + 45;
        int listWidth = WINDOW_WIDTH - 40;
        int listHeight = WINDOW_HEIGHT - 100;

        if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= listY && mouseY < listY + listHeight) {

            // Calculate which theme was clicked
            int entryHeight = 30; // Match the increased height
            int clickedIndex = scrollOffset + (int)((mouseY - listY) / entryHeight);

            if (clickedIndex >= 0 && clickedIndex < themeEntries.size()) {
                // If clicking the same theme again, activate it
                if (clickedIndex == selectedThemeIndex) {
                    setSelectedThemeActive();
                } else {
                    // Otherwise just select it
                    selectedThemeIndex = clickedIndex;
                    updateButtonStates();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle scrolling the theme list
        if (verticalAmount != 0) {
            if (verticalAmount > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (verticalAmount < 0 && scrollOffset < themeEntries.size() - (WINDOW_HEIGHT - 100) / 30) {
                scrollOffset++;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Set the selected theme as active
     */
    private void setSelectedThemeActive() {
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);
            // Only change if not already active
            if (!selected.getName().equals(themeManager.getCurrentTheme().getName())) {
                themeManager.setCurrentTheme(selected.getName());
                // Force reload config to apply changes immediately
                ModConfig.getInstance().save();
            }
        }
    }

    /**
     * Create a new theme
     */
    private void createNewTheme() {
        assert this.client != null;
        this.client.setScreen(new ThemeEditorScreen(this, "", new HashMap<>(), true));
    }

    /**
     * Edit the selected theme
     */
    private void editSelectedTheme() {
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);
            assert this.client != null;
            this.client.setScreen(new ThemeEditorScreen(this, selected.getName(), selected.getColors(), false));
        }
    }

    /**
     * Delete the selected theme
     */
    private void deleteSelectedTheme() {
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);

            // Can't delete Default theme
            if (!selected.getName().equals("Default")) {
                themeManager.deleteTheme(selected.getName());
                loadThemeEntries();
                selectedThemeIndex = -1;
                updateButtonStates();
            }
        }
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Theme entry class for the list
     */
    private static class ThemeEntry {
        private final String name;
        private final Map<String, String> colors;

        public ThemeEntry(String name, Map<String, String> colors) {
            this.name = name;
            this.colors = colors;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getColors() {
            return colors;
        }
    }
}