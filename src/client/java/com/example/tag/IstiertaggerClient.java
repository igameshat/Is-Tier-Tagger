package com.example.tag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dv8tion.jda.api.JDA;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class IstiertaggerClient implements ClientModInitializer {
	public static final String MOD_ID = "is-tier-tagger";
	private static final Logger LOGGER = LoggerFactory.getLogger("IstiertaggerClient");

	private static Object jda = null; // Changed from JDA to Object to avoid direct class reference
	private static boolean discordAvailable = false;

	private static final String DISCORD_TOKEN = "HIDDEN";


	private IsrealTiersApiService apiService;
	private TierUIManager uiManager;

	// Singleton instance
	private static IstiertaggerClient instance;

	private TierDisplayManager tierDisplayManager;
	private PlayerHistoryTracker historyTracker;


	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing IstiertaggerClient");

		// Store instance
		instance = this;

		// Load config
		ModConfig config = ModConfig.getInstance();

		// Initialize history tracker
		this.historyTracker = new PlayerHistoryTracker(LOGGER);
		TierScreen.setHistoryTracker(this.historyTracker);

		// Initialize theme manager
		ThemeManager themeManager = ThemeManager.getInstance();
		LOGGER.info("Initialized theme manager with {} themes", themeManager.getThemes().size());

		// Initialize context menu handler
		ContextMenuHandler menuHandler = ContextMenuHandler.getInstance();
		LOGGER.info("Initialized context menu handler");

		// Initialize services
		this.apiService = new IsrealTiersApiService(LOGGER);
		this.uiManager = new TierUIManager(LOGGER);

		// Initialize tier display manager
		this.tierDisplayManager = new TierDisplayManager(LOGGER, this.historyTracker, this.apiService);

		// Check if Discord classes are available
		try {
			Class.forName("net.dv8tion.jda.api.JDABuilder");
			discordAvailable = true;
			LOGGER.info("Discord JDA library is available");
		} catch (ClassNotFoundException e) {
			discordAvailable = false;
			LOGGER.warn("Discord JDA library not found - Discord features will be disabled");

			// Update config to disable Discord if library is missing
			if (config.isDiscordEnabled()) {
				config.setDiscordEnabled(false);
				config.save();
				LOGGER.info("Automatically disabled Discord integration in config due to missing library");
			}
		}

		// Initialize Discord JDA if enabled in config and libraries are available
		if (config.isDiscordEnabled() && discordAvailable) {
			initializeDiscord();
		}

		// Register commands
		registerCommands();

		// Register keybinding
		registerKeybinding();

		// Add command for directly opening GUI
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("istaggergui")
					.executes(context -> {
						MinecraftClient.getInstance().setScreen(new TierScreen());
						return 1;
					})
			);
		});

		// Add command for theme settings
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("istaggerthemes")
					.executes(context -> {
						MinecraftClient.getInstance().setScreen(new ThemeSettingsScreen(null));
						return 1;
					})
			);
		});

		// Add command for player comparison
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("istaggercompare")
					.executes(context -> {
						MinecraftClient.getInstance().setScreen(new PlayerComparisonScreen());
						return 1;
					})
					.then(argument("player1", StringArgumentType.word())
							.executes(context -> {
								PlayerComparisonScreen screen = new PlayerComparisonScreen();
								MinecraftClient.getInstance().setScreen(screen);

								// Pre-fill player1 field
								String player1 = StringArgumentType.getString(context, "player1");
								if (screen.player1Field != null) {
									screen.player1Field.setText(player1);
								}

								return 1;
							})
							.then(argument("player2", StringArgumentType.word())
									.executes(context -> {
										PlayerComparisonScreen screen = new PlayerComparisonScreen();
										MinecraftClient.getInstance().setScreen(screen);

										// Pre-fill both player fields
										String player1 = StringArgumentType.getString(context, "player1");
										String player2 = StringArgumentType.getString(context, "player2");

										if (screen.player1Field != null) {
											screen.player1Field.setText(player1);
										}

										if (screen.player2Field != null) {
											screen.player2Field.setText(player2);
										}

										// Trigger comparison
										screen.comparePlayers();

										return 1;
									})
							)
					)
			);
		});
	}

	/**
	 * Get the singleton instance
	 */
	public static IstiertaggerClient getInstance() {
		return instance;
	}

	/**
	 * Initialize Discord connection using reflection to avoid direct class references
	 */
	public static void initializeDiscord() {
		if (!discordAvailable) {
			LOGGER.warn("Cannot initialize Discord - JDA library not available");
			return;
		}

		String token = DISCORD_TOKEN;

		if (token != null && !token.isEmpty()) {
			try {
				// Use reflection to avoid direct class references
				Class<?> jdaBuilderClass = Class.forName("net.dv8tion.jda.api.JDABuilder");
				Class<?> gatewayIntentClass = Class.forName("net.dv8tion.jda.api.requests.GatewayIntent");
				Class<?> jdaClass = Class.forName("net.dv8tion.jda.api.JDA");
				Class<?> jdaStatusEnum = Class.forName("net.dv8tion.jda.api.JDA$Status");

				// Check if JDA is already initialized and connected
				if (jda != null) {
					Object statusValue = jdaClass.getMethod("getStatus").invoke(jda);
					Object connectedStatus = jdaStatusEnum.getField("CONNECTED").get(null);

					if (statusValue.equals(connectedStatus)) {
						LOGGER.info("Discord bot already initialized");
						return;
					}
				}

				// Create JDABuilder
				Object guildMembersIntent = gatewayIntentClass.getField("GUILD_MEMBERS").get(null);
				Object messageContentIntent = gatewayIntentClass.getField("MESSAGE_CONTENT").get(null);
				Object builder = jdaBuilderClass.getMethod("createDefault", String.class).invoke(null, token);

				// Enable intents
				jdaBuilderClass.getMethod("enableIntents", gatewayIntentClass, gatewayIntentClass)
						.invoke(builder, guildMembersIntent, messageContentIntent);

				// Build JDA
				jda = jdaBuilderClass.getMethod("build").invoke(builder);

				// Wait for JDA to be ready
				jdaClass.getMethod("awaitReady").invoke(jda);

				LOGGER.info("Discord bot initialized successfully");

				// If UI manager exists, pass JDA instance to it
				if (getInstance() != null && getInstance().uiManager != null) {
					getInstance().uiManager.setJda((JDA) jda);
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
	 * Shut down the Discord connection using reflection
	 */
	public static void shutdownDiscord() {
		if (!discordAvailable || jda == null) {
			return;
		}

		try {
			Class<?> jdaClass = Class.forName("net.dv8tion.jda.api.JDA");
			jdaClass.getMethod("shutdown").invoke(jda);
			jda = null;
			LOGGER.info("Discord bot shut down");
		} catch (Exception e) {
			LOGGER.error("Error shutting down Discord", e);
		}
	}

	/**
	 * Check if Discord is connected
	 */
	public static boolean isDiscordConnected() {
		if (!discordAvailable || jda == null) {
			return false;
		}

		try {
			Class<?> jdaClass = Class.forName("net.dv8tion.jda.api.JDA");
			Class<?> statusEnum = Class.forName("net.dv8tion.jda.api.JDA$Status");

			Object status = jdaClass.getMethod("getStatus").invoke(jda);
			Object connectedStatus = statusEnum.getField("CONNECTED").get(null);

			return status.equals(connectedStatus);
		} catch (Exception e) {
			LOGGER.error("Error checking Discord connection", e);
			return false;
		}
	}

	private boolean isValidFilter(String filter) {
		return filter.equals("crystal") ||
				filter.equals("sword") ||
				filter.equals("uhc") ||
				filter.equals("pot") ||
				filter.equals("smp");
	}


	/**
	 * Append tier emoji to player name
	 * This method is called from the EntityNameTagMixin
	 */
	public Text appendTierToPlayerName(PlayerEntity player, Text originalName) {
		if (player == null || !ModConfig.getInstance().isShowNameTagEmoji()) {
			return originalName;
		}

		try {
			String uuid = player.getUuid().toString();
			String username = player.getName().getString();

			// Get emoji for this player
			Text tierEmoji = TierDisplayManager.getPlayerTierEmoji(uuid, username);

			// If no emoji available, return original name
			if (tierEmoji.getString().isEmpty()) {
				return originalName;
			}

			// Append emoji to name
			return Text.empty()
					.append(originalName)
					.append(" ")
					.append(tierEmoji);
		} catch (Exception e) {
			LOGGER.error("Error appending tier to player name", e);
			return originalName;
		}
	}


	private void registerKeybinding() {
		// Register keybinding for opening GUI
		KeyBinding openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"Israel Tier Tagger Open GUI",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_I, // Default key (I for Israel Tiers)
				"Israel Tier Tagger"
		));

		// Register callback for when the key is pressed
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.wasPressed()) {
				client.setScreen(new TierScreen());
			}
		});
	}

	public Text appendPlayerTierEmoji(PlayerEntity player, Text originalName) {
		if (player == null || !ModConfig.getInstance().isShowNameTagEmoji()) {
			return originalName;
		}

		try {
			String uuid = player.getUuid().toString();
			String username = player.getName().getString();

			// Get emoji for this player
			Text tierEmoji = TierDisplayManager.getPlayerTierEmoji(uuid, username);

			// If no emoji available, return original name
			if (tierEmoji.getString().isEmpty()) {
				return originalName;
			}

			// Append emoji to name
			return Text.empty()
					.append(originalName)
					.append(" ")
					.append(tierEmoji);
		} catch (Exception e) {
			LOGGER.error("Error appending tier to player name", e);
			return originalName;
		}
	}

	public TierDisplayManager getTierDisplayManager() {
		return tierDisplayManager;
	}

	public PlayerHistoryTracker getHistoryTracker() {
		return historyTracker;
	}

	/**
	 * Register essential commands for API interaction
	 */
	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

			// 1. Main command for looking up player stats
			dispatcher.register(literal("istagger")
					.then(argument("username", StringArgumentType.word())
							.suggests((context, builder) -> {
								// Get online players for suggestions
								if (MinecraftClient.getInstance().getNetworkHandler() != null) {
									MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
											.map(entry -> entry.getProfile().getName())
											.forEach(builder::suggest);
								}
								return builder.buildFuture();
							})
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
												// Record player data if history tracking is enabled
												if (ModConfig.getInstance().isTrackPlayerHistory()) {
													historyTracker.recordPlayerData(uuid, username, data);
												}

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

			// 2. Command for viewing tier lists
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

			// 3. Command for comparing players
			dispatcher.register(literal("istaggercompare")
					.then(argument("player1", StringArgumentType.word())
							.then(argument("player2", StringArgumentType.word())
									.executes(context -> {
										FabricClientCommandSource source = context.getSource();
										String player1 = StringArgumentType.getString(context, "player1");
										String player2 = StringArgumentType.getString(context, "player2");

										source.sendFeedback(uiManager.createFeedbackMessage(
												"Comparing " + player1 + " and " + player2 + "..."));

										new Thread(() -> {
											try {
												comparePlayersInChat(source, player1, player2);
											} catch (Exception e) {
												LOGGER.error("Error comparing players", e);
												source.sendFeedback(uiManager.createFeedbackMessage(
														"§cError comparing players: " + e.getMessage()));
											}
										}, "PlayerComparison-Thread").start();

										return 1;
									})
							)
					));
		});
	}

	/**
	 * Compare players and display results in chat
	 */
	private void comparePlayersInChat(FabricClientCommandSource source, String player1, String player2) {
		try {
			// Fetch UUIDs
			String uuid1 = apiService.fetchUUID(player1);
			String uuid2 = apiService.fetchUUID(player2);

			if (uuid1 == null || uuid2 == null) {
				source.sendFeedback(uiManager.createFeedbackMessage(
						"§cCould not find one or both players."));
				return;
			}

			// Fetch player data
			apiService.fetchPlayerData(uuid1, (data1, success1) -> {
				if (!success1) {
					source.sendFeedback(uiManager.createFeedbackMessage(
							"§cCould not fetch data for " + player1));
					return;
				}

				apiService.fetchPlayerData(uuid2, (data2, success2) -> {
					if (!success2) {
						source.sendFeedback(uiManager.createFeedbackMessage(
								"§cCould not fetch data for " + player2));
						return;
					}

					// Both players data fetched successfully - display comparison
					displayPlayerComparison(source, player1, player2, data1, data2);
				});
			});
		} catch (Exception e) {
			LOGGER.error("Error in player comparison", e);
			source.sendFeedback(uiManager.createFeedbackMessage(
					"§cError comparing players: " + e.getMessage()));
		}
	}

	/**
	 * Display player comparison in chat
	 */
	private void displayPlayerComparison(FabricClientCommandSource source, String player1, String player2,
										 JsonObject data1, JsonObject data2) {
		try {
			// Extract userData
			JsonObject userData1 = data1.get("userData").getAsJsonObject();
			JsonObject userData2 = data2.get("userData").getAsJsonObject();

			// Parse stats
			JsonArray stats1 = userData1.getAsJsonArray("stats");
			JsonArray stats2 = userData2.getAsJsonArray("stats");

			if (stats1.isEmpty() || stats2.isEmpty()) {
				source.sendFeedback(uiManager.createFeedbackMessage(
						"§cNo stats available for one or both players."));
				return;
			}

			JsonObject gameStats1 = stats1.get(0).getAsJsonObject();
			JsonObject gameStats2 = stats2.get(0).getAsJsonObject();

			// Display header
			source.sendFeedback(Text.literal("§6=== Player Comparison: " + player1 + " vs " + player2 + " ==="));

			// Calculate total points for each player
			int totalPoints1 = 0;
			int totalPoints2 = 0;

			// Compare each game mode
			for (String gameMode : new String[]{"crystal", "sword", "uhc", "pot", "smp"}) {
				String gameModeDisplay = gameMode.substring(0, 1).toUpperCase() + gameMode.substring(1);

				// Get player 1 tier and points
				String tier1 = "N/A";
				int points1 = 0;

				JsonArray modeStats1 = gameStats1.has(gameMode) ? gameStats1.getAsJsonArray(gameMode) : null;
				if (modeStats1 != null && !modeStats1.isEmpty()) {
					JsonObject stat = modeStats1.get(0).getAsJsonObject();
					tier1 = stat.has("tier") ? stat.get("tier").getAsString() : "N/A";
					points1 = apiService.getPointsForTier(tier1);
					totalPoints1 += points1;
				}

				// Get player 2 tier and points
				String tier2 = "N/A";
				int points2 = 0;

				JsonArray modeStats2 = gameStats2.has(gameMode) ? gameStats2.getAsJsonArray(gameMode) : null;
				if (modeStats2 != null && !modeStats2.isEmpty()) {
					JsonObject stat = modeStats2.get(0).getAsJsonObject();
					tier2 = stat.has("tier") ? stat.get("tier").getAsString() : "N/A";
					points2 = apiService.getPointsForTier(tier2);
					totalPoints2 += points2;
				}

				// Display game mode comparison
				source.sendFeedback(Text.literal("§e" + gameModeDisplay + ": §b" +
						player1 + " (" + tier1 + ", " + points1 + " pts) §7vs §b" +
						player2 + " (" + tier2 + ", " + points2 + " pts)"));
			}

			// Display total scores
			source.sendFeedback(Text.literal(""));
			source.sendFeedback(Text.literal("§6Total Points:"));
			source.sendFeedback(Text.literal("§b" + player1 + ": §d" + totalPoints1 + " pts"));
			source.sendFeedback(Text.literal("§b" + player2 + ": §d" + totalPoints2 + " pts"));

			// Display winner
			if (totalPoints1 > totalPoints2) {
				source.sendFeedback(Text.literal("§a" + player1 + " has " + (totalPoints1 - totalPoints2) + " more points"));
			} else if (totalPoints2 > totalPoints1) {
				source.sendFeedback(Text.literal("§a" + player2 + " has " + (totalPoints2 - totalPoints1) + " more points"));
			} else {
				source.sendFeedback(Text.literal("§aBoth players have equal points"));
			}

		} catch (Exception e) {
			LOGGER.error("Error displaying comparison", e);
			source.sendFeedback(uiManager.createFeedbackMessage(
					"§cError displaying comparison: " + e.getMessage()));
		}
	}
}