package com.example.tag;

import com.example.tag.fix.DirectTextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
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
 * Screen for editing a theme with better spacing and no blurry text
 * Fixed to include context in all border drawing calls
 */
public class ThemeEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThemeEditorScreen");
    private static final int WINDOW_WIDTH = 450; // Increased width for better spacing
    private static final int WINDOW_HEIGHT = 400; // Increased height for better spacing

    // Regex for validating hex color
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");

    private final Screen parent;
    private final ThemeManager themeManager;
    private final String originalThemeName;
    private final boolean isNewTheme;

    // Current edited state
    private String themeName;
    private final Map<String, String> colors;

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

        // Calculate exact integer positions for sharpness
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
        int headerColor = config.getColor("tab_active", 0x4080FF);
        int textColor = config.getColor("text_primary", 0xFFFFFF);

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

        // Draw theme name label with sharp text
        DirectTextRenderer.drawText(
                context,
                "Theme Name:",
                windowX + 20,
                windowY + 35,
                textColor
        );

        // Draw color entries area with sharp text
        DirectTextRenderer.drawText(
                context,
                "Theme Colors:",
                windowX + 20,
                windowY + 60,
                headerColor
        );

        // Draw color entries list area background and border with pixel-perfect rendering
        int listX = windowX + 20;
        int listY = windowY + 75;
        int listWidth = WINDOW_WIDTH - 40;
        int listHeight = WINDOW_HEIGHT - 115;

        DirectTextRenderer.drawRect(context, listX, listY, listWidth, listHeight, 0x80000000);
        DirectTextRenderer.drawBorder(context, listX, listY, listWidth, listHeight, 0xFFAAAAAA);

        // Draw color entry labels and preview boxes with increased spacing
        int entryHeight = 40; // Increased height for better spacing
        int maxVisibleEntries = listHeight / entryHeight;

        for (int i = 0; i < maxVisibleEntries && i + scrollOffset < colorEntries.size(); i++) {
            int index = i + scrollOffset;
            ColorEntry entry = colorEntries.get(index);

            int entryY = listY + (i * entryHeight);

            // Draw separator line with pixel-perfect rendering
            if (i > 0) {
                DirectTextRenderer.drawRect(context, listX + 5, entryY - 1, listWidth - 10, 1, 0x40FFFFFF);
            }

            // Draw color key (label) with sharp text
            String colorName = formatKeyName(entry.getKey());
            DirectTextRenderer.drawText(
                    context,
                    colorName,
                    listX + 10,
                    entryY + 10, // Adjusted spacing
                    0xFFFFFF
            );

            // Set field position with better spacing
            entry.getField().setX(listX + 150);
            entry.getField().setY(entryY + 8); // Adjusted spacing

            // Set visibility
            entry.getField().setVisible(true);

            // Draw color preview box with pixel-perfect rendering
            String colorValue = entry.getField().getText();
            if (isValidHexColor(colorValue)) {
                int color = parseColor(colorValue);
                DirectTextRenderer.drawRect(
                        context,
                        listX + listWidth - 40, // More space for preview
                        entryY + 8,
                        30,
                        20,
                        color
                );
                DirectTextRenderer.drawBorder(
                        context,
                        listX + listWidth - 40,
                        entryY + 8,
                        30,
                        20,
                        0xFFFFFFFF
                );
            }

            // Draw description for this color setting with sharp text
            DirectTextRenderer.drawText(
                    context,
                    getColorDescription(entry.getKey()),
                    listX + 10,
                    entryY + 28, // Adjusted spacing for description
                    0xCCCCCC
            );
        }

        // Make sure entries outside the visible area are hidden
        for (int i = 0; i < colorEntries.size(); i++) {
            ColorEntry entry = colorEntries.get(i);
            boolean isVisible = i >= scrollOffset && i < scrollOffset + maxVisibleEntries;
            entry.getField().setVisible(isVisible);
        }

        // Render widgets with crisp text
        renderWidgets(context, mouseX, mouseY, delta);

        // Draw scroll hint if needed
        if (colorEntries.size() > maxVisibleEntries) {
            DirectTextRenderer.drawText(
                    context,
                    "Scroll for more colors",
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
        return switch (key) {
            case "background" -> "Main window background color";
            case "border" -> "Border around windows and elements";
            case "title" -> "Title text at top of windows";
            case "tab_active" -> "Selected tab highlighting";
            case "tab_inactive" -> "Unselected tab color";
            case "text_primary" -> "Main text color";
            case "text_secondary" -> "Secondary/dimmed text";
            case "text_error" -> "Error message text";
            case "tier_text" -> "Player tier display";
            case "points_text" -> "Point numbers display";
            case "button_default" -> "Normal button color";
            case "button_hover" -> "Button when hovered";
            case "button_disabled" -> "Inactive button color";
            case "input_background" -> "Text input background";
            case "input_border" -> "Border around text inputs";
            case "input_text" -> "Text in input fields";
            default -> "Color setting for UI element";
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle scrolling the color entries list
        if (verticalAmount != 0) {
            int entryHeight = 40; // Match the increased height
            int listHeight = WINDOW_HEIGHT - 115;
            int maxVisibleEntries = listHeight / entryHeight;
            int maxScrollOffset = Math.max(0, colorEntries.size() - maxVisibleEntries);

            if (verticalAmount > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (verticalAmount < 0 && scrollOffset < maxScrollOffset) {
                scrollOffset++;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

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

        // Render text fields with crisp text
        for (ColorEntry entry : colorEntries) {
            if (entry.getField().isVisible()) {
                // Re-render text field value if needed
                // We don't need to do extra rendering here since text fields
                // typically render their own text clearly
            }
        }
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
        assert this.client != null;
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

            // Create text field for this color with exact positions for sharpness
            int centerX = ThemeEditorScreen.this.width / 2;
            int centerY = ThemeEditorScreen.this.height / 2;
            int windowX = centerX - WINDOW_WIDTH / 2;
            int windowY = centerY - WINDOW_HEIGHT / 2;
            int listX = windowX + 20;
            int listY = windowY + 75;

            // Initial position - will be updated in render()
            int entryY = listY + ((index % 100) * 40); // Adjusted for 40px spacing

            this.field = new TextFieldWidget(
                    ThemeEditorScreen.this.textRenderer,
                    listX + 150,
                    entryY,
                    150, // Wider field
                    20,
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