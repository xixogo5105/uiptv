package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.player.YoutubeDL;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.ui.LogDisplayUI;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.StringUtils.isBlank;

public class PlayerService {
    private static PlayerService instance;
    public static final EnumSet<AccountType> PRE_DEFINED_URLS = EnumSet.of(RSS_FEED, M3U8_URL, M3U8_LOCAL, XTREME_API);

    private PlayerService() {
    }

    public static synchronized PlayerService getInstance() {
        if (instance == null) {
            instance = new PlayerService();
        }
        return instance;
    }

    public PlayerResponse get(Account account, Channel channel) throws IOException {
        return get(account, channel, "");
    }

    public PlayerResponse get(Account account, Channel channel, String series) throws IOException {
        boolean predefined = PRE_DEFINED_URLS.contains(account.getType());
        String rawUrl;
        if (predefined) {
            rawUrl = resolveBestChannelCmd(account, channel);
        } else if (shouldTryLiveCmdFallback(account, channel)) {
            rawUrl = fetchStalkerLiveUrlWithFallback(account, channel, series);
        } else {
            String originalCmd = resolveBestChannelCmd(account, channel);
            rawUrl = fetchStalkerPortalUrl(account, series, originalCmd);
        }
        String finalUrl = normalizeStreamUrl(account, resolveAndProcessUrl(rawUrl));
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    public boolean isDrmProtected(Channel channel) {
        if (channel == null) {
            return false;
        }
        return !isBlank(channel.getDrmType())
                || !isBlank(channel.getDrmLicenseUrl())
                || !isBlank(channel.getClearKeysJson())
                || !isBlank(channel.getInputstreamaddon())
                || !isBlank(channel.getManifestType());
    }

    public String buildDrmBrowserPlaybackUrl(Account account, Channel channel, String categoryId, String mode) {
        JSONObject payload = new JSONObject();
        payload.put("mode", normalizeMode(mode, account));
        payload.put("accountId", account == null ? "" : safe(account.getDbId()));
        payload.put("categoryId", safe(categoryId));

        JSONObject channelJson = new JSONObject();
        channelJson.put("dbId", channel == null ? "" : safe(channel.getDbId()));
        channelJson.put("channelId", channel == null ? "" : safe(channel.getChannelId()));
        channelJson.put("name", channel == null ? "" : safe(channel.getName()));
        channelJson.put("logo", channel == null ? "" : safe(channel.getLogo()));
        channelJson.put("cmd", channel == null ? "" : safe(channel.getCmd()));
        channelJson.put("cmd_1", channel == null ? "" : safe(channel.getCmd_1()));
        channelJson.put("cmd_2", channel == null ? "" : safe(channel.getCmd_2()));
        channelJson.put("cmd_3", channel == null ? "" : safe(channel.getCmd_3()));
        channelJson.put("drmType", channel == null ? "" : safe(channel.getDrmType()));
        channelJson.put("drmLicenseUrl", channel == null ? "" : safe(channel.getDrmLicenseUrl()));
        channelJson.put("clearKeysJson", channel == null ? "" : safe(channel.getClearKeysJson()));
        channelJson.put("inputstreamaddon", channel == null ? "" : safe(channel.getInputstreamaddon()));
        channelJson.put("manifestType", channel == null ? "" : safe(channel.getManifestType()));
        payload.put("channel", channelJson);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return localServerOrigin() + "/drm.html?drmLaunch=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8) + "&v=20260223b";
    }

    private String fetchStalkerPortalUrl(final Account account, final String series, final String originalCmd) {
        if (isBlank(originalCmd)) {
            return originalCmd;
        }

        LogDisplayUI.addLog("create_link start");
        String resolvedCmd = resolveCreateLink(account, series, originalCmd);
        if (isBlank(resolvedCmd)) {
            LogDisplayUI.addLog("create_link returned empty cmd. Refreshing token and retrying once.");
            HandshakeService.getInstance().hardTokenRefresh(account);
            resolvedCmd = resolveCreateLink(account, series, originalCmd);
        }

        if (isBlank(resolvedCmd)) {
            LogDisplayUI.addLog("create_link failed after retry. Using original channel cmd.");
            return originalCmd;
        }

        String mergedCmd = mergeMissingQueryParams(resolvedCmd, originalCmd);
        if (!mergedCmd.equals(resolvedCmd)) {
            LogDisplayUI.addLog("create_link had missing query params. Merged missing values from original channel cmd.");
        }
        LogDisplayUI.addLog("create_link resolved URL: " + mergedCmd);
        return mergedCmd;
    }

    private String resolveCreateLink(Account account, String series, String cmd) {
        String json = FetchAPI.fetch(getParams(account, cmd, series), account);
        String resolved = parseUrl(json);
        if (isBlank(resolved)) {
            LogDisplayUI.addLog("create_link unresolved for provided cmd.");
        }
        return resolved;
    }

    private String parseUrl(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js");
            if (js != null) {
                String cmd = js.optString("cmd", null);
                if (!isBlank(cmd)) {
                    return cmd;
                }
                String url = js.optString("url", null);
                if (!isBlank(url)) {
                    return url;
                }
            }
            String cmd = root.optString("cmd", null);
            return isBlank(cmd) ? null : cmd;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Map<String, String> getParams(Account account, String urlPrefix, String series) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", Account.AccountAction.series.name().equalsIgnoreCase(account.getAction().name()) ? Account.AccountAction.vod.name() : account.getAction().name());
        params.put("action", "create_link");
        params.put("cmd", urlPrefix);
        params.put("series", Account.AccountAction.series.name().equalsIgnoreCase(account.getAction().name()) ? series : "");
        params.put("forced_storage", "undefined");
        params.put("disable_ad", "0");
        params.put("download", "0");
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    static String mergeMissingQueryParams(String resolvedCmd, String originalCmd) {
        if (isBlank(resolvedCmd) || isBlank(originalCmd)) {
            return resolvedCmd;
        }

        String resolvedPrefix = extractCmdPrefix(resolvedCmd);
        String originalPrefix = extractCmdPrefix(originalCmd);
        String resolvedUrl = extractCmdUrl(resolvedCmd);
        String originalUrl = extractCmdUrl(originalCmd);

        int resolvedQueryIndex = resolvedUrl.indexOf('?');
        int originalQueryIndex = originalUrl.indexOf('?');
        if (resolvedQueryIndex < 0 || originalQueryIndex < 0) {
            return resolvedCmd;
        }

        String resolvedBase = resolvedUrl.substring(0, resolvedQueryIndex);
        String originalBase = originalUrl.substring(0, originalQueryIndex);
        String normalizedResolvedBase = normalizeResolvedBase(resolvedBase, originalBase);
        Map<String, String> resolvedParams = parseQueryParams(resolvedUrl.substring(resolvedQueryIndex + 1));
        Map<String, String> originalParams = parseQueryParams(originalUrl.substring(originalQueryIndex + 1));

        originalParams.forEach((key, value) -> {
            String existing = resolvedParams.get(key);
            if ((existing == null || existing.isBlank()) && value != null && !value.isBlank()) {
                resolvedParams.put(key, value);
            }
        });

        String mergedUrl = normalizedResolvedBase + "?" + toQueryString(resolvedParams);
        String prefix = !isBlank(resolvedPrefix) ? resolvedPrefix : originalPrefix;
        return isBlank(prefix) ? mergedUrl : prefix + " " + mergedUrl;
    }

    private static String normalizeResolvedBase(String resolvedBase, String originalBase) {
        if (isBlank(resolvedBase)) {
            return resolvedBase;
        }
        String trimmed = resolvedBase.trim();
        if (trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || trimmed.startsWith("//")) {
            return trimmed;
        }
        if (isBlank(originalBase)) {
            return trimmed;
        }
        try {
            URI originalUri = URI.create(originalBase.trim());
            URI normalizedOriginal = originalUri;
            if (originalUri.getScheme() == null || originalUri.getHost() == null) {
                return trimmed;
            }
            if (!normalizedOriginal.getPath().endsWith("/")) {
                String path = normalizedOriginal.getPath();
                int idx = path.lastIndexOf('/');
                String dirPath = idx >= 0 ? path.substring(0, idx + 1) : "/";
                normalizedOriginal = new URI(normalizedOriginal.getScheme(), normalizedOriginal.getUserInfo(),
                        normalizedOriginal.getHost(), normalizedOriginal.getPort(), dirPath, null, null);
            }
            URI resolvedUri = normalizedOriginal.resolve(trimmed);
            return resolvedUri.toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private static String extractCmdPrefix(String cmd) {
        if (isBlank(cmd)) {
            return "";
        }
        String trimmed = cmd.trim();
        if (trimmed.startsWith("ffmpeg ")) {
            return "ffmpeg";
        }
        return "";
    }

    private static String extractCmdUrl(String cmd) {
        if (isBlank(cmd)) {
            return "";
        }
        String trimmed = cmd.trim();
        if (trimmed.startsWith("ffmpeg ")) {
            return trimmed.substring("ffmpeg ".length()).trim();
        }
        return trimmed;
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (isBlank(query)) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isBlank()) continue;
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /**
     * Processes the URL, extracting the actual stream URL if it's a YouTube link.
     * This method ensures that the final URL passed to the player is a direct stream URL
     * for YouTube videos, while leaving other URLs untouched.
     *
     * @param url The URL to process.
     * @return The resolved streaming URL for YouTube videos, or the original URL for others.
     */
    private static String resolveAndProcessUrl(String url) {
        if (isBlank(url)) return url;

        String processedUrl = extractPlayableUrl(url);

        // Check if the link is a YouTube video URL
        if (processedUrl != null && (processedUrl.contains("youtube.com/watch?v=") || processedUrl.contains("youtu.be/"))) {
            String streamingUrl = YoutubeDL.getStreamingUrl(processedUrl);
            if (streamingUrl != null && !streamingUrl.isEmpty()) {
                return streamingUrl;
            }
        }
        return processedUrl;
    }

    private static String extractPlayableUrl(String raw) {
        if (isBlank(raw)) {
            return raw;
        }
        String value = raw.trim();
        String lower = value.toLowerCase();

        if (lower.startsWith("ffmpeg ")) {
            return value.substring("ffmpeg ".length()).trim();
        }
        if (lower.startsWith("ffmpeg+")) {
            return value.substring("ffmpeg+".length()).trim();
        }
        if (lower.startsWith("ffmpeg%20")) {
            return value.substring("ffmpeg%20".length()).trim();
        }

        String[] uriParts = value.split(" ");
        if (uriParts.length > 1) {
            return uriParts[uriParts.length - 1];
        }

        return value;
    }

    private static String normalizeStreamUrl(Account account, String url) {
        if (isBlank(url)) {
            return url;
        }

        String value = url.trim();

        String scheme = "http";
        try {
            String portal = account == null ? null : account.getServerPortalUrl();
            if (!isBlank(portal)) {
                URI portalUri = URI.create(portal.trim());
                if (!isBlank(portalUri.getScheme())) {
                    scheme = portalUri.getScheme();
                }
            }
        } catch (Exception ignored) {
        }

        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            // Some Stalker providers return https links that are actually served over http.
            // Align transport with the portal scheme for known playback paths.
            if (account != null && account.getType() == STALKER_PORTAL
                    && "http".equalsIgnoreCase(scheme)
                    && value.toLowerCase().startsWith("https://")
                    && (value.toLowerCase().contains("/live/play/") || value.toLowerCase().contains("/play/movie.php"))) {
                return "http://" + value.substring("https://".length());
            }
            return value;
        }

        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }

        if (value.startsWith("/")) {
            try {
                String portal = account == null ? null : account.getServerPortalUrl();
                if (!isBlank(portal)) {
                    URI portalUri = URI.create(portal.trim());
                    String host = portalUri.getHost();
                    int port = portalUri.getPort();
                    if (!isBlank(host)) {
                        return scheme + "://" + host + (port > 0 ? ":" + port : "") + value;
                    }
                }
            } catch (Exception ignored) {
            }
            return value;
        }

        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return scheme + "://" + value;
        }

        return value;
    }

    private static String localServerOrigin() {
        String port = ConfigurationService.getInstance().read().getServerPort();
        if (isBlank(port)) {
            port = "8888";
        }
        return "http://127.0.0.1:" + port.trim();
    }

    private static String normalizeMode(String mode, Account account) {
        String m = safe(mode).toLowerCase();
        if ("itv".equals(m) || "vod".equals(m) || "series".equals(m)) {
            return m;
        }
        if (account != null && account.getAction() != null) {
            String derived = safe(account.getAction().name()).toLowerCase();
            if ("itv".equals(derived) || "vod".equals(derived) || "series".equals(derived)) {
                return derived;
            }
        }
        return "itv";
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String resolveBestChannelCmd(Account account, Channel channel) {
        if (channel == null) return "";
        String primary = channel.getCmd();
        if (account == null) return primary;

        // For Stalker live channels, some portals expose multiple cmd variants and
        // the first one can contain an empty stream id (stream=), causing 405.
        if (account.getType() == STALKER_PORTAL && account.getAction() == Account.AccountAction.itv) {
            String[] candidates = new String[]{channel.getCmd(), channel.getCmd_1(), channel.getCmd_2(), channel.getCmd_3()};
            for (String c : candidates) {
                if (isUsableLiveCmd(c)) return c;
            }
            return primary;
        }
        return primary;
    }

    private static boolean shouldTryLiveCmdFallback(Account account, Channel channel) {
        return account != null
                && account.getType() == STALKER_PORTAL
                && account.getAction() == Account.AccountAction.itv
                && channel != null;
    }

    private String fetchStalkerLiveUrlWithFallback(Account account, Channel channel, String series) {
        List<String> candidates = getLiveCmdCandidates(channel);
        String fallbackCmd = resolveBestChannelCmd(account, channel);
        if (candidates.isEmpty() && !isBlank(fallbackCmd)) {
            candidates.add(fallbackCmd);
        }

        LogDisplayUI.addLog("live create_link candidates: " + candidates.size());
        for (String cmd : candidates) {
            String resolved = fetchStalkerPortalUrl(account, series, cmd);
            if (isUsableResolvedLiveUrl(resolved)) {
                LogDisplayUI.addLog("live create_link selected usable URL");
                return resolved;
            }
            String rescued = rescueResolvedLiveUrlWithCandidates(resolved, candidates);
            if (isUsableResolvedLiveUrl(rescued)) {
                LogDisplayUI.addLog("live create_link recovered URL by merging stream param from alternate cmd");
                return rescued;
            }
        }

        LogDisplayUI.addLog("live create_link fallback to original cmd");
        return isBlank(fallbackCmd) ? channel.getCmd() : fallbackCmd;
    }

    private static List<String> getLiveCmdCandidates(Channel channel) {
        List<String> candidates = new ArrayList<>();
        String[] values = new String[]{channel.getCmd(), channel.getCmd_1(), channel.getCmd_2(), channel.getCmd_3()};
        for (String value : values) {
            if (!isBlank(value) && !candidates.contains(value)) {
                candidates.add(value);
            }
        }
        return candidates;
    }

    private static boolean isUsableResolvedLiveUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalized = url.trim().toLowerCase();
        if (normalized.startsWith("ffmpeg ")) {
            normalized = normalized.substring("ffmpeg ".length()).trim();
        }
        return !normalized.contains("stream=&");
    }

    private static String rescueResolvedLiveUrlWithCandidates(String resolvedUrl, List<String> candidates) {
        if (isBlank(resolvedUrl) || candidates == null || candidates.isEmpty()) {
            return resolvedUrl;
        }
        String fixed = resolvedUrl;
        for (String candidate : candidates) {
            fixed = mergeMissingQueryParams(fixed, candidate);
            if (isUsableResolvedLiveUrl(fixed)) {
                return fixed;
            }
        }
        return fixed;
    }

    private static boolean isUsableLiveCmd(String cmd) {
        if (isBlank(cmd)) return false;
        String normalized = cmd.trim().toLowerCase();
        if (normalized.startsWith("ffmpeg ")) {
            normalized = normalized.substring("ffmpeg ".length()).trim();
        }
        // Reject known broken pattern with empty stream parameter.
        if (normalized.contains("stream=&")) return false;
        return true;
    }

}
