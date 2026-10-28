package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.*;
import com.uiptv.util.AppLog;
import com.uiptv.util.ServerUrlUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.uiptv.util.ServerUtils.*;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

@SuppressWarnings("java:S1075")
public class HttpPlayerJsonServer implements HttpHandler {
    public static final String SEASON = "season";
    public static final String EPISODE_NUM = "episodeNum";
    public static final String MANIFEST_TYPE = "manifestType";
    public static final String INPUTSTREAMADDON = "inputstreamaddon";
    public static final String CLEAR_KEYS_JSON = "clearKeysJson";
    public static final String DRM_LICENSE_URL = "drmLicenseUrl";
    private static final String PATH_PLAYER_BINGEWATCH = "/player/bingewatch";
    private static final String PATH_PLAYER_LIVE = "/player/live";
    private static final String PATH_PLAYER_SERIES = "/player/series";
    private static final String PATH_PLAYER_VOD = "/player/vod";
    private static final String JSON_KEY_STRATEGY_HINT = "strategyHint";
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String MODE_SERIES = "series";
    private static final String MODE_VOD = "vod";
    private static final String MODE_ITV = "itv";
    private static final String URL_FRAGMENT_DASH_MPD = ".mpd";
    private static final String URL_FRAGMENT_PROXY_STREAM = "/proxy-stream?src=";
    private static final String PATH_LIVE_PLAY = "/live/play/";
    private static final String PATH_PLAY_MOVIE = "/play/movie.php";
    private static final String STRATEGY_HINT_SHAKA = "SHAKA";
    private static final String STRATEGY_HINT_NATIVE_PROXY = "NATIVE_PROXY";
    private static final String STRATEGY_HINT_NATIVE = "NATIVE";
    private final PlayerRequestResolver playerRequestResolver = new PlayerRequestResolver();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String mode = resolveRequestedMode(ex, getParam(ex, "mode"));
            ResolvedWebPlayback resolved = resolvePlayback(ex, mode);
            PlaybackMediaInspector.MediaInfo mediaInfo = shouldInspectMedia(resolved.response(), mode, getParam(ex, "url"))
                    ? PlaybackMediaInspector.inspect(resolved.response().getUrl(), resolved.response().getAccount()) : null;
            applyWebPlaybackProcessing(resolved.response(), mode);
            generateJsonResponse(ex, buildJsonResponse(resolved, mediaInfo));
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                return;
            }
            AppLog.addErrorLog(HttpPlayerJsonServer.class, "HttpPlayerJsonServer failed: " + e);
            try {
                generateResponseText(ex, 500, "player-error");
            } catch (IOException ioException) {
                if (!isClientDisconnect(ioException)) {
                    throw ioException;
                }
            }
        }
    }

    private ResolvedWebPlayback resolvePlayback(HttpExchange ex, String mode) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        String accountId = getParam(ex, "accountId");
        String bingeWatchToken = getParam(ex, "bingeWatchToken");
        String bingeWatchEpisodeId = getParam(ex, "episodeId");
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String directUrl = getParam(ex, "url");
        String seriesParentId = getParam(ex, "seriesParentId");
        if (isNotBlank(bingeWatchToken)) {
            return resolveBingeWatchPlayback(ex, accountId, bingeWatchToken, bingeWatchEpisodeId);
        }
        if (isNotBlank(directUrl)) {
            return resolveDirectUrlPlayback(ex, accountId, channelId, directUrl);
        }
        if (isNotBlank(bookmarkId)) {
            return new ResolvedWebPlayback(resolveBookmarkPlayback(bookmarkId, mode, seriesParentId), "", "", List.of());
        }
        return new ResolvedWebPlayback(resolveDirectPlayback(ex, accountId, categoryId, channelId, mode, seriesParentId), "", "", List.of());
    }

    private String resolveRequestedMode(HttpExchange ex, String requestedMode) {
        if (isNotBlank(requestedMode)) {
            return requestedMode;
        }
        String path = ex == null || ex.getRequestURI() == null ? "" : safe(ex.getRequestURI().getPath()).toLowerCase();
        if (PATH_PLAYER_BINGEWATCH.equals(path) || PATH_PLAYER_SERIES.equals(path)) {
            return MODE_SERIES;
        }
        if (PATH_PLAYER_VOD.equals(path)) {
            return MODE_VOD;
        }
        if (PATH_PLAYER_LIVE.equals(path)) {
            return MODE_ITV;
        }
        return "";
    }

    private ResolvedWebPlayback resolveDirectUrlPlayback(HttpExchange ex, String accountId, String channelId, String directUrl) {
        PlayerResponse response = new PlayerResponse(directUrl);
        Account account = isBlank(accountId) ? null : AccountService.getInstance().getById(accountId);
        response.setAccount(account);
        Channel channel = buildRequestChannel(channelId, ex);
        if (hasChannelMetadata(channel)) {
            response.setFromChannel(channel, account);
        }
        return new ResolvedWebPlayback(response, "", "", List.of());
    }

    private ResolvedWebPlayback resolveBingeWatchPlayback(HttpExchange ex,
                                                          String accountId,
                                                          String token,
                                                          String episodeId) throws IOException {
        List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(token);
        if (items.isEmpty()) {
            return new ResolvedWebPlayback(new PlayerResponse(""), token, "", List.of());
        }
        String currentEpisodeId = isNotBlank(episodeId) ? episodeId : items.get(0).episodeId();
        BingeWatchService.ResolvedEpisode resolvedEpisode = BingeWatchService.getInstance().resolveEpisode(token, currentEpisodeId);
        if (resolvedEpisode == null || isBlank(resolvedEpisode.url())) {
            return new ResolvedWebPlayback(new PlayerResponse(""), token, currentEpisodeId, items);
        }

        PlayerResponse response = new PlayerResponse(resolvedEpisode.url());
        Account account = isBlank(accountId) ? null : AccountService.getInstance().getById(accountId);
        Channel channel = buildRequestChannel(currentEpisodeId, ex);
        if (isBlank(channel.getName())) {
            channel.setName(resolvedEpisode.episodeName());
        }
        for (BingeWatchService.PlaylistItem item : items) {
            if (currentEpisodeId.equals(item.episodeId())) {
                if (isBlank(channel.getSeason())) {
                    channel.setSeason(item.season());
                }
                if (isBlank(channel.getEpisodeNum())) {
                    channel.setEpisodeNum(item.episodeNumber());
                }
                break;
            }
        }
        response.setFromChannel(channel, account);
        return new ResolvedWebPlayback(response, token, currentEpisodeId, items);
    }

    private void applyWebPlaybackProcessing(PlayerResponse response, String mode) {
        if (response == null || isBlank(response.getUrl())) {
            return;
        }
        String sourceUrl = normalizeWebPlaybackUrl(mode, response.getUrl());
        if (ConfigurationService.getInstance().isWebPlaybackProxyEnabled(response.getAccount())) {
            if (!isLocalProxyUrl(sourceUrl)) {
                sourceUrl = buildLocalProxyUrl(sourceUrl);
            }
        } else if (isLocalProxyUrl(sourceUrl)) {
            sourceUrl = unwrapLocalProxyUrl(sourceUrl);
        }
        response.setUrl(sourceUrl);
    }

    private PlayerResponse resolveBookmarkPlayback(String bookmarkId, String mode, String seriesParentId) throws IOException {
        return playerRequestResolver.resolveBookmarkPlayback(bookmarkId, mode, seriesParentId);
    }

    private PlayerResponse resolveDirectPlayback(HttpExchange ex, String accountId, String categoryId, String channelId,
                                                 String mode, String seriesParentId) throws IOException {
        Account account = AccountService.getInstance().getById(accountId);
        String seriesId = getParam(ex, "seriesId");
        Channel requestChannel = buildRequestChannel(channelId, ex);
        return playerRequestResolver.resolveDirectPlayback(account, categoryId, channelId, mode, seriesParentId, seriesId, requestChannel);
    }

    private Channel buildRequestChannel(String channelId, HttpExchange ex) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(sanitizeParam(getParam(ex, "name")));
        channel.setLogo(sanitizeParam(getParam(ex, "logo")));
        channel.setCmd(sanitizeParam(getParam(ex, "cmd")));
        channel.setCmd_1(sanitizeParam(getParam(ex, "cmd_1")));
        channel.setCmd_2(sanitizeParam(getParam(ex, "cmd_2")));
        channel.setCmd_3(sanitizeParam(getParam(ex, "cmd_3")));
        channel.setDrmType(sanitizeParam(getParam(ex, "drmType")));
        channel.setDrmLicenseUrl(sanitizeParam(getParam(ex, DRM_LICENSE_URL)));
        channel.setClearKeysJson(sanitizeParam(getParam(ex, CLEAR_KEYS_JSON)));
        channel.setInputstreamaddon(sanitizeParam(getParam(ex, INPUTSTREAMADDON)));
        channel.setManifestType(sanitizeParam(getParam(ex, MANIFEST_TYPE)));
        channel.setSeason(sanitizeParam(getParam(ex, SEASON)));
        channel.setEpisodeNum(sanitizeParam(getParam(ex, EPISODE_NUM)));
        return channel;
    }

    private String buildJsonResponse(ResolvedWebPlayback resolved, PlaybackMediaInspector.MediaInfo mediaInfo) {
        PlayerResponse response = resolved.response();
        JSONObject json = new JSONObject();
        json.put("url", response == null ? "" : response.getUrl());
        putIfNotBlank(json, JSON_KEY_STRATEGY_HINT, determineStrategyHint(response));
        appendChannelJson(json, response);
        appendDrmJson(json, response);
        if (mediaInfo != null) json.put("mediaInfo", mediaInfo.toJson());
        appendBingeWatchJson(json, resolved);
        return json.toString();
    }

    private boolean shouldInspectMedia(PlayerResponse response, String mode, String directUrl) {
        if (response == null || isBlank(response.getUrl())) return false;
        if (isNotBlank(directUrl)) return false;
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) return false;
        return !hasDrmMetadata(response);
    }

    private void appendChannelJson(JSONObject json, PlayerResponse response) {
        Channel channel = response == null ? null : response.getChannel();
        if (channel == null || !hasChannelMetadata(channel)) {
            return;
        }
        JSONObject channelJson = new JSONObject();
        channelJson.put("channelId", safe(channel.getChannelId()));
        channelJson.put("name", safe(channel.getName()));
        channelJson.put("logo", safe(channel.getLogo()));
        channelJson.put(SEASON, safe(channel.getSeason()));
        channelJson.put(EPISODE_NUM, safe(channel.getEpisodeNum()));
        json.put("channel", channelJson);
        if (isNotBlank(channel.getName())) {
            json.put("title", channel.getName());
        }
    }

    private void appendDrmJson(JSONObject json, PlayerResponse response) {
        if (!hasDrmMetadata(response)) {
            return;
        }
        JSONObject drm = new JSONObject();
        putIfNotBlank(drm, "type", response.getDrmType());
        putIfNotBlank(drm, "licenseUrl", response.getDrmLicenseUrl());
        JSONObject clearKeys = parseClearKeysJson(response.getClearKeysJson());
        if (clearKeys != null && !clearKeys.isEmpty()) {
            drm.put("clearKeys", clearKeys);
        }
        putIfNotBlank(drm, INPUTSTREAMADDON, response.getInputstreamaddon());
        putIfNotBlank(drm, MANIFEST_TYPE, response.getManifestType());
        json.put("drm", drm);
    }

    private void appendBingeWatchJson(JSONObject json, ResolvedWebPlayback resolved) {
        if (isBlank(resolved.bingeWatchToken()) || resolved.playlistItems().isEmpty()) {
            return;
        }
        JSONObject binge = new JSONObject();
        binge.put("token", resolved.bingeWatchToken());
        binge.put("currentEpisodeId", safe(resolved.currentEpisodeId()));
        binge.put("items", buildBingeWatchItems(resolved.playlistItems()));
        json.put("bingeWatch", binge);
    }

    private JSONArray buildBingeWatchItems(List<BingeWatchService.PlaylistItem> items) {
        JSONArray jsonItems = new JSONArray();
        for (BingeWatchService.PlaylistItem item : items) {
            JSONObject itemJson = new JSONObject();
            itemJson.put("episodeId", safe(item.episodeId()));
            itemJson.put("episodeName", safe(item.episodeName()));
            itemJson.put(SEASON, safe(item.season()));
            itemJson.put("episodeNumber", safe(item.episodeNumber()));
            jsonItems.put(itemJson);
        }
        return jsonItems;
    }

    private boolean hasDrmMetadata(PlayerResponse response) {
        return response != null
                && (isNotBlank(response.getDrmType())
                || isNotBlank(response.getInputstreamaddon())
                || isNotBlank(response.getManifestType()));
    }

    private void putIfNotBlank(JSONObject json, String key, String value) {
        if (isNotBlank(value)) {
            json.put(key, value);
        }
    }

    private JSONObject parseClearKeysJson(String clearKeysJson) {
        if (isBlank(clearKeysJson)) {
            return null;
        }
        try {
            return new JSONObject(clearKeysJson);
        } catch (Exception _) {
            return null;
        }
    }

    private String determineStrategyHint(PlayerResponse response) {
        if (response == null || isBlank(response.getUrl())) {
            return STRATEGY_HINT_NATIVE;
        }
        String lowerUrl = response.getUrl().toLowerCase();
        if (hasDrmMetadata(response) || isDashUrl(lowerUrl)) {
            return STRATEGY_HINT_SHAKA;
        }
        if (isLocalProxyUrl(lowerUrl)) {
            return STRATEGY_HINT_NATIVE_PROXY;
        }
        return STRATEGY_HINT_NATIVE;
    }

    private boolean isDashUrl(String url) {
        return url.contains(URL_FRAGMENT_DASH_MPD);
    }

    private boolean isLocalProxyUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            if (!"/proxy-stream".equalsIgnoreCase(uri.getPath()) || isBlank(uri.getRawQuery())) {
                return false;
            }
            for (String parameter : uri.getRawQuery().split("&")) {
                if (parameter.startsWith("src=")) {
                    return true;
                }
            }
        } catch (IllegalArgumentException _) {
            return false;
        }
        return false;
    }

    private String buildLocalProxyUrl(String sourceUrl) {
        return ServerUrlUtil.getLocalServerUrl() + URL_FRAGMENT_PROXY_STREAM
                + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
    }

    private String unwrapLocalProxyUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"/proxy-stream".equalsIgnoreCase(uri.getPath()) || isBlank(uri.getRawQuery())) {
                return url;
            }
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && "src".equals(pair[0])) {
                    return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException _) {
            // Keep malformed URLs unchanged so playback can report its normal network error.
        }
        return url;
    }

    private boolean hasChannelMetadata(Channel channel) {
        if (channel == null) {
            return false;
        }
        return isNotBlank(channel.getChannelId())
                || isNotBlank(channel.getName())
                || isNotBlank(channel.getLogo())
                || isNotBlank(channel.getSeason())
                || isNotBlank(channel.getEpisodeNum())
                || isNotBlank(channel.getCmd())
                || isNotBlank(channel.getManifestType());
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private String normalizeWebPlaybackUrl(String mode, String url) {
        if (isBlank(url)) {
            return url;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) {
            return url;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith(HTTPS_PREFIX) && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE))) {
            return HTTP_PREFIX + url.substring(HTTPS_PREFIX.length());
        }
        return url;
    }

    private String sanitizeParam(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                String message = sanitizeParam(current.getMessage()).toLowerCase();
                if (message.contains("broken pipe")
                        || message.contains("connection reset")
                        || message.contains("stream closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private record ResolvedWebPlayback(PlayerResponse response,
                                       String bingeWatchToken,
                                       String currentEpisodeId,
                                       List<BingeWatchService.PlaylistItem> playlistItems) {
    }
}
