package com.example.tag;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Screen for editing a theme
 */
public class ThemeEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThemeEditorScreen");
    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 350;

    // Regex for validating hex color
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");

    private final Screen parent;
    private final ThemeManager themeManager;
    private final String originalThemeName;
    private final Map<String, String> originalColors;
    private final boolean isNewTheme;

    // Current edited state
    private String themeName;
    private Map<String, String> colors;

    // UI components
    private ButtonWidget doneButton;
    private TextFieldWidget nameField;
    private final List<ColorEntry> colorEntries = new ArrayList<>();

    // Scrolling
    private int scrollOffset = 0;

    // To track changes
    private boolean isDirty = false;

    public ThemeEditorScreen(Screen parent, String themeName, Map<String, String> colors, boolean isNewTheme) {
        super(Text.literal(isNewTheme ? "Create New Theme" : "Edit Theme"));
        this.parent = parent;
        this.themeManager = ThemeManager.getInstance();
        this.originalThemeName = themeName;
        this.originalColors = colors;
        this.isNewTheme = isNewTheme;

        // Make copies to avoid modifying the originals
        this.themeName = themeName;
        this.colors = new HashMap<>(colors);

        // If creating a new theme, ensure all color keys are present
        if (isNewTheme || this.colors.isEmpty()) {
            // Get default colors from the Default theme
            ThemeManager.Theme defaultTheme = themeManager.getThemeByName("Default");
            if (defaultTheme != null) {
                for (Map.Entry<String, String> entry : defaultTheme.getColors().entrySet()) {
                    if (!this.colors.containsKey(entry.getKey())) {
                        this.colors.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Name field
        this.nameField = new TextFieldWidget(
                this.textRenderer,
                windowX + 130,
                windowY + 30,
                WINDOW_WIDTH - 150,
                20,
                Text.literal("")
        );
        this.nameField.setText(themeName);
        this.nameField.setEditableColor(0xFFFFFF);
        this.nameField.setMaxLength(32);

        // Add change listener to automatically save
        this.nameField.setChangedListener(text -> {
            this.themeName = text;
            this.isDirty = true;
        });

        // If editing Default theme, name is not editable
        if (originalThemeName.equals("Default")) {
            this.nameField.setEditable(false);
        }

        this.addDrawableChild(this.nameField);

        // Create color entry fields
        createColorEntries();

        // Done button
        this.doneButton = ButtonWidget.builder(
                Text.literal("Done"),
                button -> {
                    if (isDirty) {
                        saveTheme();
                    }
                    close();
                }
        ).dimensions(centerX - 40, windowY + WINDOW_HEIGHT - 30, 80, 20).build();
        this.addDrawableChild(this.doneButton);
    }

    /**
     * Create editable text fields for each color entry
     */
    private void createColorEntries() {
        colorEntries.clear();

        int index = 0;
        // Sort keys for a consistent order
        List<String> sortedKeys = new ArrayList<>(colors.keySet());
        sortedKeys.sort(String::compareTo);

        for (String key : sortedKeys) {
            ColorEntry colorEntry = new ColorEntry(
                    key,
                    colors.get(key),
                    index
            );
            colorEntries.add(colorEntry);
            index++;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // First draw a solid background
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        // Get theme colors
        ModConfig config = ModConfig.getInstance();
        int backgroundColor = config.getColor("background", 0xCC000000);
        int borderColor = config.getColor("border", 0xFFFFFFFF);
        int titleColor = config.getColor("title", 0xFFFFFF);
        int textPrimaryColor = config.getColor("text_primary", 0xFFFFFF);
        int headerColor = config.getColor("tab_active", 0x4080FF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, backgroundColor);
        context.drawBorder(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, borderColor);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, windowY + 10, titleColor);

        // Draw theme name label
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Theme Name:"),
                windowX + 20,
                windowY + 35,
                textPrimaryColor
        );

        // Draw color entries area
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Theme Colors:"),
                windowX + 20,
                windowY + 60,
                headerColor
        );

        // Draw color entries list area background and border
        int listX = windowX + 20;
        int listY = windowY + 75;
        int listWidth = WINDOW_WIDTH - 40;
        int listHeight = WINDOW_HEIGHT - 115;

        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80000000);
        context.drawBorder(listX, listY, listWidth, listHeight, 0xFFAAAAAA);

        // Draw color entry labels and preview boxes
        int entryHeight = 25;
        int maxVisibleEntries = listHeight / entryHeight;

        // First loop - draw all the backgrounds and labels
        for (int i = 0; i < maxVisibleEntries && i + scrollOffset < colorEntries.size(); i++) {
            int index = i + scrollOffset;
            ColorEntry entry = colorEntries.get(index);

            int entryY = listY + (i * entryHeight);

            // Draw separator line
            if (i > 0) {
                context.fill(listX + 5, entryY - 1, listX + listWidth - 5, entryY, 0x40FFFFFF);
            }

            // Draw color key (label) with high contrast for readability
            String colorName = formatKeyName(entry.getKey());
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(colorName),
                    listX + 10,
                    entryY + 8,
                    0xFFFFFF // Always white for maximum clarity
            );

            // Set field position
            entry.getField().setX(listX + 150);
            entry.getField().setY(entryY + 3);

            // Set visibility
            entry.getField().setVisible(true);

            // Draw color preview box
            String colorValue = entry.getField().getText();
            if (isValidHexColor(colorValue)) {
                int color = parseColor(colorValue);
                context.fill(
                        listX + listWidth - 30,
                        entryY + 5,
                        listX + listWidth - 10,
                        entryY + entryHeight - 5,
                        color
                );
                context.drawBorder(
                        listX + listWidth - 30,
                        entryY + 5,
                        20,
                        entryHeight - 10,
                        0xFFFFFFFF
                );
            }

            // Draw description for this color setting
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(getColorDescription(entry.getKey())),
                    listX + 10,
                    entryY + entryHeight + 2,
                    0xCCCCCC  // Light gray
            );
        }

        // Make sure entries outside the visible area are hidden
        for (int i = 0; i < colorEntries.size(); i++) {
            ColorEntry entry = colorEntries.get(i);
            boolean isVisible = i >= scrollOffset && i < scrollOffset + maxVisibleEntries;
            entry.getField().setVisible(isVisible);
        }

        // Draw name field and Done button
        this.nameField.render(context, mouseX, mouseY, delta);
        this.doneButton.render(context, mouseX, mouseY, delta);

        // Now draw all the text fields on top to ensure they're not blurry
        for (int i = 0; i < maxVisibleEntries && i + scrollOffset < colorEntries.size(); i++) {
            int index = i + scrollOffset;
            ColorEntry entry = colorEntries.get(index);
            if (entry.getField().isVisible()) {
                entry.getField().render(context, mouseX, mouseY, delta);
            }
        }

        // Draw scroll hint if needed
        if (colorEntries.size() > maxVisibleEntries) {
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal("Scroll for more colors"),
                    windowX + listWidth - 120,
                    windowY + listHeight + 80,
                    0xAAAAAA
            );
        }
    }

    /**
     * Get a description for a color key
     */
    private String getColorDescription(String key) {
        switch (key) {
            case "background": return "Main window background color";
            case "border": return "Border around windows and elements";
            case "title": return "Title text at top of windows";
            case "tab_active": return "Selected tab highlighting";
            case "tab_inactive": return "Unselected tab color";
            case "text_primary": return "Main text color";
            case "text_secondary": return "Secondary/dimmed text";
            case "text_error": return "Error message text";
            case "tier_text": return "Player tier display";
            case "points_text": return "Point numbers display";
            case "button_default": return "Normal button color";
            case "button_hover": return "Button when hovered";
            case "button_disabled": return "Inactive button color";
            case "input_background": return "Text input background";
            case "input_border": return "Border around text inputs";
            case "input_text": return "Text in input fields";
            default: return "Color setting for UI element";
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle scrolling the color entries list
        if (verticalAmount != 0) {
            if (verticalAmount > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (verticalAmount < 0 && scrollOffset < colorEntries.size() - (WINDOW_HEIGHT - 115) / 25) {
                scrollOffset++;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Format a color key name for display (e.g., "background" -> "Background")
     */
    private String formatKeyName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : key.toCharArray()) {
            if (c == '_') {
                result.append(" ");
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    /**
     * Check if a string is a valid hex color
     */
    private boolean isValidHexColor(String color) {
        return color != null && HEX_COLOR_PATTERN.matcher(color).matches();
    }

    /**
     * Parse a hex color string to an integer color value
     */
    private int parseColor(String hexColor) {
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1);
        }

        try {
            // Parse color with alpha if it's 8 characters long
            if (hexColor.length() == 8) {
                return (int) Long.parseLong(hexColor, 16);
            } else if (hexColor.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hexColor, 16);
            }
        } catch (NumberFormatException e) {
            // If parsing fails, return black
        }

        return 0xFF000000;
    }

    /**
     * Save the theme
     */
    private void saveTheme() {
        // Get the theme name
        String newThemeName = this.nameField.getText().trim();

        // Validate theme name
        if (newThemeName.isEmpty()) {
            LOGGER.warn("Theme name is empty, not saving");
            return;
        }

        // If editing Default theme, don't allow name change
        if (originalThemeName.equals("Default")) {
            newThemeName = "Default";
        }

        // Check if name already exists and it's not the original name
        if (!newThemeName.equals(originalThemeName) && themeManager.getThemeByName(newThemeName) != null) {
            LOGGER.warn("Theme name already exists: {}", newThemeName);
            return;
        }

        // Update color values from fields
        Map<String, String> newColors = new HashMap<>();
        boolean hasInvalidColors = false;

        for (ColorEntry entry : colorEntries) {
            String colorValue = entry.getField().getText();

            // Validate color
            if (!isValidHexColor(colorValue)) {
                hasInvalidColors = true;
                continue;
            }

            newColors.put(entry.getKey(), colorValue);
        }

        if (hasInvalidColors) {
            LOGGER.warn("Theme contains invalid colors, not saving");
            return;
        }

        // If we're renaming a theme, delete the old one
        if (!isNewTheme && !newThemeName.equals(originalThemeName)) {
            themeManager.deleteTheme(originalThemeName);
        }

        // Save the theme
        themeManager.saveTheme(newThemeName, newColors);

        // If the edited theme was the active one, update it
        ModConfig config = ModConfig.getInstance();
        if (config.getColorSchemeName().equals(originalThemeName)) {
            themeManager.setCurrentTheme(newThemeName);
        }

        // Reset the dirty flag
        isDirty = false;
        LOGGER.info("Theme saved: {}", newThemeName);
    }

    /**
     * Auto-save on close if changes were made
     */
    @Override
    public void close() {
        if (isDirty) {
            saveTheme();
        }
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Color entry class for editing colors
     */
    private class ColorEntry {
        private final String key;
        private final int index;
        private final TextFieldWidget field;

        public ColorEntry(String key, String value, int index) {
            this.key = key;
            this.index = index;

            // Create text field for this color
            int centerX = ThemeEditorScreen.this.width / 2;
            int centerY = ThemeEditorScreen.this.height / 2;
            int windowX = centerX - WINDOW_WIDTH / 2;
            int windowY = centerY - WINDOW_HEIGHT / 2;
            int listX = windowX + 20;
            int listY = windowY + 75;

            // Initial position - will be updated in render()
            int entryY = listY + ((index % 100) * 25); // Just to ensure they're initially visible

            this.field = new TextFieldWidget(
                    ThemeEditorScreen.this.textRenderer,
                    listX + 150,
                    entryY,
                    150,
                    18,
                    Text.literal("")
            );
            this.field.setText(value);
            this.field.setEditableColor(0xFFFFFF);
            this.field.setMaxLength(9); // #RRGGBBAA

            // Only allow hex characters
            this.field.setTextPredicate(text -> {
                if (text.isEmpty() || text.equals("#")) {
                    return true;
                }
                return text.startsWith("#") && text.substring(1).matches("[0-9A-Fa-f]*");
            });

            // Add change listener to automatically update color
            this.field.setChangedListener(text -> {
                colors.put(key, text);
                isDirty = true;
            });

            ThemeEditorScreen.this.addDrawableChild(this.field);
        }

        public String getKey() {
            return key;
        }

        public int getIndex() {
            return index;
        }

        public TextFieldWidget getField() {
            return field;
        }
    }
}