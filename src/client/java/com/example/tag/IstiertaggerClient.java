package com.example.tag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class IstiertaggerClient implements ClientModInitializer {
	public static final String MOD_ID = "is-tier-tagger";
	private static final Logger LOGGER = LoggerFactory.getLogger("IstiertaggerClient");
	private static final Gson GSON = new Gson();
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(20))
			.build();

	private static JDA jda;
	private static final String DISCORD_TOKEN = null;

	private HttpRequest.Builder createApiRequest(String uuid) {
		return HttpRequest.newBuilder()
				.uri(URI.create("https://israeltiers.com/api/user/" + uuid))
				.header("accept", "application/json, text/plain, */*")
				.header("accept-language", "en-US,en;q=0.9")
				.header("referer", "https://israeltiers.com/p/" + uuid)
				.header("sec-fetch-dest", "empty")
				.header("sec-fetch-mode", "cors")
				.header("sec-fetch-site", "same-origin")
				.header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36")
				.timeout(Duration.ofSeconds(20));
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("istagger")
					.then(argument("username", StringArgumentType.word())
							.executes(context -> {
								// Default command without filter
								handlePlayerLookup(context.getSource(), StringArgumentType.getString(context, "username"), null);
								return 1;
							})
							.then(argument("filter", StringArgumentType.word())
									.executes(context -> {
										String username = StringArgumentType.getString(context, "username");
										String filter = StringArgumentType.getString(context, "filter").toLowerCase();
										if (isValidFilter(filter)) {
											handlePlayerLookup(context.getSource(), username, filter);
										} else {
											((FabricClientCommandSource) context.getSource()).getPlayer().sendMessage(
													Text.literal("§cInvalid filter. Valid options: crystal, sword, uhc, pot, smp"),
													false
											);
										}
										return 1;
									})
							)
					));

			// Add command for viewing tier lists
			dispatcher.register(literal("istaggertiers")
					.then(argument("filter", StringArgumentType.word())
							.executes(context -> {
								String filter = StringArgumentType.getString(context, "filter").toLowerCase();
								if (isValidFilter(filter)) {
									handleTierList(context.getSource(), filter);
								} else {
									((FabricClientCommandSource) context.getSource()).getPlayer().sendMessage(
											Text.literal("§cInvalid filter. Valid options: crystal, sword, uhc, pot, smp"),
											false
									);
								}
								return 1;
							})
					));
		});
	}

	private static final JsonObject TIER_POINTS = GSON.fromJson(
			"{ \"HT1\": 60, \"LT1\": 44, \"HT2\": 28, \"LT2\": 16, \"HT3\": 10, \"LT3\": 6, \"HT4\": 4, \"LT4\": 3, \"HT5\": 2, \"LT5\": 1 }",
			JsonObject.class
	);

	private int getPointsForTier(String tier) {
		try {
			if (tier != null && !tier.isEmpty() && TIER_POINTS.has(tier)) {
				return TIER_POINTS.get(tier).getAsInt();
			}
		} catch (Exception e) {
			LOGGER.error("Error getting points for tier: " + tier, e);
		}
		return 0;
	}

	private boolean isValidFilter(String filter) {
		return filter.equals("crystal") ||
				filter.equals("sword") ||
				filter.equals("uhc") ||
				filter.equals("pot") ||
				filter.equals("smp");
	}

	private void handleTierList(FabricClientCommandSource source, String filter) {
		source.getPlayer().sendMessage(Text.literal("Fetching " + filter + " tier list..."), false);

		new Thread(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create("https://api.israeltiers.com/api/tiers?filter=" + filter))
						.header("accept", "application/json")
						.GET()
						.timeout(Duration.ofSeconds(20))
						.build();

				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					JsonArray tiers = GSON.fromJson(response.body(), JsonArray.class);
					displayTierList(source, filter, tiers);
				} else {
					source.getPlayer().sendMessage(
							Text.literal("§cFailed to fetch tier list. Status: " + response.statusCode()),
							false
					);
				}
			} catch (Exception e) {
				LOGGER.error("Error fetching tier list", e);
				source.getPlayer().sendMessage(
						Text.literal("§cError fetching tier list: " + e.getMessage()),
						false
				);
			}
		}, "TierList-Thread").start();
	}

	private void displayDiscordInfo(FabricClientCommandSource source, String discordId) {
		if (jda != null) {
			try {
				jda.retrieveUserById(discordId).queue(
						user -> {
							if (user != null) {
								// Create clickable discord name
								source.getPlayer().sendMessage(
										Text.literal("§7Discord: ")
												.append(Text.literal("§f" + user.getName())
														.styled(style -> style.withClickEvent(
																new net.minecraft.text.ClickEvent(
																		net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD,
																		discordId
																)
														))
														.styled(style -> style.withHoverEvent(
																new net.minecraft.text.HoverEvent(
																		net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																		Text.literal("§7Click to copy Discord ID")
																)
														))
												),
										false
								);
							} else {
								displayDiscordId(source, discordId);
							}
						},
						error -> {
							LOGGER.error("Error fetching Discord user", error);
							displayDiscordId(source, discordId);
						}
				);
			} catch (Exception e) {
				LOGGER.error("Error with Discord lookup", e);
				displayDiscordId(source, discordId);
			}
		} else {
			displayDiscordId(source, discordId);
		}
	}

	private void displayDiscordId(FabricClientCommandSource source, String discordId) {
		source.getPlayer().sendMessage(
				Text.literal("§7Discord ID: ")
						.append(Text.literal("§f" + discordId)
								.styled(style -> style.withClickEvent(
										new net.minecraft.text.ClickEvent(
												net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD,
												discordId
										)
								))
								.styled(style -> style.withHoverEvent(
										new net.minecraft.text.HoverEvent(
												net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
												Text.literal("§7Click to copy Discord ID")
										)
								))
						),
				false
		);
	}

	private void displayTierList(FabricClientCommandSource source, String filter, JsonArray tiers) {
		source.getPlayer().sendMessage(Text.literal("\n§6=== " + filter.toUpperCase() + " Tier List ==="), false);

		// Sort players by points
		List<Map.Entry<String, Integer>> sortedPlayers = new ArrayList<>();

		for (int i = 0; i < tiers.size(); i++) {
			JsonObject player = tiers.get(i).getAsJsonObject();
			String uuid = player.get("minecraftUUID").getAsString();
			JsonArray filterStats = player.getAsJsonArray(filter);

			if (filterStats != null && !filterStats.isEmpty()) {
				JsonObject stat = filterStats.get(0).getAsJsonObject();
				String tier = stat.get("tier").getAsString();
				int points = getPointsForTier(tier);
				sortedPlayers.add(new AbstractMap.SimpleEntry<>(uuid, points));
			}
		}

		// Sort by points (highest first)
		sortedPlayers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

		// Display sorted list
		for (int i = 0; i < sortedPlayers.size(); i++) {
			Map.Entry<String, Integer> entry = sortedPlayers.get(i);
			String uuid = entry.getKey();
			int points = entry.getValue();

			// Find player data again
			for (int j = 0; j < tiers.size(); j++) {
				JsonObject player = tiers.get(j).getAsJsonObject();
				if (player.get("minecraftUUID").getAsString().equals(uuid)) {
					JsonArray filterStats = player.getAsJsonArray(filter);
					JsonObject stat = filterStats.get(0).getAsJsonObject();
					String tier = stat.get("tier").getAsString();
					String lastUpdate = stat.get("lastupdate").getAsString();

					try {
						String username = fetchUsernameFromUUID(uuid);
						String formattedTime = formatUnixTimestamp(lastUpdate);
						source.getPlayer().sendMessage(
								Text.literal(String.format("#%d §e%s: §b%s §d(%d points) §7(Last updated: §f%s§7)",
										i + 1, username, tier, points, formattedTime)),
								false
						);
					} catch (Exception e) {
						LOGGER.error("Error fetching username for UUID: " + uuid, e);
					}
					break;
				}
			}
		}
	}

	private String fetchUsernameFromUUID(String uuid) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.mojang.com/user/profile/" + uuid))
				.GET()
				.build();

		HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() == 200) {
			JsonObject profile = GSON.fromJson(response.body(), JsonObject.class);
			return profile.get("name").getAsString();
		}
		return uuid;
	}

	private void handlePlayerLookup(FabricClientCommandSource source, String username, String filter) {
		source.getPlayer().sendMessage(Text.literal("Looking up data for " + username +
				(filter != null ? " (" + filter + " only)" : "") + "..."), false);

		new Thread(() -> {
			try {
				String uuid = fetchUUID(username);
				if (uuid == null) {
					source.getPlayer().sendMessage(Text.literal("Could not find player: " + username), false);
					return;
				}

				HttpRequest request = createApiRequest(uuid)
						.GET()
						.build();

				HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					JsonObject data = GSON.fromJson(response.body(), JsonObject.class);
					displayPlayerData(source, username, data, filter);
				} else {
					openInBrowser(uuid, username, source);
				}
			} catch (Exception e) {
				LOGGER.error("Error in player lookup", e);
				try {
					String uuid = fetchUUID(username);
					if (uuid != null) {
						source.getPlayer().sendMessage(Text.literal("API request failed, opening in browser..."), false);
						openInBrowser(uuid, username, source);
					} else {
						source.getPlayer().sendMessage(Text.literal("Could not find player: " + username), false);
					}
				} catch (Exception ex) {
					source.getPlayer().sendMessage(Text.literal("Failed to look up player: " + ex.getMessage()), false);
				}
			}
		}, "PlayerLookup-Thread").start();
	}

	private String fetchUUID(String username) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
				.GET()
				.timeout(Duration.ofSeconds(10))
				.build();

		HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() == 200) {
			JsonObject jsonObject = GSON.fromJson(response.body(), JsonObject.class);
			String uuid = jsonObject.get("id").getAsString();
			return uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
		}
		return null;
	}

	private void openInBrowser(String uuid, String username, FabricClientCommandSource source) {
		try {
			String url = "https://israeltiers.com/api/user/" + uuid;
			URI uri = new URI(url);

			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("win")) {
				Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
			} else if (os.contains("mac")) {
				Runtime.getRuntime().exec(new String[]{"open", url});
			} else {
				Runtime.getRuntime().exec(new String[]{"xdg-open", url});
			}

			source.getPlayer().sendMessage(Text.literal("Opened in browser: " + url), false);
		} catch (Exception e) {
			LOGGER.error("Error opening browser", e);
			source.getPlayer().sendMessage(Text.literal("Failed to open browser: " + e.getMessage()), false);
		}
	}

	private void displayPlayerData(FabricClientCommandSource source, String username, JsonObject data, String filter) {
		try {
			// Main header
			source.getPlayer().sendMessage(Text.literal("§6=== Player Data for " + username + " ==="), false);

			// Basic info
			String uuid = data.get("id").getAsString();
			source.getPlayer().sendMessage(
					Text.literal("§7UUID: ")
							.append(Text.literal("§f" + uuid)
									.styled(style -> style.withClickEvent(
											new net.minecraft.text.ClickEvent(
													net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD,
													uuid
											)
									))
									.styled(style -> style.withHoverEvent(
											new net.minecraft.text.HoverEvent(
													net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
													Text.literal("§7Click to copy UUID")
											)
									))
							),
					false
			);

			// Username with NameMC link
			source.getPlayer().sendMessage(
					Text.literal("§7Username: ")
							.append(Text.literal("§f" + username)
									.styled(style -> style.withClickEvent(
											new net.minecraft.text.ClickEvent(
													net.minecraft.text.ClickEvent.Action.OPEN_URL,
													"https://namemc.com/profile/" + username
											)
									))
									.styled(style -> style.withHoverEvent(
											new net.minecraft.text.HoverEvent(
													net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
													Text.literal("§7Click to view on NameMC")
											)
									))
							),
					false
			);

			// Parse userData
			JsonObject userData = data.get("userData").getAsJsonObject();
			String discordId = userData.get("discordId").getAsString();

			if (jda != null) {
				try {
					net.dv8tion.jda.api.entities.User user = jda.retrieveUserById(discordId).complete();
					if (user != null) {
						source.getPlayer().sendMessage(
								Text.literal("§7Discord: ")
										.append(Text.literal("§f" + user.getName())
												.styled(style -> style.withClickEvent(
														new net.minecraft.text.ClickEvent(
																net.minecraft.text.ClickEvent.Action.COPY_TO_CLIPBOARD,
																discordId
														)
												))
												.styled(style -> style.withHoverEvent(
														new net.minecraft.text.HoverEvent(
																net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
																Text.literal("§7Click to copy Discord ID: " + discordId)
														)
												))
										),
								false
						);
					} else {
						displayDiscordId(source, discordId);
					}
				} catch (Exception e) {
					LOGGER.error("Error with Discord lookup: " + e.getMessage());
					displayDiscordId(source, discordId);
				}
			} else {
				displayDiscordId(source, discordId);
			}

			// Parse stats
			JsonArray stats = userData.getAsJsonArray("stats");
			if (!stats.isEmpty()) {
				JsonObject gameStats = stats.get(0).getAsJsonObject();

				if (filter == null) {
					source.getPlayer().sendMessage(Text.literal("\n§6=== Game Stats ==="), false);

					// Calculate total points
					int totalPoints = 0;
					for (String gameMode : new String[]{"crystal", "pot", "sword", "uhc", "smp"}) {
						JsonArray modeStats = gameStats.getAsJsonArray(gameMode);
						if (modeStats != null && !modeStats.isEmpty()) {
							JsonObject stat = modeStats.get(0).getAsJsonObject();
							String tier = stat.get("tier").getAsString();
							totalPoints += getPointsForTier(tier);
						}
					}

					source.getPlayer().sendMessage(Text.literal("§6Total Points: §d" + totalPoints), false);

					// Display all game modes
					displayGameMode(source, gameStats, "crystal", "Crystal", username);
					displayGameMode(source, gameStats, "pot", "Pot", username);
					displayGameMode(source, gameStats, "sword", "Sword", username);
					displayGameMode(source, gameStats, "uhc", "UHC", username);
					displayGameMode(source, gameStats, "smp", "SMP", username);
				} else {
					// Display only the filtered game mode
					displayGameMode(source, gameStats, filter,
							filter.substring(0, 1).toUpperCase() + filter.substring(1), username);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error formatting player data", e);
			source.getPlayer().sendMessage(Text.literal("§cError formatting data: " + e.getMessage()), false);
		}
	}

	private void displayGameMode(FabricClientCommandSource source, JsonObject gameStats,
								 String gameMode, String displayName, String username) {
		try {
			JsonArray modeStats = gameStats.getAsJsonArray(gameMode);
			if (modeStats != null && !modeStats.isEmpty()) {
				JsonObject stat = modeStats.get(0).getAsJsonObject();
				String tier = stat.get("tier").getAsString();
				String lastUpdate = stat.get("lastupdate").getAsString();

				// Only display if there's actual data
				if (!tier.isEmpty() || !lastUpdate.isEmpty()) {
					String formattedTime = formatUnixTimestamp(lastUpdate);

					// Create clickable game mode stats
					source.getPlayer().sendMessage(
							Text.literal("§e" + displayName + ": ")
									.append(Text.literal("§b" + tier)
											.styled(style -> style.withClickEvent(
													new net.minecraft.text.ClickEvent(
															net.minecraft.text.ClickEvent.Action.OPEN_URL,
															"https://israeltiers.com/p/" + username
													)
											))
											.styled(style -> style.withHoverEvent(
													new net.minecraft.text.HoverEvent(
															net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
															Text.literal("§7Click to view profile")
													)
											))
									)
									.append(Text.literal(" §d(" + getPointsForTier(tier) + " points) "))
									.append(Text.literal("§7(Last updated: §f" + formattedTime + "§7)")),
							false
					);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error displaying game mode: " + gameMode, e);
		}
	}

	private String formatUnixTimestamp(String timestamp) {
		try {
			long unixTime = Long.parseLong(timestamp);
			java.util.Date date = new java.util.Date(unixTime * 1000L); // Convert to milliseconds
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			return sdf.format(date);
		} catch (Exception e) {
			LOGGER.error("Error formatting timestamp: " + timestamp, e);
			return timestamp; // Return original if parsing fails
		}
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing IstiertaggerClient");

		// Initialize JDA
		try {
			jda = JDABuilder.createDefault(DISCORD_TOKEN)
					.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
					.build();

			// Wait for JDA to be ready
			jda.awaitReady();
			LOGGER.info("Discord bot initialized successfully");
		} catch (Exception e) {
			LOGGER.error("Failed to initialize Discord bot", e);
			jda = null;
		}

		registerCommands();
	}
}
