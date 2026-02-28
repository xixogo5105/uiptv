package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.ui.LogDisplayUI;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.PlayerUrlUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.isBlank;

public class StalkerPortalPlayerService implements AccountPlayerService {

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        LogDisplayUI.addLog("Resolving playback URL for Stalker Portal account: " + account.getAccountName());
        ensureStalkerSession(account);

        String rawUrl;
        if (shouldTryLiveCmdFallback(account, channel)) {
            rawUrl = fetchStalkerLiveUrlWithFallback(account, channel, series);
        } else {
            String originalCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
            rawUrl = fetchStalkerPortalUrl(account, series, originalCmd);
        }

        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        LogDisplayUI.addLog("Final resolved URL: " + finalUrl);
        LogDisplayUI.addLog("Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    private void ensureStalkerSession(Account account) {
        if (account == null || account.getType() != STALKER_PORTAL) {
            return;
        }
        if (isBlank(account.getServerPortalUrl())) {
            AccountService.getInstance().ensureServerPortalUrl(account);
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
    }

    private boolean shouldTryLiveCmdFallback(Account account, Channel channel) {
        return account != null
                && account.getType() == STALKER_PORTAL
                && account.getAction() == Account.AccountAction.itv
                && channel != null;
    }

    private String fetchStalkerLiveUrlWithFallback(Account account, Channel channel, String series) {
        List<String> candidates = getLiveCmdCandidates(channel);
        String fallbackCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
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

        resolvedCmd = normalizeSeriesStreamPlaceholder(resolvedCmd, series);
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

    private String normalizeSeriesStreamPlaceholder(String resolvedCmd, String seriesParam) {
        if (isBlank(resolvedCmd) || isBlank(seriesParam)) {
            return resolvedCmd;
        }
        String streamToken = extractStreamToken(seriesParam);
        if (isBlank(streamToken)) {
            return resolvedCmd;
        }
        if (resolvedCmd.contains("stream=.&")) {
            return resolvedCmd.replace("stream=.&", "stream=" + streamToken + "&");
        }
        if (resolvedCmd.endsWith("stream=.")) {
            return resolvedCmd.substring(0, resolvedCmd.length() - "stream=.".length()) + "stream=" + streamToken;
        }
        if (resolvedCmd.contains("stream=&")) {
            return resolvedCmd.replace("stream=&", "stream=" + streamToken + "&");
        }
        if (resolvedCmd.endsWith("stream=")) {
            return resolvedCmd + streamToken;
        }
        return resolvedCmd;
    }

    private String extractStreamToken(String seriesParam) {
        if (isBlank(seriesParam)) {
            return "";
        }
        String trimmed = seriesParam.trim();
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            trimmed = trimmed.substring(0, colonIndex);
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        return digits;
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
}
