package com.uiptv.service;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

public class ConfigurationService {
    public static final int DEFAULT_CACHE_EXPIRY_DAYS = 30;
    public static final int DEFAULT_UI_ZOOM_PERCENT = 100;
    public static final String DEFAULT_VLC_CACHING_MS = "1000";
    public static final List<Integer> FIREFOX_ZOOM_PERCENT_OPTIONS =
            List.of(50, 75, 80, 90, 95, 100, 105, 110, 115, 120, 125, 133, 140, 150, 170, 200, 250, 300);
    public static final List<String> VLC_CACHING_OPTIONS_MS =
            List.of("", "1000", "2000", "3000", "4000", "5000", "10000", "15000", "20000", "25000", "30000", "60000");
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private final AtomicLong changeRevision = new AtomicLong(1);
    private final Set<ConfigurationChangeListener> changeListeners = new CopyOnWriteArraySet<>();

    private ConfigurationService() {
    }

    private static class SingletonHelper {
        private static final ConfigurationService INSTANCE = new ConfigurationService();
    }

    public static ConfigurationService getInstance() {
        return SingletonHelper.INSTANCE;
    }
    public void clearCache(Account account) {
        ConfigurationDb.get().clearCache(account);
    }
    public void clearAllCache() {
        ConfigurationDb.get().clearAllCache();
    }

    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
        notifyConfigurationChanged();
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }

    public void addChangeListener(ConfigurationChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(ConfigurationChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    public void notifyConfigurationChanged() {
        long revision = changeRevision.incrementAndGet();
        for (ConfigurationChangeListener listener : changeListeners) {
            try {
                listener.onConfigurationChanged(revision);
            } catch (Exception _) {
                // Listener failures must never break configuration updates.
            }
        }
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
        } catch (Exception _) {
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
        } catch (Exception _) {
            return DEFAULT_CACHE_EXPIRY_DAYS;
        }
    }

    public String normalizeVlcCachingMs(String rawCachingMs) {
        if (rawCachingMs == null) {
            return DEFAULT_VLC_CACHING_MS;
        }
        String normalized = rawCachingMs.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        return VLC_CACHING_OPTIONS_MS.contains(normalized) ? normalized : DEFAULT_VLC_CACHING_MS;
    }

    public boolean isVlcHttpUserAgentEnabled() {
        Configuration configuration = read();
        return configuration == null || configuration.isEnableVlcHttpUserAgent();
    }

    public boolean isResolveChainAndDeepRedirectsEnabled() {
        try {
            Configuration configuration = read();
            return configuration != null && configuration.isResolveChainAndDeepRedirects();
        } catch (RuntimeException _) {
            return false;
        }
    }

    public boolean isResolveChainAndDeepRedirectsEnabled(Account account) {
        if (isResolveChainAndDeepRedirectsEnabled()) {
            return true;
        }
        return account != null && account.isResolveChainAndDeepRedirects();
    }

}
