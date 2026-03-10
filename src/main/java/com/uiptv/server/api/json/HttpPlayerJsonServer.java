package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;
import com.uiptv.util.AppLog;
import com.uiptv.util.ServerUrlUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.uiptv.util.ServerUtils.*;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

@SuppressWarnings("java:S1075")
public class HttpPlayerJsonServer implements HttpHandler {
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
    private static final String URL_FRAGMENT_HLS_M3U8 = ".m3u8";
    private static final String URL_FRAGMENT_LOCAL_HLS = "/hls/stream.m3u8";
    private static final String URL_FRAGMENT_PROXY_STREAM = "/proxy-stream?src=";
    private static final String URL_FRAGMENT_EXTENSION_TS = "extension=ts";
    private static final String URL_SUFFIX_TS = ".ts";
    private static final String URL_SUFFIX_MPEGTS = ".mpegts";
    private static final String QUERY_PARAM_TOKEN = "token=";
    private static final String QUERY_PARAM_PLAY_TOKEN = "play_token=";
    private static final String QUERY_PARAM_PREFER_HLS = "preferHls";
    private static final String PATH_LIVE_PLAY = "/live/play/";
    private static final String PATH_PLAY_MOVIE = "/play/movie.php";
    private static final String STRATEGY_HINT_SHAKA = "SHAKA";
    private static final String STRATEGY_HINT_NATIVE_PROXY = "NATIVE_PROXY";
    private static final String STRATEGY_HINT_NATIVE = "NATIVE";
    private static final boolean WEB_VOD_STYLE_PLAYLIST = false;
    public static final String SEASON = "season";
    public static final String EPISODE_NUM = "episodeNum";
    public static final String MANIFEST_TYPE = "manifestType";
    public static final String INPUTSTREAMADDON = "inputstreamaddon";
    public static final String CLEAR_KEYS_JSON = "clearKeysJson";
    public static final String DRM_LICENSE_URL = "drmLicenseUrl";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String mode = resolveRequestedMode(ex, getParam(ex, "mode"));
            String hvec = getParam(ex, "hvec");
            ResolvedWebPlayback resolved = resolvePlayback(ex, mode);
            applyWebPlaybackProcessing(resolved.response(), mode, hvec, isEnabledFlag(getParam(ex, QUERY_PARAM_PREFER_HLS)));
            generateJsonResponse(ex, buildJsonResponse(resolved));
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                return;
            }
            AppLog.addLog("HttpPlayerJsonServer failed: " + e);
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

    private void applyWebPlaybackProcessing(PlayerResponse response, String mode, String hvec, boolean preferHls) {
        if (response == null || isBlank(response.getUrl())) {
            stopTransmuxingIfActive();
            return;
        }
        String originalUrl = response.getUrl();
        String normalizedUrl = normalizeWebPlaybackUrl(mode, originalUrl);
        response.setUrl(normalizedUrl);
        if (shouldBypassLocalProxyWebPlayback(response, mode, normalizedUrl)) {
            stopTransmuxingIfActive();
            return;
        }
        if (!shouldStartTransmuxing(response, mode, originalUrl, preferHls)) {
            stopTransmuxingIfActive();
            if (shouldUseLocalProxyWebPlayback(mode, normalizedUrl)) {
                response.setUrl(buildLocalProxyUrl(normalizedUrl));
            }
            return;
        }
        applyTransmuxedPlayback(response, mode, originalUrl, hvec, preferHls);
    }

    private boolean shouldStartTransmuxing(PlayerResponse response, String mode, String originalUrl, boolean preferHls) {
        boolean forceWebHls = preferHls || shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl);
        if (shouldPreferDirectLivePlayback(mode, originalUrl) && !forceWebHls) {
            return false;
        }
        return forceWebHls
                || (ConfigurationService.getInstance().read().isEnableFfmpegTranscoding()
                && FfmpegService.getInstance().isTransmuxingNeeded(response.getUrl()));
    }

    private void applyTransmuxedPlayback(PlayerResponse response, String mode, String originalUrl, String hvec, boolean preferHls) {
        boolean forceWebHls = preferHls || shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl);
        String sourceUrl = response.getUrl();
        boolean vodStylePlaylist = shouldUseVodStylePlaylist();
        if (startTransmuxing(sourceUrl, forceWebHls, vodStylePlaylist)) {
            setHlsPlayback(response, hvec);
            return;
        }
        String fallbackUrl = forceWebHls ? retryForcedWebHls(sourceUrl) : sourceUrl;
        if (forceWebHls && !fallbackUrl.equals(sourceUrl) && startTransmuxing(fallbackUrl, true, vodStylePlaylist)) {
            setHlsPlayback(response, hvec);
            return;
        }
        // Keep original stream URL on fallback so the browser strategy layer can
        // try native first and only move to proxy when necessary.
        stopTransmuxingIfActive();
        response.setUrl(fallbackUrl);
    }

    private boolean startTransmuxing(String sourceUrl, boolean forceWebHls, boolean vodStylePlaylist) {
        if (!forceWebHls) {
            return tryStartTransmuxingInput(sourceUrl, vodStylePlaylist);
        }
        // For forced web-HLS flows, prefer direct server-side ffmpeg input first.
        // This avoids proxy-induced edge cases for some Stalker TS series URLs.
        if (tryStartTransmuxingInput(sourceUrl, vodStylePlaylist)) {
            return true;
        }
        String proxied = buildLocalProxyUrl(sourceUrl);
        return !sourceUrl.equals(proxied) && tryStartTransmuxingInput(proxied, vodStylePlaylist);
    }

    private boolean shouldUseVodStylePlaylist() {
        // Keep rolling playlists for web playback so advertised segments stay aligned
        // with in-memory eviction and avoid stale segment fetches.
        return WEB_VOD_STYLE_PLAYLIST;
    }

    private void stopTransmuxingIfActive() {
        try {
            FfmpegService.getInstance().stopTransmuxing();
        } catch (Exception _) {
            // Stopping stale transmux should not break playback response.
        }
    }

    private boolean tryStartTransmuxingInput(String inputUrl, boolean forceWebHls) {
        try {
            return FfmpegService.getInstance().startTransmuxing(inputUrl, forceWebHls);
        } catch (Exception _) {
            return false;
        }
    }

    private String retryForcedWebHls(String sourceUrl) {
        String downgraded = downgradeHttpsToHttp(sourceUrl);
        return downgraded.equals(sourceUrl) ? sourceUrl : downgraded;
    }

    private void setHlsPlayback(PlayerResponse response, String hvec) {
        response.setUrl(isHvecEnabled(hvec) ? URL_FRAGMENT_LOCAL_HLS + "?hvec=1" : URL_FRAGMENT_LOCAL_HLS);
        response.setManifestType("hls");
    }

    private PlayerResponse resolveBookmarkPlayback(String bookmarkId, String mode, String seriesParentId) throws IOException {
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
        applyMode(account, mode);
        if (bookmark.getAccountAction() != null) {
            account.setAction(bookmark.getAccountAction());
        }
        Channel channel = resolveBookmarkChannel(bookmark);
        String scopedCategoryId = resolveSeriesCategoryId(account, bookmark.getCategoryId());
        return PlayerService.getInstance().get(account, channel, bookmark.getChannelId(), seriesParentId, scopedCategoryId);
    }

    private Channel resolveBookmarkChannel(Bookmark bookmark) {
        Channel channel = readBookmarkSnapshot(bookmark);
        if (channel != null) {
            return channel;
        }
        return createLegacyBookmarkChannel(bookmark);
    }

    private Channel readBookmarkSnapshot(Bookmark bookmark) {
        if (isNotBlank(bookmark.getSeriesJson())) {
            Episode episode = Episode.fromJson(bookmark.getSeriesJson());
            if (episode != null) {
                Channel channel = new Channel();
                channel.setCmd(episode.getCmd());
                channel.setName(episode.getTitle());
                channel.setChannelId(episode.getId());
                if (episode.getInfo() != null) {
                    channel.setLogo(episode.getInfo().getMovieImage());
                }
                return channel;
            }
        }
        if (isNotBlank(bookmark.getChannelJson())) {
            return Channel.fromJson(bookmark.getChannelJson());
        }
        if (isNotBlank(bookmark.getVodJson())) {
            return Channel.fromJson(bookmark.getVodJson());
        }
        return null;
    }

    private Channel createLegacyBookmarkChannel(Bookmark bookmark) {
        Channel channel = new Channel();
        channel.setCmd(bookmark.getCmd());
        channel.setChannelId(bookmark.getChannelId());
        channel.setName(bookmark.getChannelName());
        channel.setDrmType(bookmark.getDrmType());
        channel.setDrmLicenseUrl(bookmark.getDrmLicenseUrl());
        channel.setClearKeysJson(bookmark.getClearKeysJson());
        channel.setInputstreamaddon(bookmark.getInputstreamaddon());
        channel.setManifestType(bookmark.getManifestType());
        return channel;
    }

    private PlayerResponse resolveDirectPlayback(HttpExchange ex, String accountId, String categoryId, String channelId,
                                                 String mode, String seriesParentId) throws IOException {
        Account account = AccountService.getInstance().getById(accountId);
        applyMode(account, mode);
        Channel channel = mergeRequestChannel(resolveRequestedChannel(account, categoryId, channelId, mode), channelId, ex);
        String seriesId = getParam(ex, "seriesId");
        String scopedCategoryId = resolveSeriesCategoryId(account, categoryId);
        return PlayerService.getInstance().get(account, channel, seriesId, seriesParentId, scopedCategoryId);
    }

    private Channel resolveRequestedChannel(Account account, String categoryId, String channelId, String mode) {
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (MODE_VOD.equals(normalizedMode) && account != null) {
            Channel vodChannel = VodChannelDb.get().getChannelByChannelId(channelId, categoryId, account.getDbId());
            if (vodChannel != null) {
                return vodChannel;
            }
            return VodChannelDb.get().getChannelByChannelIdAndAccount(channelId, account.getDbId());
        }
        return ChannelDb.get().getChannelById(channelId, categoryId);
    }

    private Channel mergeRequestChannel(Channel channel, String channelId, HttpExchange ex) {
        Channel requestChannel = buildRequestChannel(channelId, ex);
        if (channel == null) {
            return requestChannel;
        }
        fillIfBlank(channel::getName, channel::setName, requestChannel.getName());
        fillIfBlank(channel::getLogo, channel::setLogo, requestChannel.getLogo());
        fillIfBlank(channel::getCmd, channel::setCmd, requestChannel.getCmd());
        fillIfBlank(channel::getCmd_1, channel::setCmd_1, requestChannel.getCmd_1());
        fillIfBlank(channel::getCmd_2, channel::setCmd_2, requestChannel.getCmd_2());
        fillIfBlank(channel::getCmd_3, channel::setCmd_3, requestChannel.getCmd_3());
        fillIfBlank(channel::getDrmType, channel::setDrmType, requestChannel.getDrmType());
        fillIfBlank(channel::getDrmLicenseUrl, channel::setDrmLicenseUrl, requestChannel.getDrmLicenseUrl());
        fillIfBlank(channel::getClearKeysJson, channel::setClearKeysJson, requestChannel.getClearKeysJson());
        fillIfBlank(channel::getInputstreamaddon, channel::setInputstreamaddon, requestChannel.getInputstreamaddon());
        fillIfBlank(channel::getManifestType, channel::setManifestType, requestChannel.getManifestType());
        fillIfBlank(channel::getSeason, channel::setSeason, requestChannel.getSeason());
        fillIfBlank(channel::getEpisodeNum, channel::setEpisodeNum, requestChannel.getEpisodeNum());
        return channel;
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

    private void fillIfBlank(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String value) {
        if (isBlank(getter.get()) && isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private String buildJsonResponse(ResolvedWebPlayback resolved) {
        PlayerResponse response = resolved.response();
        JSONObject json = new JSONObject();
        json.put("url", response == null ? "" : response.getUrl());
        putIfNotBlank(json, JSON_KEY_STRATEGY_HINT, determineStrategyHint(response));
        appendChannelJson(json, response);
        appendDrmJson(json, response);
        appendBingeWatchJson(json, resolved);
        return json.toString();
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
        if (hasDrmMetadata(response) || isDashUrl(lowerUrl) || isLocalTransmuxedHls(lowerUrl)) {
            return STRATEGY_HINT_SHAKA;
        }
        if (isLocalProxyUrl(lowerUrl)) {
            return STRATEGY_HINT_NATIVE_PROXY;
        }
        if (isForcedWebPath(lowerUrl)) {
            return STRATEGY_HINT_NATIVE_PROXY;
        }
        return STRATEGY_HINT_NATIVE;
    }

    private boolean isDashUrl(String url) {
        return url.contains(URL_FRAGMENT_DASH_MPD);
    }

    private boolean isLocalTransmuxedHls(String url) {
        return url.contains(URL_FRAGMENT_LOCAL_HLS);
    }

    private boolean isLocalProxyUrl(String url) {
        return url.contains(URL_FRAGMENT_PROXY_STREAM);
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

    private record ResolvedWebPlayback(PlayerResponse response,
                                       String bingeWatchToken,
                                       String currentEpisodeId,
                                       List<BingeWatchService.PlaylistItem> playlistItems) {
    }

    private void applyMode(Account account, String mode) {
        if (account == null || isBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception _) {
            account.setAction(Account.AccountAction.itv);
        }
    }

    private String resolveSeriesCategoryId(Account account, String rawCategoryId) {
        if (account == null || account.getAction() != Account.AccountAction.series) {
            return "";
        }
        if (isBlank(rawCategoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(rawCategoryId);
        if (category != null && isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return rawCategoryId;
    }

    private boolean isHvecEnabled(String value) {
        return isEnabledFlag(value);
    }

    private boolean shouldForceWebHls(String mode, PlayerResponse response) {
        if (response == null || isBlank(response.getUrl())) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_SERIES.equals(normalizedMode)) {
            return false;
        }
        return shouldForceSeriesWebHls(response.getUrl().toLowerCase());
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

    private String downgradeHttpsToHttp(String url) {
        if (isBlank(url)) {
            return url;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith(HTTPS_PREFIX) && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE))) {
            return HTTP_PREFIX + url.substring(HTTPS_PREFIX.length());
        }
        return url;
    }

    private String buildLocalProxyUrl(String sourceUrl) {
        return ServerUrlUtil.getLocalServerUrl() + URL_FRAGMENT_PROXY_STREAM + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
    }

    private boolean shouldBypassLocalProxyWebPlayback(PlayerResponse response, String mode, String url) {
        if (response == null || isBlank(url)) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode)) {
            return false;
        }
        boolean hasDrm = isNotBlank(response.getDrmType())
                || isNotBlank(response.getDrmLicenseUrl())
                || isNotBlank(response.getClearKeysJson())
                || isNotBlank(response.getInputstreamaddon())
                || isNotBlank(response.getManifestType());
        if (hasDrm) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains(PATH_LIVE_PLAY)
                || hasTrailingNumericPath(lower);
    }


    private boolean shouldForceWebHlsForUrl(String mode, String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_SERIES.equals(normalizedMode)) {
            return false;
        }
        return shouldForceSeriesWebHls(url.toLowerCase());
    }

    private boolean shouldForceSeriesWebHls(String lowerUrl) {
        if (isBlank(lowerUrl)) {
            return false;
        }
        if (isAdaptivePlaybackUrl(lowerUrl) || hasKnownProgressiveVideoExtension(lowerUrl) || hasKnownProgressiveVideoQuery(lowerUrl)) {
            return false;
        }
        return isForcedWebPath(lowerUrl);
    }

    private boolean shouldUseLocalProxyWebPlayback(String mode, String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) {
            return false;
        }
        return isForcedWebPath(url.toLowerCase());
    }

    private boolean shouldPreferDirectLivePlayback(String mode, String url) {
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (isBlank(url) || !MODE_ITV.equals(normalizedMode)) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        if (isAdaptivePlaybackUrl(lowerUrl) && !hasTokenizedAccess(lowerUrl)) {
            return false;
        }
        return isLikelyMpegTsUrl(lowerUrl)
                || hasTrailingNumericPath(lowerUrl)
                || lowerUrl.contains(PATH_LIVE_PLAY);
    }

    private boolean isLikelyMpegTsUrl(String lowerUrl) {
        if (isBlank(lowerUrl)) {
            return false;
        }
        String path = stripQuery(lowerUrl);
        return lowerUrl.contains(URL_FRAGMENT_EXTENSION_TS)
                || path.endsWith(URL_SUFFIX_TS)
                || path.endsWith(URL_SUFFIX_MPEGTS);
    }

    private boolean isAdaptivePlaybackUrl(String lowerUrl) {
        if (isBlank(lowerUrl)) {
            return false;
        }
        return lowerUrl.contains(URL_FRAGMENT_HLS_M3U8)
                || lowerUrl.contains(URL_FRAGMENT_DASH_MPD)
                || lowerUrl.contains(URL_FRAGMENT_LOCAL_HLS);
    }

    private boolean hasTokenizedAccess(String lowerUrl) {
        if (isBlank(lowerUrl)) {
            return false;
        }
        return lowerUrl.contains(QUERY_PARAM_TOKEN) || lowerUrl.contains(QUERY_PARAM_PLAY_TOKEN);
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

    private boolean isEnabledFlag(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
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

    private boolean isForcedWebPath(String url) {
        if (isBlank(url)) return false;
        return url.contains(PATH_PLAY_MOVIE)
                || url.contains(PATH_LIVE_PLAY)
                || url.contains("type=movie")
                || url.contains("type=" + MODE_SERIES)
                || url.contains("extension=mp4")
                || url.contains("extension=mkv")
                || url.contains("extension=mpg")
                || url.contains("extension=mpeg")
                || hasVideoFileExtension(url)
                || hasTrailingNumericPath(url);
    }

    private boolean hasVideoFileExtension(String url) {
        String path = stripQuery(url).toLowerCase();
        return path.endsWith(".mpg")
                || path.endsWith(".mpeg")
                || path.endsWith(".mkv")
                || path.endsWith(".avi")
                || path.endsWith(".wmv");
    }

    private boolean hasKnownProgressiveVideoExtension(String url) {
        String path = stripQuery(url).toLowerCase();
        return path.endsWith(".mp4")
                || path.endsWith(".m4v")
                || path.endsWith(".mov")
                || path.endsWith(".webm");
    }

    private boolean hasKnownProgressiveVideoQuery(String url) {
        if (isBlank(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        boolean streamLooksProgressive = lower.contains("stream=")
                && (lower.contains(".mp4")
                || lower.contains(".m4v")
                || lower.contains(".mov")
                || lower.contains(".webm"));
        return streamLooksProgressive
                || lower.contains(".mp4&")
                || lower.contains(".m4v&")
                || lower.contains(".mov&")
                || lower.contains(".webm&")
                || lower.contains("extension=mp4")
                || lower.contains("extension=m4v")
                || lower.contains("extension=mov")
                || lower.contains("extension=webm");
    }

    private boolean hasTrailingNumericPath(String url) {
        String path = stripQuery(url);
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return false;
        }
        return isDigitsOnly(path.substring(slashIndex + 1));
    }

    private String stripQuery(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
    }

    private boolean isDigitsOnly(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
