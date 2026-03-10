package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AppLog;
import com.uiptv.util.ServerUrlUtil;
import javafx.scene.Node;
import javafx.scene.Scene;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public final class PlaybackUIService {
    private static final String PLAYLIST_RESOLUTION_FAILURE = "Playback failed: unable to resolve playlist URL.";
    private static final String DEFAULT_MODE = "series";
    static final String WEB_BROWSER_PLAYER_PATH = "__web_browser_player__";
    static final String EMBEDDED_PLAYER_PATH = "__embedded_player__";

    private PlaybackUIService() {
    }

    public static List<PlayerOption> getConfiguredPlayerOptions() {
        Configuration configuration = ConfigurationService.getInstance().read();
        String player1 = configuration == null ? "" : configuration.getPlayerPath1();
        String player2 = configuration == null ? "" : configuration.getPlayerPath2();
        String player3 = configuration == null ? "" : configuration.getPlayerPath3();
        return List.of(
                new PlayerOption(I18n.tr("autoEmbeddedPlayer"), EMBEDDED_PLAYER_PATH),
                new PlayerOption(I18n.tr("configDefaultWebBrowserPlayer"), WEB_BROWSER_PLAYER_PATH),
                new PlayerOption(I18n.tr("autoPlayer1"), player1),
                new PlayerOption(I18n.tr("autoPlayer2"), player2),
                new PlayerOption(I18n.tr("autoPlayer3"), player3)
        );
    }

    public static void play(Node source, PlaybackRequest request) {
        if (source == null || request == null || request.account == null || request.channel == null) {
            return;
        }

        Configuration configuration = ConfigurationService.getInstance().read();
        PlaybackModeContext context = buildPlaybackModeContext(configuration, request);

        if (handleBrowserPlayback(context, request)) return;
        if (handleDrmBrowserFallback(context, request)) return;

        Scene scene = source.getScene();
        if (scene != null) {
            scene.setCursor(javafx.scene.Cursor.WAIT);
        }

        new Thread(() -> {
            try {
                PlayerResponse response = resolvePlayerResponse(request);
                response.setFromChannel(request.channel, request.account);
                runLater(() -> launchResolvedPlayback(context, request, response));
            } catch (Exception e) {
                runLater(() -> showErrorAlert(request.errorPrefix + e.getMessage()));
            } finally {
                if (scene != null) {
                    runLater(() -> scene.setCursor(javafx.scene.Cursor.DEFAULT));
                }
            }
        }).start();
    }

    public static void playDirectUrl(String playerPath, String url, String errorPrefix) {
        playDirectUrl(playerPath, url, errorPrefix, null, null);
    }

    public static void playDirectUrl(String playerPath, String url, String errorPrefix, Account account, Channel channel) {
        if (isBlank(url)) {
            showErrorAlert(isBlank(errorPrefix) ? PLAYLIST_RESOLUTION_FAILURE : errorPrefix + "unable to resolve playlist URL.");
            return;
        }
        PlayerResponse response = new PlayerResponse(url);
        if (channel != null) {
            response.setFromChannel(channel, account);
        }
        if (isEmbeddedPlayerPath(playerPath)) {
            Configuration configuration = ConfigurationService.getInstance().read();
            playEmbedded(response, configuration != null && configuration.isEmbeddedPlayer());
            return;
        }
        if (isBrowserPlayerPath(playerPath)) {
            if (!ServerUrlUtil.ensureServerForWebPlayback()) {
                showErrorAlert(isBlank(errorPrefix) ? PLAYLIST_RESOLUTION_FAILURE : errorPrefix + "unable to start local web player.");
                return;
            }
            String browserUrl = buildBrowserDirectPlaybackUrl(url, account, channel);
            ServerUrlUtil.openInBrowser(browserUrl);
            return;
        }
        if (isBlank(playerPath)) {
            showErrorAlert(I18n.tr("autoNoDefaultPlayerConfigured"));
            return;
        }
        com.uiptv.util.Platform.executeCommand(playerPath, url);
    }

    private static PlaybackModeContext buildPlaybackModeContext(Configuration configuration, PlaybackRequest request) {
        boolean useEmbeddedPlayerConfig = configuration != null && configuration.isEmbeddedPlayer();
        boolean browserIsDefaultConfig = configuration != null
                && WEB_BROWSER_PLAYER_PATH.equalsIgnoreCase(String.valueOf(configuration.getDefaultPlayerPath()).trim());
        boolean playerPathIsEmbedded = isEmbeddedPlayerPath(request.playerPath);
        boolean playerPathIsBrowser = WEB_BROWSER_PLAYER_PATH.equalsIgnoreCase(String.valueOf(request.playerPath).trim());
        String mode = request.account.getAction() == null ? "itv" : request.account.getAction().name();
        return new PlaybackModeContext(useEmbeddedPlayerConfig, browserIsDefaultConfig, playerPathIsEmbedded, playerPathIsBrowser, mode);
    }

    private static boolean handleBrowserPlayback(PlaybackModeContext context, PlaybackRequest request) {
        if (!context.playerPathIsBrowser()) {
            return false;
        }
        return openBrowserPlayback(context.mode(), I18n.tr("autoUnableToStartLocalWebServerForBrowserPlayback"), request);
    }

    private static boolean handleDrmBrowserFallback(PlaybackModeContext context, PlaybackRequest request) {
        if (!request.allowDrmBrowserFallback || !PlayerService.getInstance().isDrmProtected(request.channel)) {
            return false;
        }
        if (!context.browserIsDefaultConfig()) {
            String localServerUrl = ServerUrlUtil.getLocalServerUrl();
            boolean confirmed = showConfirmationAlert(I18n.tr("autoDrmBrowserOnlyConfirm", localServerUrl));
            if (!confirmed) {
                return true;
            }
        }
        return openBrowserPlayback(context.mode(), I18n.tr("autoUnableToStartLocalWebServerForDRMPlayback"), request);
    }

    private static boolean openBrowserPlayback(String mode, String startupFailureMessage, PlaybackRequest request) {
        if (!ServerUrlUtil.ensureServerForWebPlayback()) {
            showErrorAlert(startupFailureMessage);
            return true;
        }
        if (request == null) {
            return false;
        }
        String browserUrl = PlayerService.getInstance()
                .buildDrmBrowserPlaybackUrl(
                        request.account,
                        request.channel,
                        request.categoryId,
                        mode,
                        request.seriesId,
                        request.seriesCategoryId
                );
        ServerUrlUtil.openInBrowser(browserUrl);
        return true;
    }

    private static PlayerResponse resolvePlayerResponse(PlaybackRequest request) throws IOException {
        String channelId = isBlank(request.channelId) ? request.channel.getChannelId() : request.channelId;
        if (isBlank(request.seriesId)) {
            return PlayerService.getInstance().get(request.account, request.channel, channelId);
        }
        return PlayerService.getInstance().get(request.account, request.channel, channelId, request.seriesId, request.seriesCategoryId);
    }

    private static void launchResolvedPlayback(PlaybackModeContext context, PlaybackRequest request, PlayerResponse response) {
        if (context.playerPathIsEmbedded()) {
            playEmbedded(response, context.useEmbeddedPlayerConfig());
            return;
        }
        if (isBlank(request.playerPath)) {
            if (context.useEmbeddedPlayerConfig()) {
                playEmbedded(response, true);
            } else {
                showErrorAlert(I18n.tr("autoNoDefaultPlayerConfigured"));
            }
            return;
        }
        com.uiptv.util.Platform.executeCommand(request.playerPath, response.getUrl());
    }

    private static void playEmbedded(PlayerResponse response, boolean enabled) {
        if (!enabled) {
            showErrorAlert(I18n.tr("autoEmbeddedPlayerNotEnabled"));
            return;
        }
        getPlayer().stopForReload();
        getPlayer().play(response);
    }

    static boolean isEmbeddedPlayerPath(String playerPath) {
        if (isBlank(playerPath)) {
            return false;
        }
        String normalized = playerPath.trim().toLowerCase();
        if (EMBEDDED_PLAYER_PATH.equalsIgnoreCase(normalized)) {
            return true;
        }
        return normalized.contains("embedded");
    }

    private static boolean isBrowserPlayerPath(String playerPath) {
        return WEB_BROWSER_PLAYER_PATH.equalsIgnoreCase(normalizePlayerPath(playerPath));
    }

    private static String normalizePlayerPath(String playerPath) {
        return playerPath == null ? "" : playerPath.trim();
    }

    private static String buildBrowserDirectPlaybackUrl(String url, Account account, Channel channel) {
        JSONObject payload = new JSONObject();
        String mode = determineMode(account);
        payload.put("mode", mode);
        payload.put("directUrl", url == null ? "" : url);
        payload.put("accountId", account == null ? "" : safe(account.getDbId()));
        payload.put("categoryId", channel == null ? "" : safe(channel.getCategoryId()));

        JSONObject channelJson = buildChannelJson(channel);
        payload.put("channel", channelJson);

        String bingeWatchToken = extractBingeWatchToken(url);
        if (!isBlank(bingeWatchToken)) {
            payload.put("bingeWatchToken", bingeWatchToken);
        }

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return ServerUrlUtil.getLocalServerUrl()
                + "/player.html?launch="
                + URLEncoder.encode(encoded, StandardCharsets.UTF_8)
                + "&v=20260309b";
    }

    private static String extractBingeWatchToken(String url) {
        if (isBlank(url) || !url.contains("/bingewatch.m3u8")) {
            return "";
        }
        try {
            String token = HttpUtilLike.getQueryParam(url, "token");
            return token == null ? "" : token.trim();
        } catch (Exception _) {
            return "";
        }
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

    private static String determineMode(Account account) {
        if (account == null || account.getAction() == null) {
            return DEFAULT_MODE;
        }
        String mode = safe(account.getAction().name()).toLowerCase();
        if ("itv".equals(mode) || "vod".equals(mode) || DEFAULT_MODE.equals(mode)) {
            return mode;
        }
        return DEFAULT_MODE;
    }

    private static JSONObject buildChannelJson(Channel channel) {
        JSONObject channelJson = new JSONObject();
        if (channel != null) {
            channelJson.put("dbId", safe(channel.getDbId()));
            channelJson.put("channelId", safe(channel.getChannelId()));
            channelJson.put("name", safe(channel.getName()));
            channelJson.put("logo", safe(channel.getLogo()));
            channelJson.put("cmd", safe(channel.getCmd()));
            channelJson.put("cmd_1", safe(channel.getCmd_1()));
            channelJson.put("cmd_2", safe(channel.getCmd_2()));
            channelJson.put("cmd_3", safe(channel.getCmd_3()));
            channelJson.put("drmType", safe(channel.getDrmType()));
            channelJson.put("drmLicenseUrl", safe(channel.getDrmLicenseUrl()));
            channelJson.put("clearKeysJson", safe(channel.getClearKeysJson()));
            channelJson.put("inputstreamaddon", safe(channel.getInputstreamaddon()));
            channelJson.put("manifestType", safe(channel.getManifestType()));
            channelJson.put("season", safe(channel.getSeason()));
            channelJson.put("episodeNum", safe(channel.getEpisodeNum()));
        }
        return channelJson;
    }

    private static final class HttpUtilLike {
        private static String getQueryParam(String url, String key) {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return "";
            }
            for (String part : query.split("&")) {
                int idx = part.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String name = java.net.URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
                if (key.equals(name)) {
                    return java.net.URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                }
            }
            return "";
        }
    }

    public static final class PlaybackRequest {
        private final Account account;
        private final Channel channel;
        private final String playerPath;
        private String categoryId = "";
        private String channelId = "";
        private String seriesId = "";
        private String seriesCategoryId = "";
        private boolean allowDrmBrowserFallback = true;
        private String errorPrefix = "Playback failed: ";

        public PlaybackRequest(Account account, Channel channel, String playerPath) {
            this.account = account;
            this.channel = channel;
            this.playerPath = playerPath;
        }

        public PlaybackRequest categoryId(String categoryId) {
            this.categoryId = categoryId == null ? "" : categoryId;
            return this;
        }

        public PlaybackRequest channelId(String channelId) {
            this.channelId = channelId == null ? "" : channelId;
            return this;
        }

        public PlaybackRequest series(String seriesId, String seriesCategoryId) {
            this.seriesId = seriesId == null ? "" : seriesId;
            this.seriesCategoryId = seriesCategoryId == null ? "" : seriesCategoryId;
            return this;
        }

        public PlaybackRequest allowDrmBrowserFallback(boolean allowDrmBrowserFallback) {
            this.allowDrmBrowserFallback = allowDrmBrowserFallback;
            return this;
        }

        public PlaybackRequest errorPrefix(String errorPrefix) {
            this.errorPrefix = isBlank(errorPrefix) ? "Playback failed: " : errorPrefix;
            return this;
        }
    }

    public record PlayerOption(String label, String playerPath) {
    }

    private record PlaybackModeContext(
            boolean useEmbeddedPlayerConfig,
            boolean browserIsDefaultConfig,
            boolean playerPathIsEmbedded,
            boolean playerPathIsBrowser,
            String mode
    ) {
    }
}
