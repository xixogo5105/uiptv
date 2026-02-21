package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.player.YoutubeDL;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.HttpUtil;
import com.uiptv.ui.LogDisplayUI;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
        return get(account, channel, "", false);
    }

    public PlayerResponse get(Account account, Channel channel, boolean resolveRedirectForEmbeddedPlayer) throws IOException {
        return get(account, channel, "", resolveRedirectForEmbeddedPlayer);
    }

    public PlayerResponse get(Account account, Channel channel, String series) throws IOException {
        return get(account, channel, series, false);
    }

    public PlayerResponse get(Account account, Channel channel, String series, boolean resolveRedirectForEmbeddedPlayer) throws IOException {
        boolean predefined = PRE_DEFINED_URLS.contains(account.getType());
        String rawUrl = predefined ? channel.getCmd() : fetchStalkerPortalUrl(account, series, channel.getCmd());
        String finalUrl = resolveAndProcessUrl(rawUrl);
        if (resolveRedirectForEmbeddedPlayer) {
            finalUrl = resolveRedirectIfNeeded(finalUrl);
        }
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
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

    private String resolveRedirectIfNeeded(String url) {
        if (isBlank(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return url;
        }
        String finalUrl = HttpUtil.resolveFinalUrl(url, null);
        if (!url.equals(finalUrl)) {
            LogDisplayUI.addLog("Resolved embedded playback redirect URL: " + finalUrl);
        }
        return finalUrl;
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
        Map<String, String> resolvedParams = parseQueryParams(resolvedUrl.substring(resolvedQueryIndex + 1));
        Map<String, String> originalParams = parseQueryParams(originalUrl.substring(originalQueryIndex + 1));

        originalParams.forEach((key, value) -> {
            String existing = resolvedParams.get(key);
            if ((existing == null || existing.isBlank()) && value != null && !value.isBlank()) {
                resolvedParams.put(key, value);
            }
        });

        String mergedUrl = resolvedBase + "?" + toQueryString(resolvedParams);
        String prefix = !isBlank(resolvedPrefix) ? resolvedPrefix : originalPrefix;
        return isBlank(prefix) ? mergedUrl : prefix + " " + mergedUrl;
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

        String processedUrl = url;
        String[] uriParts = url.split(" ");
        if (uriParts.length > 1) {
            processedUrl = uriParts[1]; // Original logic to extract the actual URL part
        }

        // Check if the link is a YouTube video URL
        if (processedUrl != null && (processedUrl.contains("youtube.com/watch?v=") || processedUrl.contains("youtu.be/"))) {
            String streamingUrl = YoutubeDL.getStreamingUrl(processedUrl);
            if (streamingUrl != null && !streamingUrl.isEmpty()) {
                return streamingUrl;
            }
        }
        return processedUrl;
    }

}
