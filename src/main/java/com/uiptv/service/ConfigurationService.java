package com.uiptv.service;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;

import java.util.List;

public class ConfigurationService {
    public static final int DEFAULT_CACHE_EXPIRY_DAYS = 30;
    public static final int DEFAULT_UI_ZOOM_PERCENT = 100;
    public static final List<Integer> FIREFOX_ZOOM_PERCENT_OPTIONS =
            List.of(50, 75, 80, 90, 100, 110, 115, 120, 125, 133, 140, 150, 170, 200, 250, 300);
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private static ConfigurationService instance;

    private ConfigurationService() {
    }

    public static synchronized ConfigurationService getInstance() {
        if (instance == null) {
            instance = new ConfigurationService();
        }
        return instance;
    }
    public void clearCache(Account account) {
        ConfigurationDb.get().clearCache(account);
    }
    public void clearAllCache() {
        ConfigurationDb.get().clearAllCache();
    }

    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }

    public int getCacheExpiryDays() {
        Configuration configuration = read();
        return normalizeCacheExpiryDays(configuration != null ? configuration.getCacheExpiryDays() : null);
    }

    public long getCacheExpiryMs() {
        return getCacheExpiryDays() * MILLIS_PER_DAY;
    }

    public int getUiZoomPercent() {
        Configuration configuration = read();
        return normalizeUiZoomPercent(configuration != null ? configuration.getUiZoomPercent() : null);
    }

    public int normalizeUiZoomPercent(String rawZoomPercent) {
        if (rawZoomPercent == null || rawZoomPercent.trim().isEmpty()) {
            return DEFAULT_UI_ZOOM_PERCENT;
        }
        try {
            int parsed = Integer.parseInt(rawZoomPercent.trim());
            return FIREFOX_ZOOM_PERCENT_OPTIONS.contains(parsed) ? parsed : DEFAULT_UI_ZOOM_PERCENT;
        } catch (Exception ignored) {
            return DEFAULT_UI_ZOOM_PERCENT;
        }
    }

    public int normalizeCacheExpiryDays(String rawDays) {
        if (rawDays == null || rawDays.trim().isEmpty()) {
            return DEFAULT_CACHE_EXPIRY_DAYS;
        }
        try {
            int parsed = Integer.parseInt(rawDays.trim());
            return parsed > 0 ? parsed : DEFAULT_CACHE_EXPIRY_DAYS;
        } catch (Exception ignored) {
            return DEFAULT_CACHE_EXPIRY_DAYS;
        }
    }
}
