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
	private static final String DISCORD_TOKEN = "YOUR_BOT_TOKEN_HERE"; // Replace with your bot token

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
								handlePlayerLookup(context.getSource(), StringArgumentType.getString(context, "username"));
								return 1;
							})));
		});
	}

	private void handlePlayerLookup(FabricClientCommandSource source, String username) {
		source.getPlayer().sendMessage(Text.literal("Looking up data for " + username + "..."), false);

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
					displayPlayerData(source, username, data);
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

	private void displayPlayerData(FabricClientCommandSource source, String username, JsonObject data) {
		try {
			// Main header
			source.getPlayer().sendMessage(Text.literal("§6=== Player Data for " + username + " ==="), false);

			// Basic info
			source.getPlayer().sendMessage(Text.literal("§7UUID: §f" + data.get("id").getAsString()), false);
			source.getPlayer().sendMessage(Text.literal("§7Username: §f" + data.get("name").getAsString()), false);

			// Parse userData
			JsonObject userData = data.get("userData").getAsJsonObject();
			String discordId = userData.get("discordId").getAsString();

			// Fetch Discord username using JDA
			if (jda != null) {
				jda.retrieveUserById(discordId).queue(
						discordUser -> {
							if (discordUser != null) {
								String discordName = discordUser.getName();
								source.getPlayer().sendMessage(
										Text.literal("§7Discord: §f" + discordName + " §7(ID: §f" + discordId + "§7)"),
										false
								);
							}
						},
						error -> {
							LOGGER.error("Error fetching Discord user", error);
							source.getPlayer().sendMessage(Text.literal("§7Discord ID: §f" + discordId), false);
						}
				);
			} else {
				source.getPlayer().sendMessage(Text.literal("§7Discord ID: §f" + discordId), false);
			}

			// Parse stats
			JsonArray stats = userData.getAsJsonArray("stats");
			if (stats.size() > 0) {
				JsonObject gameStats = stats.get(0).getAsJsonObject();

				source.getPlayer().sendMessage(Text.literal("\n§6=== Game Stats ==="), false);

				displayGameMode(source, gameStats, "crystal", "Crystal");
				displayGameMode(source, gameStats, "pot", "Pot");
				displayGameMode(source, gameStats, "sword", "Sword");
				displayGameMode(source, gameStats, "uhc", "UHC");
				displayGameMode(source, gameStats, "smp", "SMP");
			}
		} catch (Exception e) {
			LOGGER.error("Error formatting player data", e);
			source.getPlayer().sendMessage(Text.literal("§cError formatting data: " + e.getMessage()), false);
		}
	}

	private void displayGameMode(FabricClientCommandSource source, JsonObject gameStats, String gameMode, String displayName) {
		try {
			JsonArray modeStats = gameStats.getAsJsonArray(gameMode);
			if (modeStats != null && modeStats.size() > 0) {
				JsonObject stat = modeStats.get(0).getAsJsonObject();
				String tier = stat.get("tier").getAsString();
				String lastUpdate = stat.get("lastupdate").getAsString();

				// Only display if there's actual data
				if (!tier.isEmpty() || !lastUpdate.isEmpty()) {
					StringBuilder line = new StringBuilder();
					line.append("§e").append(displayName).append(": ");

					if (!tier.isEmpty()) {
						line.append("§b").append(tier);
					} else {
						line.append("§7No tier");
					}

					if (!lastUpdate.isEmpty()) {
						// Format the timestamp
						String formattedTime = formatTimestamp(lastUpdate);
						line.append(" §7(Last updated: §f").append(formattedTime).append("§7)");
					}

					source.getPlayer().sendMessage(Text.literal(line.toString()), false);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error displaying game mode: " + gameMode, e);
		}
	}

	private String formatTimestamp(String timestamp) {
		try {
			// Input format: DDMMYYYYHHMMSS (e.g., 28112024160332)
			if (timestamp.length() == 14) {
				String day = timestamp.substring(0, 2);
				String month = timestamp.substring(2, 4);
				String year = timestamp.substring(4, 8);
				String hour = timestamp.substring(8, 10);
				String minute = timestamp.substring(10, 12);
				String second = timestamp.substring(12, 14);

				return String.format("%s/%s/%s %s:%s:%s",
						day, month, year, hour, minute, second);
			}
		} catch (Exception e) {
			LOGGER.error("Error formatting timestamp", e);
		}
		return timestamp; // Return original if parsing fails
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing IstiertaggerClient");

		// Initialize JDA
		try {
			jda = JDABuilder.createLight(DISCORD_TOKEN)
					.enableIntents(GatewayIntent.GUILD_MEMBERS)
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
