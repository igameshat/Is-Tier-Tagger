package com.example.tag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced configuration handler with UI customization options
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("is-tier-tagger.json").toFile();
    private static ModConfig instance;

    // Basic settings
    private boolean autoOpenBrowser = true;
    private int apiTimeoutSeconds = 20;
    private boolean colorfulOutput = true;
    private String defaultFilter = "none";
    private boolean discordEnabled = true;

    // Cache settings
    private int cacheDurationMinutes = 15;
    private int tierListCacheDurationMinutes = 30;

    // UI settings
    private boolean compactMode = false;
    private boolean showLeaderboard = true;
    private int leaderboardEntries = 8;
    private boolean useCustomColors = true;
    private Map<String, String> colorScheme = new HashMap<>();
    // Add color scheme name to store the active theme name
    private String colorSchemeName = "Default";

    // Keybinding
    private String openGuiKey = "key.keyboard.i";

    // Added for new features
    private boolean showNameTagEmoji = true;
    private boolean trackPlayerHistory = true;
    private int maxHistoryDays = 30;

    /**
     * Get singleton instance
     */
    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Load config from file or create new
     */
    private static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);

                // Initialize default color scheme if not present
                if (config.colorScheme == null || config.colorScheme.isEmpty()) {
                    config.initializeDefaultColorScheme();
                }

                // Initialize colorSchemeName to Default if not present
                if (config.colorSchemeName == null || config.colorSchemeName.isEmpty()) {
                    config.colorSchemeName = "Default";
                }

                return config;
            } catch (IOException e) {
                System.err.println("Failed to load config file: " + e.getMessage());
            }
        }

        // If loading fails or file doesn't exist, create a new config
        ModConfig config = new ModConfig();
        config.initializeDefaultColorScheme();
        config.save();
        return config;
    }

    /**
     * Initialize default color scheme
     */
    private void initializeDefaultColorScheme() {
        colorScheme = new HashMap<>();
        colorScheme.put("background", "#000000CC");
        colorScheme.put("border", "#FFFFFFFF");
        colorScheme.put("title", "#FFFFFF");
        colorScheme.put("tab_active", "#4080FF");
        colorScheme.put("tab_inactive", "#303030");
        colorScheme.put("text_primary", "#FFFFFF");
        colorScheme.put("text_secondary", "#AAAAAA");
        colorScheme.put("text_error", "#FF5555");
        colorScheme.put("tier_text", "#4080FF");
        colorScheme.put("points_text", "#FFAA00");
    }

    /**
     * Save config to file
     */
    public void save() {
        try {
            if (!CONFIG_FILE.exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
                CONFIG_FILE.createNewFile();
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
        }
    }

    // Getters and setters for basic settings

    public boolean isAutoOpenBrowser() {
        return autoOpenBrowser;
    }

    public void setAutoOpenBrowser(boolean autoOpenBrowser) {
        this.autoOpenBrowser = autoOpenBrowser;
    }

    public int getApiTimeoutSeconds() {
        return apiTimeoutSeconds;
    }

    public void setApiTimeoutSeconds(int apiTimeoutSeconds) {
        this.apiTimeoutSeconds = apiTimeoutSeconds;
    }

    public boolean isColorfulOutput() {
        return colorfulOutput;
    }

    public void setColorfulOutput(boolean colorfulOutput) {
        this.colorfulOutput = colorfulOutput;
    }

    public String getDefaultFilter() {
        return defaultFilter;
    }

    public void setDefaultFilter(String defaultFilter) {
        this.defaultFilter = defaultFilter;
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public void setDiscordEnabled(boolean discordEnabled) {
        this.discordEnabled = discordEnabled;
    }

    // Getters and setters for cache settings

    public int getCacheDurationMinutes() {
        return cacheDurationMinutes;
    }

    public void setCacheDurationMinutes(int cacheDurationMinutes) {
        this.cacheDurationMinutes = cacheDurationMinutes;
    }

    public int getTierListCacheDurationMinutes() {
        return tierListCacheDurationMinutes;
    }

    public void setTierListCacheDurationMinutes(int tierListCacheDurationMinutes) {
        this.tierListCacheDurationMinutes = tierListCacheDurationMinutes;
    }

    // Getters and setters for UI settings

    public boolean isCompactMode() {
        return compactMode;
    }

    public void setCompactMode(boolean compactMode) {
        this.compactMode = compactMode;
    }

    public boolean isShowLeaderboard() {
        return showLeaderboard;
    }

    public void setShowLeaderboard(boolean showLeaderboard) {
        this.showLeaderboard = showLeaderboard;
    }

    public int getLeaderboardEntries() {
        return leaderboardEntries;
    }

    public void setLeaderboardEntries(int leaderboardEntries) {
        this.leaderboardEntries = leaderboardEntries;
    }

    public boolean isUseCustomColors() {
        return useCustomColors;
    }

    public void setUseCustomColors(boolean useCustomColors) {
        this.useCustomColors = useCustomColors;
    }

    public Map<String, String> getColorScheme() {
        return colorScheme;
    }

    public void setColorScheme(Map<String, String> colorScheme) {
        this.colorScheme = colorScheme;
    }

    // Add getter and setter for the color scheme name
    public String getColorSchemeName() {
        return colorSchemeName;
    }

    public void setColorSchemeName(String colorSchemeName) {
        this.colorSchemeName = colorSchemeName;
    }

    // Added getters and setters for new features

    public boolean isShowNameTagEmoji() {
        return showNameTagEmoji;
    }

    public void setShowNameTagEmoji(boolean showNameTagEmoji) {
        this.showNameTagEmoji = showNameTagEmoji;
    }

    public boolean isTrackPlayerHistory() {
        return trackPlayerHistory;
    }

    public void setTrackPlayerHistory(boolean trackPlayerHistory) {
        this.trackPlayerHistory = trackPlayerHistory;
    }

    public int getMaxHistoryDays() {
        return maxHistoryDays;
    }

    public void setMaxHistoryDays(int maxHistoryDays) {
        this.maxHistoryDays = maxHistoryDays;
    }

    /**
     * Get a color from the color scheme, with fallback to default
     * @param key Color key
     * @param defaultColor Default color if not found
     * @return Integer representation of the color
     */
    public int getColor(String key, int defaultColor) {
        if (!useCustomColors || colorScheme == null || !colorScheme.containsKey(key)) {
            return defaultColor;
        }

        try {
            String hexColor = colorScheme.get(key);
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }

            // Parse color with alpha if it's 8 characters long
            if (hexColor.length() == 8) {
                return (int) Long.parseLong(hexColor, 16);
            } else if (hexColor.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hexColor, 16);
            }
        } catch (NumberFormatException e) {
            // Fall back to default if parsing fails
        }

        return defaultColor;
    }

    // Keybinding settings

    public String getOpenGuiKey() {
        return openGuiKey;
    }

    public void setOpenGuiKey(String openGuiKey) {
        this.openGuiKey = openGuiKey;
    }
}