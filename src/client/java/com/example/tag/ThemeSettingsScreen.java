package com.example.tag;

import net.minecraft.client.gui.DrawContext;
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
 * Screen for managing themes
 */
public class ThemeSettingsScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThemeSettingsScreen");
    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 300;

    private final Screen parent;
    private final ThemeManager themeManager;

    // UI components
    private ButtonWidget backButton;
    private ButtonWidget createThemeButton;
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

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Load theme entries
        loadThemeEntries();

        // Back button
        this.backButton = ButtonWidget.builder(
                Text.literal("Back"),
                button -> close()
        ).dimensions(windowX + 20, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.backButton);

        // Create new theme button
        this.createThemeButton = ButtonWidget.builder(
                Text.literal("Create New"),
                button -> createNewTheme()
        ).dimensions(windowX + 120, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.createThemeButton);

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
        // Draw a darkened solid background
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);
        int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
        int textSecondaryColor = config.getColor("text_secondary", 0xAAAAAA);
        int activeColor = config.getColor("tab_active", 0x4080FF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, backgroundColor);
        context.drawBorder(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, windowY + 10, titleColor);

        // Draw theme list
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Available Themes:"),
                windowX + 20,
                windowY + 30,
                textPrimaryColor
        );

        // Draw list area background and border
        int listX = windowX + 20;
        int listY = windowY + 45;
        int listWidth = WINDOW_WIDTH - 40;
        int listHeight = WINDOW_HEIGHT - 90;

        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80000000);
        context.drawBorder(listX, listY, listWidth, listHeight, 0xFFAAAAAA);

        // Draw theme entries
        int entryHeight = 25;
        int maxVisibleEntries = listHeight / entryHeight;

        for (int i = 0; i < maxVisibleEntries && i + scrollOffset < themeEntries.size(); i++) {
            int index = i + scrollOffset;
            ThemeEntry entry = themeEntries.get(index);

            int entryY = listY + (i * entryHeight);

            // Highlight selected entry
            if (index == selectedThemeIndex) {
                context.fill(listX + 1, entryY, listX + listWidth - 1, entryY + entryHeight, activeColor);
            }

            // Draw theme name
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(entry.getName()),
                    listX + 10,
                    entryY + 8,
                    index == selectedThemeIndex ? 0xFFFFFF : textSecondaryColor
            );

            // Draw active indicator if this is the current theme
            if (entry.getName().equals(themeManager.getCurrentTheme().getName())) {
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal("✓ Active"),
                        listX + listWidth - 70,
                        entryY + 8,
                        0x80FF80
                );
            }
        }

        // Draw help text
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);
            String activeMessage = selected.getName().equals(themeManager.getCurrentTheme().getName())
                    ? "Current active theme"
                    : "Click again to activate theme";

            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(activeMessage),
                    windowX + 20,
                    windowY + WINDOW_HEIGHT - 55,
                    activeColor
            );
        }

        // Render buttons last to ensure they're on top
        super.render(context, mouseX, mouseY, delta);
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
        int listHeight = WINDOW_HEIGHT - 90;

        if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= listY && mouseY < listY + listHeight) {

            // Calculate which theme was clicked
            int entryHeight = 25;
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
            } else if (verticalAmount < 0 && scrollOffset < themeEntries.size() - (WINDOW_HEIGHT - 90) / 25) {
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
        this.client.setScreen(new ThemeEditorScreen(this, "", new HashMap<>(), true));
    }

    /**
     * Edit the selected theme
     */
    private void editSelectedTheme() {
        if (selectedThemeIndex >= 0 && selectedThemeIndex < themeEntries.size()) {
            ThemeEntry selected = themeEntries.get(selectedThemeIndex);
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