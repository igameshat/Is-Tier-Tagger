package com.example.tag;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.function.BiConsumer;

/**
 * Service class to handle all API requests to Israel Tiers and Mojang APIs
 */
public class IsrealTiersApiService {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;

    private final Logger logger;
    private final PlayerDataCache cache;

    // Tier points mapping
    private static final JsonObject TIER_POINTS = GSON.fromJson(
            "{ \"HT1\": 60, \"LT1\": 44, \"HT2\": 28, \"LT2\": 16, \"HT3\": 10, \"LT3\": 6, \"HT4\": 4, \"LT4\": 3, \"HT5\": 2, \"LT5\": 1 }",
            JsonObject.class
    );

    public IsrealTiersApiService(Logger logger) {
        this.logger = logger;
        this.cache = new PlayerDataCache(logger);

        // Initialize HTTP client with timeout from config
        ModConfig config = ModConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .build();
    }

    /**
     * Create an API request to the Israel Tiers API
     */
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

    /**
     * Fetch UUID from username using Mojang API
     */
    public String fetchUUID(String username) throws Exception {
        // Check cache first
        String cachedUuid = cache.getCachedUUID(username);
        if (cachedUuid != null) {
            logger.debug("Using cached UUID for {}: {}", username, cachedUuid);
            return cachedUuid;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject jsonObject = GSON.fromJson(response.body(), JsonObject.class);
            String uuid = jsonObject.get("id").getAsString();
            uuid = uuid.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");

            // Cache the result
            cache.cacheUUID(username, uuid);

            return uuid;
        }
        return null;
    }

    /**
     * Fetch username from UUID using Mojang API
     */
    public String fetchUsernameFromUUID(String uuid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/user/profile/" + uuid))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject profile = GSON.fromJson(response.body(), JsonObject.class);
            return profile.get("name").getAsString();
        }
        return uuid;
    }

    /**
     * Fetch player data from Israel Tiers API
     * @param uuid Player UUID
     * @param callback Callback with the fetched data and success status
     */
    public void fetchPlayerData(String uuid, BiConsumer<JsonObject, Boolean> callback) {
        // Check cache first
        JsonObject cachedData = cache.getCachedPlayerData(uuid);
        if (cachedData != null) {
            logger.debug("Using cached player data for {}", uuid);
            callback.accept(cachedData, true);
            return;
        }

        try {
            HttpRequest request = createApiRequest(uuid)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject data = GSON.fromJson(response.body(), JsonObject.class);

                // Cache the result
                cache.cachePlayerData(uuid, data);

                callback.accept(data, true);
            } else {
                callback.accept(null, false);
            }
        } catch (Exception e) {
            logger.error("Error fetching player data", e);
            callback.accept(null, false);
        }
    }

    /**
     * Fetch tier list from Israel Tiers API
     * @param filter Game mode filter
     * @param callback Callback with the fetched tiers and success status
     */
    public void fetchTierList(String filter, BiConsumer<JsonArray, Boolean> callback) {
        // Check cache first
        Object cachedTierList = cache.getCachedTierList(filter);
        if (cachedTierList != null) {
            logger.debug("Using cached tier list for filter {}", filter);
            callback.accept((JsonArray) cachedTierList, true);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.israeltiers.com/api/tiers?filter=" + filter))
                    .header("accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray tiers = GSON.fromJson(response.body(), JsonArray.class);

                // Cache the result
                cache.cacheTierList(filter, tiers);

                callback.accept(tiers, true);
            } else {
                callback.accept(null, false);
            }
        } catch (Exception e) {
            logger.error("Error fetching tier list", e);
            callback.accept(null, false);
        }
    }

    /**
     * Get points for a tier
     */
    public int getPointsForTier(String tier) {
        try {
            if (tier != null && !tier.isEmpty() && TIER_POINTS.has(tier)) {
                return TIER_POINTS.get(tier).getAsInt();
            }
        } catch (Exception e) {
            logger.error("Error getting points for tier: {}", tier, e);
        }
        return 0;
    }

    public void clearCaches() {
        cache.clearAllCaches();
    }

    public String getCacheStats() {
        return cache.getStatistics();
    }

    /**
     * Format Unix timestamp to human-readable date
     */
    public String formatUnixTimestamp(String timestamp) {
        try {
            long unixTime = Long.parseLong(timestamp);
            Date date = new Date(unixTime * 1000L); // Convert to milliseconds
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            return sdf.format(date);
        } catch (Exception e) {
            logger.error("Error formatting timestamp: {}", timestamp, e);
            return timestamp; // Return original if parsing fails
        }
    }
}