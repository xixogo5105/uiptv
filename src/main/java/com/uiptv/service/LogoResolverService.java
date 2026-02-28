package com.uiptv.service;

import com.uiptv.util.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class LogoResolverService {
    private static final String CHANNELS_CATALOG_URL = "https://iptv-org.github.io/api/channels.json";
    private static final String LOGOS_CATALOG_URL = "https://raw.githubusercontent.com/iptv-org/database/master/data/logos.csv";
    private static final long CATALOG_REFRESH_MS = 24L * 60L * 60L * 1000L;
    private static final LogoResolverService INSTANCE = new LogoResolverService();
    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "uhd", "fhd", "hd", "sd", "hq", "4k", "8k",
            "hevc", "h264", "h265", "x264", "x265",
            "tv", "channel", "live", "official",
            "plus", "intl", "international"
    ));

    private final ConcurrentHashMap<String, String> localCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> catalog = new ConcurrentHashMap<>();
    private final File localCacheFile;
    private volatile long lastCatalogRefreshAt = 0L;
    private volatile boolean refreshInProgress = false;

    private LogoResolverService() {
        this.localCacheFile = new File(System.getProperty("java.io.tmpdir"), "uiptv-logo-cache.json");
        loadLocalCache();
    }

    public static LogoResolverService getInstance() {
        return INSTANCE;
    }

    public String resolve(String channelName, String providerLogo, String countryCode) {
        if (isNotBlank(providerLogo)) {
            return providerLogo;
        }
        if (isBlank(channelName)) {
            return "";
        }

        String key = makeLookupKey(channelName);
        if (isBlank(key)) {
            return "";
        }

        String cached = localCache.get(key);
        if (isNotBlank(cached)) {
            return cached;
        }

        triggerCatalogRefreshAsync();
        String resolved = catalog.get(key);
        if (isBlank(resolved)) {
            resolved = catalog.get(makeLookupKey(stripCommonSuffixes(channelName)));
        }
        if (isBlank(resolved)) {
            resolved = resolveByVariants(channelName);
        }
        if (isBlank(resolved)) {
            resolved = resolveByTokenSubset(channelName);
        }

        if (isNotBlank(resolved)) {
            localCache.put(key, resolved);
            persistLocalCache();
            return resolved;
        }
        return "";
    }

    private synchronized void ensureCatalogLoaded() {
        long now = System.currentTimeMillis();
        if (!catalog.isEmpty() && now - lastCatalogRefreshAt < CATALOG_REFRESH_MS) {
            return;
        }
        if (refreshInProgress) {
            return;
        }
        refreshInProgress = true;
        try {
            HttpUtil.HttpResult response = HttpUtil.sendRequest(CHANNELS_CATALOG_URL, null, "GET");
            if (response.statusCode() != HttpUtil.STATUS_OK) {
                return;
            }
            String json = response.body();
            JSONArray channels = new JSONArray(json);
            Map<String, String> logoByChannelId = loadLogosByChannelId();

            ConcurrentHashMap<String, String> fresh = new ConcurrentHashMap<>();
            for (int i = 0; i < channels.length(); i++) {
                JSONObject item = channels.getJSONObject(i);
                String channelId = item.optString("id", "");
                String logo = item.optString("logo", "");
                if (isBlank(logo) && isNotBlank(channelId)) {
                    logo = logoByChannelId.getOrDefault(channelId, "");
                }
                if (isBlank(logo)) {
                    continue;
                }

                String name = item.optString("name", "");
                addAlias(fresh, name, logo);

                JSONArray altNames = item.optJSONArray("alt_names");
                if (altNames != null) {
                    for (int j = 0; j < altNames.length(); j++) {
                        addAlias(fresh, altNames.optString(j, ""), logo);
                    }
                }
            }

            if (!fresh.isEmpty()) {
                catalog.clear();
                catalog.putAll(fresh);
                lastCatalogRefreshAt = now;
            }
        } catch (Exception ignored) {
            // keep using local/provider fallback only
        } finally {
            refreshInProgress = false;
        }
    }

    private Map<String, String> loadLogosByChannelId() {
        ConcurrentHashMap<String, String> logoById = new ConcurrentHashMap<>();
        try {
            HttpUtil.HttpResult response = HttpUtil.sendRequest(LOGOS_CATALOG_URL, null, "GET");
            if (response.statusCode() != HttpUtil.STATUS_OK) {
                return logoById;
            }
            String csv = response.body();
            String[] lines = csv.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (isBlank(line)) continue;
                List<String> cells = parseCsvLine(line);
                if (cells.size() < 7) continue;
                String channel = cells.get(0);
                String logoUrl = cells.get(6);
                if (isBlank(channel) || isBlank(logoUrl)) continue;
                logoById.putIfAbsent(channel, logoUrl);
            }
        } catch (Exception ignored) {
            // best-effort only
        }
        return logoById;
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private void triggerCatalogRefreshAsync() {
        long now = System.currentTimeMillis();
        if ((!catalog.isEmpty() && now - lastCatalogRefreshAt < CATALOG_REFRESH_MS) || refreshInProgress) {
            return;
        }
        Thread loader = new Thread(this::ensureCatalogLoaded, "uiptv-logo-catalog-refresh");
        loader.setDaemon(true);
        loader.start();
    }

    private void addAlias(Map<String, String> target, String alias, String logo) {
        String key = makeLookupKey(alias);
        if (isNotBlank(key) && isNotBlank(logo)) {
            target.putIfAbsent(key, logo);
        }
    }

    private String stripCommonSuffixes(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("(?i)\\b(\\+1|\\+2|\\+3|\\+4)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(uhd|fhd|hd|sd|hq|4k|8k|hevc|h264|h265|x264|x265|tv|channel|live|official|intl|international)\\b", " ");
        return cleaned.trim();
    }

    private String makeLookupKey(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.toLowerCase()
                .replace('&', ' ')
                .replace('+', ' ')
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String resolveByVariants(String originalName) {
        List<String> variants = buildNameVariants(originalName);
        for (String variant : variants) {
            String hit = catalog.get(variant);
            if (isNotBlank(hit)) {
                return hit;
            }
        }
        return "";
    }

    private List<String> buildNameVariants(String originalName) {
        String base = makeLookupKey(originalName);
        String stripped = makeLookupKey(stripCommonSuffixes(originalName));

        Set<String> variants = new HashSet<>();
        if (isNotBlank(base)) variants.add(base);
        if (isNotBlank(stripped)) variants.add(stripped);

        List<String> tokenList = new ArrayList<>();
        for (String token : stripped.split(" ")) {
            if (isBlank(token)) continue;
            if (NOISE_TOKENS.contains(token)) continue;
            tokenList.add(token);
        }
        if (!tokenList.isEmpty()) {
            variants.add(String.join(" ", tokenList));
            // progressively remove trailing token(s), useful for "set max hd", "sony max 2 hd"
            for (int i = tokenList.size() - 1; i >= 1; i--) {
                variants.add(String.join(" ", tokenList.subList(0, i)));
            }
        }

        return new ArrayList<>(variants);
    }

    private String resolveByTokenSubset(String originalName) {
        Set<String> tokens = new HashSet<>();
        for (String token : makeLookupKey(stripCommonSuffixes(originalName)).split(" ")) {
            if (isBlank(token) || NOISE_TOKENS.contains(token)) continue;
            tokens.add(token);
        }
        if (tokens.isEmpty()) {
            return "";
        }

        // Best effort fuzzy: choose first catalog name containing all significant tokens.
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            String catalogKey = entry.getKey();
            boolean allMatch = true;
            for (String token : tokens) {
                if (!catalogKey.contains(token)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch && isNotBlank(entry.getValue())) {
                return entry.getValue();
            }
        }
        return "";
    }

    private void loadLocalCache() {
        if (!localCacheFile.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(localCacheFile)) {
            JSONObject root = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (String key : root.keySet()) {
                String value = root.optString(key, "");
                if (isNotBlank(value)) {
                    localCache.put(key, value);
                }
            }
        } catch (Exception ignored) {
            // ignore corrupt cache
        }
    }

    private synchronized void persistLocalCache() {
        try (FileOutputStream out = new FileOutputStream(localCacheFile)) {
            out.write(new JSONObject(localCache).toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // best-effort cache only
        }
    }
}
