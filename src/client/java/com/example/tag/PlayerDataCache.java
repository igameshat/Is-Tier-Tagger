package com.example.tag;

import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caching system for player data to reduce API calls
 */
public class PlayerDataCache {
    private final Logger logger;
    private final Map<String, CacheEntry> uuidCache = new HashMap<>(); // username -> uuid cache
    private final Map<String, CacheEntry> playerDataCache = new HashMap<>(); // uuid -> player data cache
    private final Map<String, CacheEntry> tierListCache = new HashMap<>(); // filter -> tier list cache

    // Default cache durations
    private final long uuidCacheDurationMs;
    private final long playerDataCacheDurationMs;
    private final long tierListCacheDurationMs;

    /**
     * Cache entry class to store data with expiration
     */
    private static class CacheEntry {
        private final Object data;
        private final long expirationTime;

        public CacheEntry(Object data, long expirationTimeMs) {
            this.data = data;
            this.expirationTime = System.currentTimeMillis() + expirationTimeMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        public Object getData() {
            return data;
        }

        public long getTimeToExpirationMs() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }
    }

    public PlayerDataCache(Logger logger) {
        this.logger = logger;

        // Get cache durations from config
        ModConfig config = ModConfig.getInstance();
        this.uuidCacheDurationMs = TimeUnit.MINUTES.toMillis(60); // 1 hour (UUIDs rarely change)
        this.playerDataCacheDurationMs = TimeUnit.MINUTES.toMillis(config.getCacheDurationMinutes());
        this.tierListCacheDurationMs = TimeUnit.MINUTES.toMillis(config.getTierListCacheDurationMinutes());

        logger.info("Initialized player data cache with durations: UUID={}ms, PlayerData={}ms, TierList={}ms",
                uuidCacheDurationMs, playerDataCacheDurationMs, tierListCacheDurationMs);
    }

    /**
     * Get UUID from cache
     * @param username Player username
     * @return UUID if in cache and not expired, null otherwise
     */
    public String getCachedUUID(String username) {
        CacheEntry entry = uuidCache.get(username.toLowerCase());
        if (entry != null && !entry.isExpired()) {
            logger.debug("Cache hit for UUID of {}", username);
            return (String) entry.getData();
        }

        // Remove expired entry if exists
        if (entry != null) {
            logger.debug("Removing expired UUID cache for {}", username);
            uuidCache.remove(username.toLowerCase());
        }

        return null;
    }

    /**
     * Cache a UUID
     * @param username Player username
     * @param uuid Player UUID
     */
    public void cacheUUID(String username, String uuid) {
        logger.debug("Caching UUID for {} -> {}", username, uuid);
        uuidCache.put(username.toLowerCase(), new CacheEntry(uuid, uuidCacheDurationMs));
    }

    /**
     * Get player data from cache
     * @param uuid Player UUID
     * @return JsonObject if in cache and not expired, null otherwise
     */
    public JsonObject getCachedPlayerData(String uuid) {
        CacheEntry entry = playerDataCache.get(uuid);
        if (entry != null && !entry.isExpired()) {
            logger.debug("Cache hit for player data of {}", uuid);
            return (JsonObject) entry.getData();
        }

        // Remove expired entry if exists
        if (entry != null) {
            logger.debug("Removing expired player data cache for {}", uuid);
            playerDataCache.remove(uuid);
        }

        return null;
    }

    /**
     * Cache player data
     * @param uuid Player UUID
     * @param data Player data
     */
    public void cachePlayerData(String uuid, JsonObject data) {
        logger.debug("Caching player data for {}", uuid);
        playerDataCache.put(uuid, new CacheEntry(data, playerDataCacheDurationMs));
    }

    /**
     * Get tier list from cache
     * @param filter Game mode filter
     * @return JsonArray if in cache and not expired, null otherwise
     */
    public Object getCachedTierList(String filter) {
        CacheEntry entry = tierListCache.get(filter);
        if (entry != null && !entry.isExpired()) {
            logger.debug("Cache hit for tier list with filter {}", filter);
            return entry.getData();
        }

        // Remove expired entry if exists
        if (entry != null) {
            logger.debug("Removing expired tier list cache for {}", filter);
            tierListCache.remove(filter);
        }

        return null;
    }

    /**
     * Cache tier list
     * @param filter Game mode filter
     * @param tierList Tier list data
     */
    public void cacheTierList(String filter, Object tierList) {
        logger.debug("Caching tier list for filter {}", filter);
        tierListCache.put(filter, new CacheEntry(tierList, tierListCacheDurationMs));
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        logger.info("Clearing all caches");
        uuidCache.clear();
        playerDataCache.clear();
        tierListCache.clear();
    }

    /**
     * Get cache statistics
     * @return Cache statistics in human-readable format
     */
    public String getStatistics() {
        int activeUuidEntries = (int) uuidCache.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .count();

        int activePlayerDataEntries = (int) playerDataCache.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .count();

        int activeTierListEntries = (int) tierListCache.entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .count();

        return String.format("Cache Stats: UUID=%d, PlayerData=%d, TierList=%d",
                activeUuidEntries, activePlayerDataEntries, activeTierListEntries);
    }
}