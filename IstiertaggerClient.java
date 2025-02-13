package com.example.tag;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class IstiertaggerClient implements ClientModInitializer {
	public static final String MOD_ID = "website_opener";
	private static final Logger LOGGER = LoggerFactory.getLogger("IstiertaggerClient");
	private static final Gson GSON = new Gson();

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("istagger")
					.then(argument("username", StringArgumentType.word())
							.executes(context -> {
								String username = StringArgumentType.getString(context, "username");
								try {
									// Fetch UUID in a separate thread
									new Thread(() -> {
										try {
											String uuid = fetchUUID(username);
											if (uuid != null) {
												String url = "https://israeltiers.com/api/user/" + uuid;
												LOGGER.info("Attempting to open URL: " + url);
												openUrl(url);
												context.getSource().sendFeedback(Text.literal("Opening profile for: " + username + " (UUID: " + uuid + ")"));
											} else {
												context.getSource().sendError(Text.literal("Could not find UUID for username: " + username));
											}
										} catch (Exception e) {
											String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
											LOGGER.error("Failed to process request: " + errorMessage, e);
											context.getSource().sendError(Text.literal("Failed to open website: " + errorMessage));
										}
									}, "URL-Opener").start();

									return 1;
								} catch (Exception e) {
									LOGGER.error("Command execution failed", e);
									context.getSource().sendError(Text.literal("Command failed: " + e.getMessage()));
									return 0;
								}
							})));
		});
	}

	private String fetchUUID(String username) throws Exception {
		String apiUrl = "https://api.mojang.com/users/profiles/minecraft/" + username;
		URL url = new URL(apiUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");

		try {
			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;

				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();

				JsonObject jsonObject = GSON.fromJson(response.toString(), JsonObject.class);
				String uuid = jsonObject.get("id").getAsString();
				// Format UUID with dashes if needed
				return uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
			} else if (responseCode == 204) {
				return null; // Player not found
			} else {
				throw new Exception("Failed to fetch UUID. Response code: " + responseCode);
			}
		} finally {
			connection.disconnect();
		}
	}

	private void openUrl(String urlString) throws Exception {
		if (urlString == null || urlString.trim().isEmpty()) {
			throw new IllegalArgumentException("URL cannot be empty");
		}

		String os = System.getProperty("os.name").toLowerCase();
		String[] command;

		if (os.contains("win")) {
			command = new String[]{"cmd", "/c", "start", urlString};
		} else if (os.contains("mac")) {
			command = new String[]{"open", urlString};
		} else {
			command = new String[]{"xdg-open", urlString};
		}

		try {
			LOGGER.info("Executing command to open URL: " + String.join(" ", command));
			Runtime.getRuntime().exec(command);
		} catch (Exception e) {
			LOGGER.error("Failed to open URL using Runtime: " + urlString, e);
			throw new Exception("Failed to open browser: " + e.getMessage());
		}
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing IstiertaggerClient");
		registerCommands();
	}
}