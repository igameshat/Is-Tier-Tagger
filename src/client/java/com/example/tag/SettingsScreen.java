package com.example.tag;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;

/**
 * Settings screen for the Israel Tier Tagger mod
 */
public class SettingsScreen extends Screen {
    private static final int WINDOW_WIDTH = 350;
    private static final int WINDOW_HEIGHT = 300;

    private final Screen parent;
    private final ModConfig config;
    private final IsrealTiersApiService apiService;

    // UI components
    private ButtonWidget cancelButton;
    private ButtonWidget resetButton;
    private ButtonWidget clearCacheButton;

    // Using buttons instead of checkboxes since CheckboxWidget is causing issues
    private ButtonWidget autoOpenBrowserButton;
    private ButtonWidget colorfulOutputButton;
    private ButtonWidget discordEnabledButton;
    private ButtonWidget showLeaderboardButton;
    private ButtonWidget useCustomColorsButton;

    private TextFieldWidget apiTimeoutField;
    private TextFieldWidget cacheDurationField;
    private TextFieldWidget tierListCacheDurationField;
    private TextFieldWidget leaderboardEntriesField;

    // Settings that will be saved
    private boolean autoOpenBrowser;
    private boolean colorfulOutput;
    private boolean discordEnabled;
    private boolean showLeaderboard;
    private boolean useCustomColors;

    private int apiTimeoutSeconds;
    private int cacheDurationMinutes;
    private int tierListCacheDurationMinutes;
    private int leaderboardEntries;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Israel Tier Tagger Settings"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
        this.apiService = new IsrealTiersApiService(LoggerFactory.getLogger("SettingsScreen"));

        // Load current settings
        this.autoOpenBrowser = config.isAutoOpenBrowser();
        this.colorfulOutput = config.isColorfulOutput();
        this.discordEnabled = config.isDiscordEnabled();
        this.showLeaderboard = config.isShowLeaderboard();
        this.useCustomColors = config.isUseCustomColors();

        this.apiTimeoutSeconds = config.getApiTimeoutSeconds();
        this.cacheDurationMinutes = config.getCacheDurationMinutes();
        this.tierListCacheDurationMinutes = config.getTierListCacheDurationMinutes();
        this.leaderboardEntries = config.getLeaderboardEntries();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Toggle buttons for boolean options
        int buttonY = windowY + 35;

        // Button spacing SPACING
        int buttonSpacing = 22;
        int buttonWidth = 150;

        // Auto Open Browser toggle
        this.autoOpenBrowserButton = ButtonWidget.builder(
                Text.literal("Auto Open Browser: " + (this.autoOpenBrowser ? "ON" : "OFF")),
                button -> {
                    this.autoOpenBrowser = !this.autoOpenBrowser;
                    button.setMessage(Text.literal("Auto Open Browser: " + (this.autoOpenBrowser ? "ON" : "OFF")));
                }
        ).dimensions(windowX + 20, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(this.autoOpenBrowserButton);

        // Colorful Output toggle
        this.colorfulOutputButton = ButtonWidget.builder(
                Text.literal("Colorful Output: " + (this.colorfulOutput ? "ON" : "OFF")),
                button -> {
                    this.colorfulOutput = !this.colorfulOutput;
                    button.setMessage(Text.literal("Colorful Output: " + (this.colorfulOutput ? "ON" : "OFF")));
                }
        ).dimensions(windowX + 20, buttonY + buttonSpacing, buttonWidth, 20).build();
        this.addDrawableChild(this.colorfulOutputButton);

        // Discord Enabled toggle
        this.discordEnabledButton = ButtonWidget.builder(
                Text.literal("Enable Discord: " + (this.discordEnabled ? "ON" : "OFF")),
                button -> {
                    this.discordEnabled = !this.discordEnabled;
                    button.setMessage(Text.literal("Enable Discord: " + (this.discordEnabled ? "ON" : "OFF")));
                }
        ).dimensions(windowX + 20, buttonY + buttonSpacing * 2, buttonWidth, 20).build();
        this.addDrawableChild(this.discordEnabledButton);

        // Show Leaderboard toggle
        this.showLeaderboardButton = ButtonWidget.builder(
                Text.literal("Show Leaderboard: " + (this.showLeaderboard ? "ON" : "OFF")),
                button -> {
                    this.showLeaderboard = !this.showLeaderboard;
                    button.setMessage(Text.literal("Show Leaderboard: " + (this.showLeaderboard ? "ON" : "OFF")));
                }
        ).dimensions(windowX + 180, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(this.showLeaderboardButton);

        // Use Custom Colors toggle
        this.useCustomColorsButton = ButtonWidget.builder(
                Text.literal("Use Custom Colors: " + (this.useCustomColors ? "ON" : "OFF")),
                button -> {
                    this.useCustomColors = !this.useCustomColors;
                    button.setMessage(Text.literal("Use Custom Colors: " + (this.useCustomColors ? "ON" : "OFF")));
                }
        ).dimensions(windowX + 180, buttonY + buttonSpacing, buttonWidth, 20).build();
        this.addDrawableChild(this.useCustomColorsButton);

        // Text fields for numerical values
        int textFieldY = buttonY + buttonSpacing * 3 + 10;
        int textFieldSpacing = 25;
        int labelWidth = 150;
        int fieldWidth = 50;

        // API Timeout field
        this.apiTimeoutField = new TextFieldWidget(
                this.textRenderer,
                windowX + 20 + labelWidth,
                textFieldY,
                fieldWidth,
                20,
                Text.literal("")
        );
        this.apiTimeoutField.setText(String.valueOf(this.apiTimeoutSeconds));
        this.apiTimeoutField.setEditableColor(0xFFFFFF);
        this.addDrawableChild(this.apiTimeoutField);

        // Cache duration field
        this.cacheDurationField = new TextFieldWidget(
                this.textRenderer,
                windowX + 20 + labelWidth,
                textFieldY + textFieldSpacing,
                fieldWidth,
                20,
                Text.literal("")
        );
        this.cacheDurationField.setText(String.valueOf(this.cacheDurationMinutes));
        this.cacheDurationField.setEditableColor(0xFFFFFF);
        this.addDrawableChild(this.cacheDurationField);

        // Tier list cache duration field
        this.tierListCacheDurationField = new TextFieldWidget(
                this.textRenderer,
                windowX + 20 + labelWidth,
                textFieldY + textFieldSpacing * 2,
                fieldWidth,
                20,
                Text.literal("")
        );
        this.tierListCacheDurationField.setText(String.valueOf(this.tierListCacheDurationMinutes));
        this.tierListCacheDurationField.setEditableColor(0xFFFFFF);
        this.addDrawableChild(this.tierListCacheDurationField);

        // Leaderboard entries field
        this.leaderboardEntriesField = new TextFieldWidget(
                this.textRenderer,
                windowX + 20 + labelWidth,
                textFieldY + textFieldSpacing * 3,
                fieldWidth,
                20,
                Text.literal("")
        );
        this.leaderboardEntriesField.setText(String.valueOf(this.leaderboardEntries));
        this.leaderboardEntriesField.setEditableColor(0xFFFFFF);
        this.addDrawableChild(this.leaderboardEntriesField);

        // Buttons
        int buttonsY = windowY + WINDOW_HEIGHT - 30;
        int buttonsWidth = 100;

        //SPACING spacing between buttons
        int buttonsSpacing = 1;

        // Only display Done button (which will automatically save), Reset and Clear Cache
        this.cancelButton = ButtonWidget.builder(
                Text.literal("Done"),
                button -> this.close()
        ).dimensions(windowX + 20, buttonsY, buttonsWidth, 20).build();
        this.addDrawableChild(this.cancelButton);

        this.resetButton = ButtonWidget.builder(
                Text.literal("Reset to Default"),
                button -> this.resetToDefault()
        ).dimensions(windowX + 20 + buttonsWidth + buttonsSpacing, buttonsY, buttonsWidth + 20, 20).build();
        this.addDrawableChild(this.resetButton);

        this.clearCacheButton = ButtonWidget.builder(
                Text.literal("Clear Cache"),
                button -> this.clearCache()
        ).dimensions(windowX + 20 + buttonsWidth + buttonsSpacing + buttonsWidth + 20 + buttonsSpacing, buttonsY, buttonsWidth, 20).build();
        this.addDrawableChild(this.clearCacheButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background
        try {
            renderBackground(context, mouseX, mouseY, delta);
        } catch (NoSuchMethodError e) {
            // Fallback for different API versions
            context.fill(0, 0, this.width, this.height, 0x88000000);
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int windowX = centerX - WINDOW_WIDTH / 2;
        int windowY = centerY - WINDOW_HEIGHT / 2;

        // Draw window background
        context.fill(windowX, windowY, windowX + WINDOW_WIDTH, windowY + WINDOW_HEIGHT, 0xCC000000);
        context.drawBorder(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFFFFFFFF);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, windowY + 10, 0xFFFFFF);

        // Draw text field labels
        int textFieldY = windowY + 35 + 25 * 3 + 10;
        int textFieldSpacing = 25;

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("API Timeout (seconds):"),
                windowX + 20,
                textFieldY + 5,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Cache Duration (minutes):"),
                windowX + 20,
                textFieldY + textFieldSpacing + 5,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Tier List Cache (minutes):"),
                windowX + 20,
                textFieldY + textFieldSpacing * 2 + 5,
                0xFFFFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Leaderboard Entries:"),
                windowX + 20,
                textFieldY + textFieldSpacing * 3 + 5,
                0xFFFFFF
        );

        // Draw all widgets
        super.render(context, mouseX, mouseY, delta);
    }

    private void resetToDefault() {
        // Reset all values to defaults
        this.autoOpenBrowser = true;
        this.colorfulOutput = true;
        this.discordEnabled = true;
        this.showLeaderboard = true;
        this.useCustomColors = true;

        this.apiTimeoutSeconds = 20;
        this.cacheDurationMinutes = 15;
        this.tierListCacheDurationMinutes = 30;
        this.leaderboardEntries = 8;

        // Update UI components
        this.autoOpenBrowserButton.setMessage(Text.literal("Auto Open Browser: " + (this.autoOpenBrowser ? "ON" : "OFF")));
        this.colorfulOutputButton.setMessage(Text.literal("Colorful Output: " + (this.colorfulOutput ? "ON" : "OFF")));
        this.discordEnabledButton.setMessage(Text.literal("Enable Discord: " + (this.discordEnabled ? "ON" : "OFF")));
        this.showLeaderboardButton.setMessage(Text.literal("Show Leaderboard: " + (this.showLeaderboard ? "ON" : "OFF")));
        this.useCustomColorsButton.setMessage(Text.literal("Use Custom Colors: " + (this.useCustomColors ? "ON" : "OFF")));

        this.apiTimeoutField.setText(String.valueOf(this.apiTimeoutSeconds));
        this.cacheDurationField.setText(String.valueOf(this.cacheDurationMinutes));
        this.tierListCacheDurationField.setText(String.valueOf(this.tierListCacheDurationMinutes));
        this.leaderboardEntriesField.setText(String.valueOf(this.leaderboardEntries));
    }

    private void clearCache() {
        if (apiService != null) {
            apiService.clearCaches();
        }
    }

    private void saveSettings() {
        // Parse numerical values from text fields
        try {
            this.apiTimeoutSeconds = Integer.parseInt(this.apiTimeoutField.getText());
            this.cacheDurationMinutes = Integer.parseInt(this.cacheDurationField.getText());
            this.tierListCacheDurationMinutes = Integer.parseInt(this.tierListCacheDurationField.getText());
            this.leaderboardEntries = Integer.parseInt(this.leaderboardEntriesField.getText());
        } catch (NumberFormatException e) {
            // Ignore and use old values if parsing fails
        }

        // Apply limits to numerical values
        this.apiTimeoutSeconds = Math.max(1, Math.min(60, this.apiTimeoutSeconds));
        this.cacheDurationMinutes = Math.max(1, Math.min(60, this.cacheDurationMinutes));
        this.tierListCacheDurationMinutes = Math.max(1, Math.min(120, this.tierListCacheDurationMinutes));
        this.leaderboardEntries = Math.max(3, Math.min(20, this.leaderboardEntries));

        // Save to config
        config.setAutoOpenBrowser(this.autoOpenBrowser);
        config.setColorfulOutput(this.colorfulOutput);
        config.setDiscordEnabled(this.discordEnabled);
        config.setShowLeaderboard(this.showLeaderboard);
        config.setUseCustomColors(this.useCustomColors);

        config.setApiTimeoutSeconds(this.apiTimeoutSeconds);
        config.setCacheDurationMinutes(this.cacheDurationMinutes);
        config.setTierListCacheDurationMinutes(this.tierListCacheDurationMinutes);
        config.setLeaderboardEntries(this.leaderboardEntries);

        config.save();

        // Apply Discord settings immediately
        if (this.discordEnabled) {
            if (!IstiertaggerClient.isDiscordConnected()) {
                IstiertaggerClient.initializeDiscord();
            }
        } else {
            if (IstiertaggerClient.isDiscordConnected()) {
                IstiertaggerClient.shutdownDiscord();
            }
        }
    }

    @Override
    public void close() {
        // Auto-save settings when closing the screen
        saveSettings();
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}