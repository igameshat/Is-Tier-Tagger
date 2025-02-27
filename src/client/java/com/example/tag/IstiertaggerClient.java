package com.example.tag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class IstiertaggerClient implements ClientModInitializer {
	public static final String MOD_ID = "is-tier-tagger";
	private static final Logger LOGGER = LoggerFactory.getLogger("IstiertaggerClient");

	private static JDA jda;

	// The token should be stored securely and not in source code
	private static final String DISCORD_TOKEN = ""

	private IsrealTiersApiService apiService;
	private TierUIManager uiManager;

    // Singleton instance
	private static IstiertaggerClient instance;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing IstiertaggerClient");

		// Store instance
		instance = this;

		// Load config
        ModConfig config = ModConfig.getInstance();

		// Initialize services
		this.apiService = new IsrealTiersApiService(LOGGER);
		this.uiManager = new TierUIManager(LOGGER);

		// Initialize Discord JDA if enabled in config
		if (config.isDiscordEnabled()) {
			initializeDiscord();
		}

		// Register commands
		registerCommands();

		// Check for ModMenu and initialize integration if available
	}

	/**
	 * Get the singleton instance
	 */
	public static IstiertaggerClient getInstance() {
		return instance;
	}

	/**
	 * Initialize Discord connection
	 */
	public static void initializeDiscord() {
		String token = DISCORD_TOKEN;

		if (token != null && !token.isEmpty()) {
			try {
				if (jda != null && jda.getStatus() == net.dv8tion.jda.api.JDA.Status.CONNECTED) {
					LOGGER.info("Discord bot already initialized");
					return;
				}

				jda = JDABuilder.createDefault(token)
						.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
						.build();

				// Wait for JDA to be ready
				jda.awaitReady();
				LOGGER.info("Discord bot initialized successfully");

				// If UI manager exists, pass JDA instance to it
				if (getInstance() != null && getInstance().uiManager != null) {
					getInstance().uiManager.setJda(jda);
				}
			} catch (Exception e) {
				LOGGER.error("Failed to initialize Discord bot", e);
				jda = null;
			}
		} else {
			LOGGER.info("Discord token not provided, Discord integration disabled");
		}
	}

	/**
	 * Shut down the Discord connection
	 */
	public static void shutdownDiscord() {
		if (jda != null) {
			jda.shutdown();
			jda = null;
			LOGGER.info("Discord bot shut down");
		}
	}

	/**
	 * Check if Discord is connected
	 */
	public static boolean isDiscordConnected() {
		return jda != null && jda.getStatus() == net.dv8tion.jda.api.JDA.Status.CONNECTED;
	}

	private boolean isValidFilter(String filter) {
		return filter.equals("crystal") ||
				filter.equals("sword") ||
				filter.equals("uhc") ||
				filter.equals("pot") ||
				filter.equals("smp");
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// /istagger command for looking up player stats
			dispatcher.register(literal("istagger")
					.then(argument("username", StringArgumentType.word())
							.executes(context -> {
								// Default command without filter
								String username = StringArgumentType.getString(context, "username");
								FabricClientCommandSource source = context.getSource();

								source.sendFeedback(uiManager.createFeedbackMessage("Looking up data for " + username + "..."));

								// Run in a separate thread to avoid blocking the main thread
								new Thread(() -> {
									try {
										String uuid = apiService.fetchUUID(username);
										if (uuid == null) {
											source.sendFeedback(uiManager.createFeedbackMessage("Could not find player: " + username));
											return;
										}

										apiService.fetchPlayerData(uuid, (data, success) -> {
											if (success) {
												uiManager.displayPlayerData(source, username, data, null);
											} else {
												uiManager.openInBrowser(username, source);
											}
										});
									} catch (Exception e) {
										LOGGER.error("Error in player lookup", e);
										source.sendFeedback(uiManager.createFeedbackMessage("Failed to look up player: " + e.getMessage()));
									}
								}, "PlayerLookup-Thread").start();

								return 1;
							})
							.then(argument("filter", StringArgumentType.word())
									.executes(context -> {
										String username = StringArgumentType.getString(context, "username");
										String filter = StringArgumentType.getString(context, "filter").toLowerCase();
										FabricClientCommandSource source = context.getSource();

										if (isValidFilter(filter)) {
											source.sendFeedback(uiManager.createFeedbackMessage(
													"Looking up data for " + username + " (" + filter + " only)..."));

											new Thread(() -> {
												try {
													String uuid = apiService.fetchUUID(username);
													if (uuid == null) {
														source.sendFeedback(uiManager.createFeedbackMessage("Could not find player: " + username));
														return;
													}

													apiService.fetchPlayerData(uuid, (data, success) -> {
														if (success) {
															uiManager.displayPlayerData(source, username, data, filter);
														} else {
															uiManager.openInBrowser(username, source);
														}
													});
												} catch (Exception e) {
													LOGGER.error("Error in player lookup", e);
													source.sendFeedback(uiManager.createFeedbackMessage("Failed to look up player: " + e.getMessage()));
												}
											}, "PlayerLookup-Thread").start();
										} else {
											source.sendFeedback(uiManager.createFeedbackMessage(
													"§cInvalid filter. Valid options: crystal, sword, uhc, pot, smp"));
										}
										return 1;
									})
							)
					));

			// /istaggertiers command for viewing tier lists
			dispatcher.register(literal("istaggertiers")
					.then(argument("filter", StringArgumentType.word())
							.executes(context -> {
								String filter = StringArgumentType.getString(context, "filter").toLowerCase();
								FabricClientCommandSource source = context.getSource();

								if (isValidFilter(filter)) {
									source.sendFeedback(uiManager.createFeedbackMessage("Fetching " + filter + " tier list..."));

									new Thread(() -> {
										try {
											apiService.fetchTierList(filter, (tiers, success) -> {
												if (success) {
													uiManager.displayTierList(source, filter, tiers, apiService);
												} else {
													source.sendFeedback(uiManager.createFeedbackMessage(
															"§cFailed to fetch tier list."));
												}
											});
										} catch (Exception e) {
											LOGGER.error("Error fetching tier list", e);
											source.sendFeedback(uiManager.createFeedbackMessage(
													"§cError fetching tier list: " + e.getMessage()));
										}
									}, "TierList-Thread").start();
								} else {
									source.sendFeedback(uiManager.createFeedbackMessage(
											"§cInvalid filter. Valid options: crystal, sword, uhc, pot, smp"));
								}
								return 1;
							})
					));
		});
	}
}