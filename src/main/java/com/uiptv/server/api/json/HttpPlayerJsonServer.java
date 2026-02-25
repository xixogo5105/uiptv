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
import com.uiptv.shared.Episode;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpPlayerJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String bookmarkId = getParam(ex, "bookmarkId");
        String accountId = getParam(ex, "accountId");
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String mode = getParam(ex, "mode");
        String seriesParentId = getParam(ex, "seriesParentId");
        String hvec = getParam(ex, "hvec");

        PlayerResponse response;

        if (isNotBlank(bookmarkId)) {
            Bookmark bookmark = BookmarkService.getInstance().getBookmark(bookmarkId);
            Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
            applyMode(account, mode);
            if (bookmark.getAccountAction() != null) {
                account.setAction(bookmark.getAccountAction());
            }
            
            Channel channel = null;
            if (isNotBlank(bookmark.getSeriesJson())) {
                Episode episode = Episode.fromJson(bookmark.getSeriesJson());
                if (episode != null) {
                    channel = new Channel();
                    channel.setCmd(episode.getCmd());
                    channel.setName(episode.getTitle());
                    channel.setChannelId(episode.getId());
                    if (episode.getInfo() != null) {
                        channel.setLogo(episode.getInfo().getMovieImage());
                    }
                }
            } else if (isNotBlank(bookmark.getChannelJson())) {
                channel = Channel.fromJson(bookmark.getChannelJson());
            } else if (isNotBlank(bookmark.getVodJson())) {
                channel = Channel.fromJson(bookmark.getVodJson());
            }

            if (channel == null) { // Fallback for legacy bookmarks
                channel = new Channel();
                channel.setCmd(bookmark.getCmd());
                channel.setChannelId(bookmark.getChannelId());
                channel.setName(bookmark.getChannelName());
                channel.setDrmType(bookmark.getDrmType());
                channel.setDrmLicenseUrl(bookmark.getDrmLicenseUrl());
                channel.setClearKeysJson(bookmark.getClearKeysJson());
                channel.setInputstreamaddon(bookmark.getInputstreamaddon());
                channel.setManifestType(bookmark.getManifestType());
            }
            String scopedCategoryId = resolveSeriesCategoryId(account, bookmark.getCategoryId());
            response = PlayerService.getInstance().get(account, channel, bookmark.getChannelId(), seriesParentId, scopedCategoryId);
        } else {
            Account account = AccountService.getInstance().getById(accountId);
            applyMode(account, mode);
            Channel channel = ChannelDb.get().getChannelById(channelId, categoryId);
            String reqName = sanitizeParam(getParam(ex, "name"));
            String reqLogo = sanitizeParam(getParam(ex, "logo"));
            String reqCmd = sanitizeParam(getParam(ex, "cmd"));
            String reqCmd1 = sanitizeParam(getParam(ex, "cmd_1"));
            String reqCmd2 = sanitizeParam(getParam(ex, "cmd_2"));
            String reqCmd3 = sanitizeParam(getParam(ex, "cmd_3"));
            String reqDrmType = sanitizeParam(getParam(ex, "drmType"));
            String reqDrmLicenseUrl = sanitizeParam(getParam(ex, "drmLicenseUrl"));
            String reqClearKeys = sanitizeParam(getParam(ex, "clearKeysJson"));
            String reqInputstreamAddon = sanitizeParam(getParam(ex, "inputstreamaddon"));
            String reqManifestType = sanitizeParam(getParam(ex, "manifestType"));
            String reqSeason = sanitizeParam(getParam(ex, "season"));
            String reqEpisodeNum = sanitizeParam(getParam(ex, "episodeNum"));

            if (channel == null) {
                channel = new Channel();
                channel.setChannelId(channelId);
                channel.setName(reqName);
                channel.setLogo(reqLogo);
                channel.setCmd(reqCmd);
                channel.setCmd_1(reqCmd1);
                channel.setCmd_2(reqCmd2);
                channel.setCmd_3(reqCmd3);
                channel.setDrmType(reqDrmType);
                channel.setDrmLicenseUrl(reqDrmLicenseUrl);
                channel.setClearKeysJson(reqClearKeys);
                channel.setInputstreamaddon(reqInputstreamAddon);
                channel.setManifestType(reqManifestType);
                channel.setSeason(reqSeason);
                channel.setEpisodeNum(reqEpisodeNum);
            } else {
                if (isBlank(channel.getName()) && isNotBlank(reqName)) channel.setName(reqName);
                if (isBlank(channel.getLogo()) && isNotBlank(reqLogo)) channel.setLogo(reqLogo);
                if (isBlank(channel.getCmd()) && isNotBlank(reqCmd)) channel.setCmd(reqCmd);
                if (isBlank(channel.getCmd_1()) && isNotBlank(reqCmd1)) channel.setCmd_1(reqCmd1);
                if (isBlank(channel.getCmd_2()) && isNotBlank(reqCmd2)) channel.setCmd_2(reqCmd2);
                if (isBlank(channel.getCmd_3()) && isNotBlank(reqCmd3)) channel.setCmd_3(reqCmd3);
                if (isBlank(channel.getDrmType()) && isNotBlank(reqDrmType)) channel.setDrmType(reqDrmType);
                if (isBlank(channel.getDrmLicenseUrl()) && isNotBlank(reqDrmLicenseUrl)) channel.setDrmLicenseUrl(reqDrmLicenseUrl);
                if (isBlank(channel.getClearKeysJson()) && isNotBlank(reqClearKeys)) channel.setClearKeysJson(reqClearKeys);
                if (isBlank(channel.getInputstreamaddon()) && isNotBlank(reqInputstreamAddon)) channel.setInputstreamaddon(reqInputstreamAddon);
                if (isBlank(channel.getManifestType()) && isNotBlank(reqManifestType)) channel.setManifestType(reqManifestType);
                if (isBlank(channel.getSeason()) && isNotBlank(reqSeason)) channel.setSeason(reqSeason);
                if (isBlank(channel.getEpisodeNum()) && isNotBlank(reqEpisodeNum)) channel.setEpisodeNum(reqEpisodeNum);
            }
            String seriesId = getParam(ex, "seriesId");
            String scopedCategoryId = resolveSeriesCategoryId(account, categoryId);
            response = PlayerService.getInstance().get(account, channel, seriesId, seriesParentId, scopedCategoryId);
        }

        String originalUrl = response.getUrl();
        response.setUrl(resolveWebPlaybackRedirects(mode, normalizeWebPlaybackUrl(mode, originalUrl)));

        // Check if transmuxing is needed and enabled
        boolean isFfmpegEnabled = ConfigurationService.getInstance().read().isEnableFfmpegTranscoding();
        boolean forceWebHls = shouldForceWebHls(mode, response)
                || shouldForceWebHlsForUrl(mode, originalUrl);
        if ((isFfmpegEnabled && FfmpegService.getInstance().isTransmuxingNeeded(response.getUrl())) || forceWebHls) {
            boolean hlsReady = false;
            String sourceUrl = response.getUrl();
            String transmuxInput = forceWebHls ? buildLocalProxyUrl(sourceUrl) : sourceUrl;
            try {
                hlsReady = FfmpegService.getInstance().startTransmuxing(transmuxInput, forceWebHls);
            } catch (Exception ignored) {
                hlsReady = false;
            }
            if (!hlsReady && forceWebHls) {
                String downgraded = downgradeHttpsToHttp(sourceUrl);
                if (!downgraded.equals(sourceUrl)) {
                    try {
                        hlsReady = FfmpegService.getInstance().startTransmuxing(buildLocalProxyUrl(downgraded), true);
                        sourceUrl = downgraded;
                    } catch (Exception ignored) {
                        hlsReady = false;
                    }
                }
            }
            if (hlsReady) {
                response.setUrl(isHvecEnabled(hvec) ? "/hls/stream.m3u8?hvec=1" : "/hls/stream.m3u8");
                response.setManifestType("hls");
            } else {
                response.setUrl(forceWebHls ? buildLocalProxyUrl(sourceUrl) : sourceUrl);
            }
        }

        generateJsonResponse(ex, buildJsonResponse(response));
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
        } catch (Exception ignored) {
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
        if (!"vod".equals(normalizedMode) && !"series".equals(normalizedMode)) {
            return false;
        }
        String url = response.getUrl().toLowerCase();
        boolean forcedPath = isForcedWebPath(url);
        if (forcedPath) {
            return true;
        }
        // Skip DRM-only flows for non-forced URLs; keep those in direct player path.
        if (isNotBlank(response.getDrmType()) || isNotBlank(response.getInputstreamaddon())) {
            return false;
        }
        return false;
    }

    private String normalizeWebPlaybackUrl(String mode, String url) {
        if (isBlank(url)) {
            return url;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!"vod".equals(normalizedMode) && !"series".equals(normalizedMode)) {
            return url;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") && (lower.contains("/live/play/") || lower.contains("/play/movie.php"))) {
            return "http://" + url.substring("https://".length());
        }
        return url;
    }

    private String downgradeHttpsToHttp(String url) {
        if (isBlank(url)) {
            return url;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") && (lower.contains("/live/play/") || lower.contains("/play/movie.php"))) {
            return "http://" + url.substring("https://".length());
        }
        return url;
    }

    private String buildLocalProxyUrl(String sourceUrl) {
        String port = ConfigurationService.getInstance().read().getServerPort();
        if (isBlank(port)) {
            port = "8888";
        }
        return "http://127.0.0.1:" + port + "/proxy-stream?src=" + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
    }

    private String resolveWebPlaybackRedirects(String mode, String inputUrl) {
        if (isBlank(inputUrl)) {
            return inputUrl;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!"vod".equals(normalizedMode) && !"series".equals(normalizedMode)) {
            return inputUrl;
        }

        String current = inputUrl;
        String lower = current.toLowerCase();
        boolean forceHttpChain = lower.contains("/live/play/") || lower.contains("/play/movie.php");
        if (!forceHttpChain) {
            return current;
        }

        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(current);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "UIPTV/1.0");
                conn.connect();

                int status = conn.getResponseCode();
                if (status < 300 || status > 399) {
                    return current;
                }

                String location = conn.getHeaderField("Location");
                if (isBlank(location)) {
                    return forceHttpChain && current.toLowerCase().startsWith("https://")
                            ? "http://" + current.substring("https://".length())
                            : current;
                }

                URI base = URI.create(current);
                URI resolved = base.resolve(location);
                current = resolved.toString();
                if (forceHttpChain && current.toLowerCase().startsWith("https://")) {
                    current = "http://" + current.substring("https://".length());
                } else {
                    current = downgradeHttpsToHttp(current);
                }
            } catch (Exception ignored) {
                return current;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return current;
    }

    private boolean shouldForceWebHlsForUrl(String mode, String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalizedMode = isBlank(mode) ? "" : mode.trim().toLowerCase();
        if (!"vod".equals(normalizedMode) && !"series".equals(normalizedMode)) {
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
        return url.contains("/play/movie.php")
                || url.contains("/live/play/")
                || url.contains("type=movie")
                || url.contains("type=series")
                || url.contains("extension=mp4")
                || url.contains("extension=mkv")
                || url.contains("extension=mpg")
                || url.contains("extension=mpeg")
                || url.matches(".*\\.(mpg|mpeg|mkv|avi|wmv)(\\?.*)?$")
                || url.matches(".*/\\d+(\\?.*)?$");
    }
}
