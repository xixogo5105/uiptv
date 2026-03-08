package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.ServerUrlUtil;
import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.List;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public final class PlaybackUIService {
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

        if (handleBrowserPlayback(context)) return;
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

    private static PlaybackModeContext buildPlaybackModeContext(Configuration configuration, PlaybackRequest request) {
        boolean useEmbeddedPlayerConfig = configuration != null && configuration.isEmbeddedPlayer();
        boolean browserIsDefaultConfig = configuration != null
                && WEB_BROWSER_PLAYER_PATH.equalsIgnoreCase(String.valueOf(configuration.getDefaultPlayerPath()).trim());
        boolean playerPathIsEmbedded = isEmbeddedPlayerPath(request.playerPath);
        boolean playerPathIsBrowser = WEB_BROWSER_PLAYER_PATH.equalsIgnoreCase(String.valueOf(request.playerPath).trim());
        String mode = request.account.getAction() == null ? "itv" : request.account.getAction().name();
        return new PlaybackModeContext(useEmbeddedPlayerConfig, browserIsDefaultConfig, playerPathIsEmbedded, playerPathIsBrowser, mode);
    }

    private static boolean handleBrowserPlayback(PlaybackModeContext context) {
        if (!context.playerPathIsBrowser()) {
            return false;
        }
        return openBrowserPlayback(context.mode(), I18n.tr("autoUnableToStartLocalWebServerForBrowserPlayback"), null);
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
        if (request != null) {
            String browserUrl = PlayerService.getInstance()
                    .buildDrmBrowserPlaybackUrl(request.account, request.channel, request.categoryId, mode);
            ServerUrlUtil.openInBrowser(browserUrl);
        }
        return true;
    }

    private static PlayerResponse resolvePlayerResponse(PlaybackRequest request) throws Exception {
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
