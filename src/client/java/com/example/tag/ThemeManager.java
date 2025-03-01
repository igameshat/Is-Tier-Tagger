package com.example.tag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages themes for the Israel Tier Tagger mod
 */
public class ThemeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ThemeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File THEMES_FILE = FabricLoader.getInstance().getConfigDir().resolve("is-tier-tagger-themes.json").toFile();

    private static ThemeManager instance;

    private List<Theme> themes;
    private String currentThemeName;

    /**
     * Represents a UI theme with a name and color scheme
     */
    public static class Theme {
        private String name;
        private Map<String, String> colors;

        public Theme(String name, Map<String, String> colors) {
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

    private ThemeManager() {
        themes = new ArrayList<>();
        loadThemes();

        // Get theme name from config
        ModConfig config = ModConfig.getInstance();
        currentThemeName = config.getColorSchemeName();

        // If current theme name is invalid or not found, default to "Default"
        if (currentThemeName == null || getThemeByName(currentThemeName) == null) {
            currentThemeName = "Default";
            config.setColorSchemeName("Default");
        }

        // If we have theme colors in config, try to match them to a theme
        if (config.isUseCustomColors() && config.getColorScheme() != null && !config.getColorScheme().isEmpty()) {
            // Find a theme that matches the colors in config
            for (Theme theme : themes) {
                if (colorsMatch(theme.getColors(), config.getColorScheme())) {
                    currentThemeName = theme.getName();
                    config.setColorSchemeName(theme.getName());
                    break;
                }
            }
        }

        // Make sure config and ThemeManager are synchronized
        config.setColorScheme(getThemeByName(currentThemeName).getColors());
        config.save();
    }

    /**
     * Compare two color maps to see if they represent the same theme
     */
    private boolean colorsMatch(Map<String, String> colors1, Map<String, String> colors2) {
        // Check a few key colors to determine if themes match
        String[] keyColors = {"background", "text_primary", "tab_active"};

        for (String key : keyColors) {
            if (colors1.containsKey(key) && colors2.containsKey(key)) {
                if (!colors1.get(key).equalsIgnoreCase(colors2.get(key))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get the singleton instance
     */
    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Load themes from file or create defaults
     */
    private void loadThemes() {
        if (THEMES_FILE.exists()) {
            try (FileReader reader = new FileReader(THEMES_FILE)) {
                Type themeListType = new TypeToken<ArrayList<Theme>>(){}.getType();
                List<Theme> loadedThemes = GSON.fromJson(reader, themeListType);

                if (loadedThemes != null && !loadedThemes.isEmpty()) {
                    themes = loadedThemes;
                    LOGGER.info("Loaded {} themes from file", themes.size());
                    return;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load themes file", e);
            }
        }

        // If loading fails or file doesn't exist, create default themes
        createDefaultThemes();
        saveThemes();
    }

    /**
     * Create default themes
     */
    private void createDefaultThemes() {
        themes.clear();

        // Default theme (Dark)
        Map<String, String> defaultColors = new HashMap<>();
        defaultColors.put("background", "#000000CC");
        defaultColors.put("border", "#FFFFFFFF");
        defaultColors.put("title", "#FFFFFF");
        defaultColors.put("tab_active", "#4080FF");
        defaultColors.put("tab_inactive", "#303030");
        defaultColors.put("text_primary", "#FFFFFF");
        defaultColors.put("text_secondary", "#AAAAAA");
        defaultColors.put("text_error", "#FF5555");
        defaultColors.put("tier_text", "#4080FF");
        defaultColors.put("points_text", "#FFAA00");
        defaultColors.put("button_default", "#505050");
        defaultColors.put("button_hover", "#707070");
        defaultColors.put("button_disabled", "#303030");
        defaultColors.put("input_background", "#000000");
        defaultColors.put("input_border", "#505050");
        defaultColors.put("input_text", "#FFFFFF");

        themes.add(new Theme("Default", defaultColors));

        // Light theme
        Map<String, String> lightColors = new HashMap<>();
        lightColors.put("background", "#FFFFFFCC");
        lightColors.put("border", "#505050FF");
        lightColors.put("title", "#000000");
        lightColors.put("tab_active", "#4080FF");
        lightColors.put("tab_inactive", "#DDDDDD");
        lightColors.put("text_primary", "#000000");
        lightColors.put("text_secondary", "#505050");
        lightColors.put("text_error", "#CC0000");
        lightColors.put("tier_text", "#0055DD");
        lightColors.put("points_text", "#DD7700");
        lightColors.put("button_default", "#DDDDDD");
        lightColors.put("button_hover", "#BBBBBB");
        lightColors.put("button_disabled", "#EEEEEE");
        lightColors.put("input_background", "#FFFFFF");
        lightColors.put("input_border", "#AAAAAA");
        lightColors.put("input_text", "#000000");

        themes.add(new Theme("Light", lightColors));

        // High Contrast theme
        Map<String, String> highContrastColors = new HashMap<>();
        highContrastColors.put("background", "#000000CC");
        highContrastColors.put("border", "#FFFFFFFF");
        highContrastColors.put("title", "#FFFFFF");
        highContrastColors.put("tab_active", "#FFFF00");
        highContrastColors.put("tab_inactive", "#303030");
        highContrastColors.put("text_primary", "#FFFFFF");
        highContrastColors.put("text_secondary", "#FFFF00");
        highContrastColors.put("text_error", "#FF0000");
        highContrastColors.put("tier_text", "#00FFFF");
        highContrastColors.put("points_text", "#FFFF00");
        highContrastColors.put("button_default", "#505050");
        highContrastColors.put("button_hover", "#808080");
        highContrastColors.put("button_disabled", "#303030");
        highContrastColors.put("input_background", "#000000");
        highContrastColors.put("input_border", "#FFFFFF");
        highContrastColors.put("input_text", "#FFFFFF");

        themes.add(new Theme("High Contrast", highContrastColors));

        // Crystal Blue theme
        Map<String, String> crystalBlueColors = new HashMap<>();
        crystalBlueColors.put("background", "#0A192FCC");
        crystalBlueColors.put("border", "#64FFDA");
        crystalBlueColors.put("title", "#64FFDA");
        crystalBlueColors.put("tab_active", "#64FFDA");
        crystalBlueColors.put("tab_inactive", "#172A45");
        crystalBlueColors.put("text_primary", "#E6F1FF");
        crystalBlueColors.put("text_secondary", "#8892B0");
        crystalBlueColors.put("text_error", "#FF5555");
        crystalBlueColors.put("tier_text", "#64FFDA");
        crystalBlueColors.put("points_text", "#FFC857");
        crystalBlueColors.put("button_default", "#172A45");
        crystalBlueColors.put("button_hover", "#303C55");
        crystalBlueColors.put("button_disabled", "#0A192F");
        crystalBlueColors.put("input_background", "#172A45");
        crystalBlueColors.put("input_border", "#64FFDA");
        crystalBlueColors.put("input_text", "#E6F1FF");

        themes.add(new Theme("Crystal Blue", crystalBlueColors));

        // Discord-inspired theme
        Map<String, String> discordColors = new HashMap<>();
        discordColors.put("background", "#36393FCC");
        discordColors.put("border", "#7289DA");
        discordColors.put("title", "#FFFFFF");
        discordColors.put("tab_active", "#7289DA");
        discordColors.put("tab_inactive", "#2F3136");
        discordColors.put("text_primary", "#FFFFFF");
        discordColors.put("text_secondary", "#B9BBBE");
        discordColors.put("text_error", "#F04747");
        discordColors.put("tier_text", "#7289DA");
        discordColors.put("points_text", "#FAA61A");
        discordColors.put("button_default", "#4F545C");
        discordColors.put("button_hover", "#7289DA");
        discordColors.put("button_disabled", "#36393F");
        discordColors.put("input_background", "#40444B");
        discordColors.put("input_border", "#72767D");
        discordColors.put("input_text", "#DCDDDE");

        themes.add(new Theme("Discord", discordColors));

        LOGGER.info("Created {} default themes", themes.size());
    }

    /**
     * Save themes to file
     */
    public void saveThemes() {
        try {
            if (!THEMES_FILE.exists()) {
                THEMES_FILE.getParentFile().mkdirs();
                THEMES_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(THEMES_FILE)) {
                GSON.toJson(themes, writer);
            }

            LOGGER.info("Saved {} themes to file", themes.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save themes file", e);
        }
    }

    /**
     * Get all available themes
     */
    public List<Theme> getThemes() {
        return themes;
    }

    /**
     * Get theme by name
     */
    public Theme getThemeByName(String name) {
        for (Theme theme : themes) {
            if (theme.getName().equals(name)) {
                return theme;
            }
        }
        return null;
    }

    /**
     * Get the current theme
     */
    public Theme getCurrentTheme() {
        Theme theme = getThemeByName(currentThemeName);
        if (theme == null) {
            theme = getThemeByName("Default");
        }
        return theme;
    }

    /**
     * Set the current theme by name
     */
    public void setCurrentTheme(String themeName) {
        if (getThemeByName(themeName) != null) {
            this.currentThemeName = themeName;

            // Update the ModConfig to use the new theme
            ModConfig config = ModConfig.getInstance();
            // Update both the colors and the theme name
            config.setColorScheme(getThemeByName(themeName).getColors());
            config.setColorSchemeName(themeName);
            config.save();
        }
    }

    /**
     * Add or update a theme
     */
    public void saveTheme(String name, Map<String, String> colors) {
        // Check if theme already exists
        Theme existingTheme = getThemeByName(name);
        if (existingTheme != null) {
            themes.remove(existingTheme);
        }

        // Add the new theme
        themes.add(new Theme(name, colors));
        saveThemes();
    }

    /**
     * Delete a theme
     */
    public boolean deleteTheme(String name) {
        // Don't allow deleting the default theme
        if (name.equals("Default")) {
            return false;
        }

        Theme theme = getThemeByName(name);
        if (theme != null) {
            themes.remove(theme);

            // If the deleted theme was the current theme, switch to Default
            if (name.equals(currentThemeName)) {
                setCurrentTheme("Default");
            }

            saveThemes();
            return true;
        }
        return false;
    }


    public void resetToDefaultTheme() {
        // Force reset to the default theme
        Theme defaultTheme = getThemeByName("Default");
        if (defaultTheme != null) {
            setCurrentTheme("Default");

            // Reset color scheme in config
            ModConfig config = ModConfig.getInstance();
            config.setUseCustomColors(false);
            config.save();
        }
    }
}