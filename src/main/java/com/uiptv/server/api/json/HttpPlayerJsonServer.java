package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.*;
import com.uiptv.util.HttpUtil;
import com.uiptv.shared.Episode;
import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

@SuppressWarnings("java:S1075")
public class HttpPlayerJsonServer implements HttpHandler {
    private static final String HEADER_LOCATION = "Location";
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";
    private static final String MODE_SERIES = "series";
    private static final String MODE_VOD = "vod";
    private static final String PATH_LIVE_PLAY = "/live/play/";
    private static final String PATH_PLAY_MOVIE = "/play/movie.php";
    private static final String USER_AGENT = "UIPTV/1.0";
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        String accountId = getParam(ex, "accountId");
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String mode = getParam(ex, "mode");
        String seriesParentId = getParam(ex, "seriesParentId");
        String hvec = getParam(ex, "hvec");
        PlayerResponse response = isNotBlank(bookmarkId)
                ? resolveBookmarkPlayback(bookmarkId, mode, seriesParentId)
                : resolveDirectPlayback(ex, accountId, categoryId, channelId, mode, seriesParentId);
        applyWebPlaybackProcessing(response, mode, hvec);

        generateJsonResponse(ex, buildJsonResponse(response));
    }

    private void applyWebPlaybackProcessing(PlayerResponse response, String mode, String hvec) {
        String originalUrl = response.getUrl();
        String normalizedUrl = resolveWebPlaybackRedirects(mode, normalizeWebPlaybackUrl(mode, originalUrl));
        response.setUrl(normalizedUrl);
        if (!shouldStartTransmuxing(response, mode, originalUrl)) {
            return;
        }
        applyTransmuxedPlayback(response, mode, originalUrl, hvec);
    }

    private boolean shouldStartTransmuxing(PlayerResponse response, String mode, String originalUrl) {
        boolean forceWebHls = shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl);
        return forceWebHls
                || (ConfigurationService.getInstance().read().isEnableFfmpegTranscoding()
                && FfmpegService.getInstance().isTransmuxingNeeded(response.getUrl()));
    }

    private void applyTransmuxedPlayback(PlayerResponse response, String mode, String originalUrl, String hvec) {
        boolean forceWebHls = shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl);
        String sourceUrl = response.getUrl();
        if (startTransmuxing(sourceUrl, forceWebHls)) {
            setHlsPlayback(response, hvec);
            return;
        }
        String fallbackUrl = forceWebHls ? retryForcedWebHls(sourceUrl) : sourceUrl;
        if (forceWebHls && !fallbackUrl.equals(sourceUrl) && startTransmuxing(fallbackUrl, true)) {
            setHlsPlayback(response, hvec);
            return;
        }
        response.setUrl(forceWebHls ? buildLocalProxyUrl(fallbackUrl) : fallbackUrl);
    }

    private boolean startTransmuxing(String sourceUrl, boolean forceWebHls) {
        String transmuxInput = forceWebHls ? buildLocalProxyUrl(sourceUrl) : sourceUrl;
        try {
            return FfmpegService.getInstance().startTransmuxing(transmuxInput, forceWebHls);
        } catch (Exception _) {
            return false;
        }
    }

    private String retryForcedWebHls(String sourceUrl) {
        String downgraded = downgradeHttpsToHttp(sourceUrl);
        return downgraded.equals(sourceUrl) ? sourceUrl : downgraded;
    }

    private void setHlsPlayback(PlayerResponse response, String hvec) {
        response.setUrl(isHvecEnabled(hvec) ? "/hls/stream.m3u8?hvec=1" : "/hls/stream.m3u8");
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
        Channel channel = mergeRequestChannel(ChannelDb.get().getChannelById(channelId, categoryId), channelId, ex);
        String seriesId = getParam(ex, "seriesId");
        String scopedCategoryId = resolveSeriesCategoryId(account, categoryId);
        return PlayerService.getInstance().get(account, channel, seriesId, seriesParentId, scopedCategoryId);
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
        channel.setDrmLicenseUrl(sanitizeParam(getParam(ex, "drmLicenseUrl")));
        channel.setClearKeysJson(sanitizeParam(getParam(ex, "clearKeysJson")));
        channel.setInputstreamaddon(sanitizeParam(getParam(ex, "inputstreamaddon")));
        channel.setManifestType(sanitizeParam(getParam(ex, "manifestType")));
        channel.setSeason(sanitizeParam(getParam(ex, "season")));
        channel.setEpisodeNum(sanitizeParam(getParam(ex, "episodeNum")));
        return channel;
    }

    private void fillIfBlank(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String value) {
        if (isBlank(getter.get()) && isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private String buildJsonResponse(PlayerResponse response) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"url\":\"").append(response.getUrl()).append("\"");

        boolean hasDrm = isNotBlank(response.getDrmType()) || isNotBlank(response.getInputstreamaddon()) || isNotBlank(response.getManifestType());
        if (hasDrm) {
            json.append(",\"drm\":{");
            if (isNotBlank(response.getDrmType())) {
                json.append("\"type\":\"").append(response.getDrmType()).append("\",");
            }
            if (isNotBlank(response.getDrmLicenseUrl())) {
                json.append("\"licenseUrl\":\"").append(response.getDrmLicenseUrl()).append("\",");
            }
            if (isNotBlank(response.getClearKeysJson())) {
                json.append("\"clearKeys\":").append(response.getClearKeysJson()).append(",");
            }
            if (isNotBlank(response.getInputstreamaddon())) {
                json.append("\"inputstreamaddon\":\"").append(response.getInputstreamaddon()).append("\",");
            }
            if (isNotBlank(response.getManifestType())) {
                json.append("\"manifestType\":\"").append(response.getManifestType()).append("\"");
            }
            if (json.charAt(json.length() - 1) == ',') {
                json.deleteCharAt(json.length() - 1);
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
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
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private boolean shouldForceWebHls(String mode, PlayerResponse response) {
        if (response == null || isBlank(response.getUrl())) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) {
            return false;
        }
        String url = response.getUrl().toLowerCase();
        return isForcedWebPath(url);
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
        return ServerUrlUtil.getLocalServerUrl() + "/proxy-stream?src=" + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
    }

    private String resolveWebPlaybackRedirects(String mode, String inputUrl) {
        if (isBlank(inputUrl)) {
            return inputUrl;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) {
            return inputUrl;
        }

        String current = inputUrl;
        boolean forceHttpChain = isForcedHttpChain(current);
        if (!forceHttpChain) {
            return current;
        }

        for (int i = 0; i < 5; i++) {
            try {
                HttpUtil.HttpResult response = followRedirectResponse(current);
                if (!isRedirect(response.statusCode())) {
                    return current;
                }
                current = resolveRedirectTarget(current, response, forceHttpChain);
            } catch (Exception _) {
                return current;
            }
        }
        return current;
    }

    private boolean isForcedHttpChain(String url) {
        String lower = url.toLowerCase();
        return lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE);
    }

    private HttpUtil.HttpResult followRedirectResponse(String current) throws IOException {
        return HttpUtil.sendRequest(
                current,
                Map.of("User-Agent", USER_AGENT),
                "GET",
                null,
                new HttpUtil.RequestOptions(false, false)
        );
    }

    private boolean isRedirect(int status) {
        return status >= 300 && status <= 399;
    }

    private String resolveRedirectTarget(String current, HttpUtil.HttpResult response, boolean forceHttpChain) {
        String location = firstHeader(response.responseHeaders(), HEADER_LOCATION);
        if (isBlank(location)) {
            return forceHttpChain && current.toLowerCase().startsWith(HTTPS_PREFIX)
                    ? HTTP_PREFIX + current.substring(HTTPS_PREFIX.length())
                    : current;
        }
        URI base = URI.create(current);
        URI resolved = base.resolve(location);
        return normalizeRedirectUrl(resolved.toString(), forceHttpChain);
    }

    private String normalizeRedirectUrl(String url, boolean forceHttpChain) {
        if (forceHttpChain && url.toLowerCase().startsWith(HTTPS_PREFIX)) {
            return HTTP_PREFIX + url.substring(HTTPS_PREFIX.length());
        }
        return downgradeHttpsToHttp(url);
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || isBlank(name)) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty() && isNotBlank(values.get(0))) {
                    return values.get(0);
                }
            }
        }
        return "";
    }

    private boolean shouldForceWebHlsForUrl(String mode, String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!MODE_VOD.equals(normalizedMode) && !MODE_SERIES.equals(normalizedMode)) {
            return false;
        }
        return isForcedWebPath(url.toLowerCase());
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
                || url.matches(".*\\.(mpg|mpeg|mkv|avi|wmv)(\\?.*)?$")
                || url.matches(".*/\\d+(\\?.*)?$");
    }
}
