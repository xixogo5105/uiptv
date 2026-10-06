package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.application.PlaybackResolutionException;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.PlayerUrlUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.StringUtils.isBlank;

public class StalkerPortalPlayerService implements AccountPlayerService {
    private static final int CREATE_LINK_TIMEOUT_SECONDS = Integer.getInteger("uiptv.stalker.create_link.timeout.seconds", 3);
    private static final String FFMPEG_PREFIX = "ffmpeg ";
    private static final String STREAM_PARAM = "stream=";
    private static final String STREAM_PARAM_WITH_SEPARATOR = "stream=&";
    private static final com.uiptv.util.HttpUtil.RequestOptions CREATE_LINK_REQUEST_OPTIONS =
            new com.uiptv.util.HttpUtil.RequestOptions(true, true,
                    CREATE_LINK_TIMEOUT_SECONDS, CREATE_LINK_TIMEOUT_SECONDS, CREATE_LINK_TIMEOUT_SECONDS);
    private static final AtomicLong CREATE_LINK_SEQUENCE = new AtomicLong();

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "playback resolution start account=" + account.getAccountName() + " channel=" + channel.getName());
        long startNanos = System.nanoTime();
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Playback resolution was cancelled");
        }
        ensureStalkerSession(account);
        String resolvedSeries = resolveSeriesParam(account, channel, series);

        String rawUrl;
        if (shouldTryLiveCmdFallback(account, channel)) {
            rawUrl = fetchStalkerLiveUrlWithFallback(account, channel, resolvedSeries);
        } else {
            String originalCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
            rawUrl = fetchStalkerPortalUrl(account, resolvedSeries, originalCmd);
        }

        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class,
                "playback resolution complete elapsed=" + elapsedMs + "ms url=" + finalUrl);
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    private String resolveSeriesParam(Account account, Channel channel, String series) {
        if (account == null || account.getAction() != Account.AccountAction.series) {
            return "";
        }
        if (!isBlank(series)) {
            return series;
        }
        if (channel == null) {
            return "";
        }
        if (!isBlank(channel.getEpisodeNum())) {
            return channel.getEpisodeNum();
        }
        return isBlank(channel.getChannelId()) ? "" : channel.getChannelId();
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

        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "live create_link candidates: " + candidates.size());
        return resolveLiveCandidatesWithTokenRefresh(
                candidates,
                cmd -> resolveLiveCandidate(account, series, cmd),
                () -> HandshakeService.getInstance().hardTokenRefresh(account),
                fallbackCmd,
                channel.getCmd()
        );
    }

    private String resolveLiveCandidate(Account account, String series, String originalCmd) {
        String resolvedCmd = resolveCreateLink(account, series, originalCmd);
        if (isBlank(resolvedCmd)) {
            return null;
        }
        resolvedCmd = normalizeSeriesStreamPlaceholder(resolvedCmd, series);
        return mergeMissingQueryParams(resolvedCmd, originalCmd);
    }

    static String resolveLiveCandidatesWithTokenRefresh(
            List<String> candidates,
            Function<String, String> resolver,
            Runnable tokenRefresh,
            String fallbackCmd,
            String channelCmd
    ) {
        String resolved = resolveLiveCandidates(candidates, resolver);
        if (resolved != null) {
            return resolved;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new PlaybackResolutionCancelledException();
        }

        tokenRefresh.run();
        if (Thread.currentThread().isInterrupted()) {
            throw new PlaybackResolutionCancelledException();
        }

        resolved = resolveLiveCandidates(candidates, resolver);
        if (resolved != null) {
            return resolved;
        }
        com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "live create_link fallback to original cmd");
        return isBlank(fallbackCmd) ? channelCmd : fallbackCmd;
    }

    static String resolveLiveCandidates(
            List<String> candidates,
            Function<String, String> resolver
    ) {
        boolean candidateTimedOut = false;
        for (String cmd : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                throw new PlaybackResolutionCancelledException();
            }
            String resolved;
            try {
                resolved = resolver.apply(cmd);
            } catch (PlaybackResolutionTimeoutException e) {
                candidateTimedOut = true;
                com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "live create_link candidate timed out");
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new PlaybackResolutionCancelledException();
            }
            if (isUsableResolvedLiveUrl(resolved)) {
                com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "live create_link selected usable URL");
                return resolved;
            }
            String rescued = rescueResolvedLiveUrlWithCandidates(resolved, candidates);
            if (isUsableResolvedLiveUrl(rescued)) {
                com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "live create_link recovered URL by merging stream param from alternate cmd");
                return rescued;
            }
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new PlaybackResolutionCancelledException();
        }
        if (candidateTimedOut) {
            throw new PlaybackResolutionTimeoutException();
        }

        return null;
    }

    private String fetchStalkerPortalUrl(final Account account, final String series, final String originalCmd) {
        if (isBlank(originalCmd)) {
            return originalCmd;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new PlaybackResolutionCancelledException();
        }

        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "create_link start");
        String resolvedCmd = resolveCreateLink(account, series, originalCmd);
        if (isBlank(resolvedCmd)) {
            if (Thread.currentThread().isInterrupted()) {
                throw new PlaybackResolutionCancelledException();
            }
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link returned empty cmd. Refreshing token and retrying once.");
            HandshakeService.getInstance().hardTokenRefresh(account);
            if (Thread.currentThread().isInterrupted()) {
                throw new PlaybackResolutionCancelledException();
            }
            resolvedCmd = resolveCreateLink(account, series, originalCmd);
        }

        if (isBlank(resolvedCmd)) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link failed after retry. Using original channel cmd.");
            return originalCmd;
        }

        resolvedCmd = normalizeSeriesStreamPlaceholder(resolvedCmd, series);
        String mergedCmd = mergeMissingQueryParams(resolvedCmd, originalCmd);
        if (!mergedCmd.equals(resolvedCmd)) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link had missing query params. Merged missing values from original channel cmd.");
        }
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "create_link resolved URL: " + mergedCmd);
        return mergedCmd;
    }

    private String resolveCreateLink(Account account, String series, String cmd) {
        long seq = CREATE_LINK_SEQUENCE.incrementAndGet();
        long startNanos = System.nanoTime();
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "create_link start seq=" + seq);
        CompletableFuture<com.uiptv.util.HttpUtil.HttpResult> httpFuture = null;
        try {
            httpFuture = com.uiptv.util.HttpUtil.sendAsync(
                    resolveCreateLinkUrl(account, cmd, series),
                    com.uiptv.util.FetchAPI.headers(account, false),
                    "GET",
                    null,
                    CREATE_LINK_REQUEST_OPTIONS
            );
            return awaitCreateLinkResult(httpFuture, CREATE_LINK_TIMEOUT_SECONDS, seq, startNanos);
        } catch (PlaybackResolutionTimeoutException | PlaybackResolutionCancelledException e) {
            throw e;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link failed seq=" + seq + " elapsed=" + elapsedMs + "ms error=" + safe(e.getMessage()));
            return null;
        } finally {
            if (httpFuture != null && !httpFuture.isDone()) {
                httpFuture.cancel(true);
            }
        }
    }

    private String awaitCreateLinkResult(
            CompletableFuture<com.uiptv.util.HttpUtil.HttpResult> httpFuture,
            int timeoutSeconds,
            long seq,
            long startNanos
    ) throws IOException {
        CompletableFuture<String> resultFuture = httpFuture.thenApply(response -> parseUrl(response.body()));
        try {
            String result = resultFuture.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (isBlank(result)) {
                com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link empty seq=" + seq);
            } else {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService.class, "create_link resolved seq=" + seq + " elapsed=" + elapsedMs + "ms");
            }
            return isBlank(result) ? null : result;
        } catch (java.util.concurrent.TimeoutException ex) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link timeout seq=" + seq);
            resultFuture.cancel(true);
            httpFuture.cancel(true);
            throw new PlaybackResolutionTimeoutException();
        } catch (InterruptedException ex) {
            resultFuture.cancel(true);
            httpFuture.cancel(true);
            Thread.currentThread().interrupt();
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService.class, "create_link interrupted seq=" + seq);
            throw new PlaybackResolutionCancelledException();
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            } else if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(cause);
        } finally {
            if (!resultFuture.isDone()) {
                resultFuture.cancel(true);
            }
            if (!httpFuture.isDone()) {
                httpFuture.cancel(true);
            }
        }
    }

    private String resolveCreateLinkUrl(Account account, String cmd, String series) {
        Map<String, String> params = getParams(account, cmd, series);
        String baseUrl = com.uiptv.util.FetchAPI.resolveBaseUrl(account);
        String payload = com.uiptv.util.FetchAPI.mapToString(params);
        return baseUrl + "?" + payload;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class PlaybackResolutionTimeoutException extends PlaybackResolutionException {
        private static final long serialVersionUID = 1L;

        PlaybackResolutionTimeoutException() {
            super("Playback URL resolution timed out.");
        }
    }

    static final class PlaybackResolutionCancelledException extends PlaybackResolutionException {
        private static final long serialVersionUID = 1L;

        PlaybackResolutionCancelledException() {
            super("Playback URL resolution was cancelled.");
        }
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
        params.put("JsHttpRequest", System.currentTimeMillis() + "-xml");
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
        } catch (Exception _) {
            // Invalid create_link payloads should fall back to the original cmd/retry path.
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
            return resolvedCmd.replace("stream=.&", STREAM_PARAM + streamToken + "&");
        }
        if (resolvedCmd.endsWith("stream=.")) {
            return resolvedCmd.substring(0, resolvedCmd.length() - "stream=.".length()) + STREAM_PARAM + streamToken;
        }
        if (resolvedCmd.contains(STREAM_PARAM_WITH_SEPARATOR)) {
            return resolvedCmd.replace(STREAM_PARAM_WITH_SEPARATOR, STREAM_PARAM + streamToken + "&");
        }
        if (resolvedCmd.endsWith(STREAM_PARAM)) {
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
        return trimmed.replaceAll("\\D", "");
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
        } catch (Exception _) {
            // Ignore malformed relative URLs and keep the unresolved base as-is.
            return trimmed;
        }
    }

    private static String extractCmdPrefix(String cmd) {
        if (isBlank(cmd)) {
            return "";
        }
        String trimmed = cmd.trim();
        if (trimmed.startsWith(FFMPEG_PREFIX)) {
            return "ffmpeg";
        }
        return "";
    }

    private static String extractCmdUrl(String cmd) {
        if (isBlank(cmd)) {
            return "";
        }
        String trimmed = cmd.trim();
        if (trimmed.startsWith(FFMPEG_PREFIX)) {
            return trimmed.substring(FFMPEG_PREFIX.length()).trim();
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
        if (normalized.startsWith(FFMPEG_PREFIX)) {
            normalized = normalized.substring(FFMPEG_PREFIX.length()).trim();
        }
        return !normalized.contains(STREAM_PARAM_WITH_SEPARATOR);
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
