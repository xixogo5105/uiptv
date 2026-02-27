package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import javafx.scene.Node;
import javafx.scene.Scene;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public final class PlaybackUIService {
    private PlaybackUIService() {
    }

    public static void play(Node source, PlaybackRequest request) {
        if (source == null || request == null || request.account == null || request.channel == null) {
            return;
        }

        boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
        boolean playerPathIsEmbedded = request.playerPath != null && request.playerPath.toLowerCase().contains("embedded");
        String mode = request.account.getAction() == null ? "itv" : request.account.getAction().name();

        if (request.allowDrmBrowserFallback && PlayerService.getInstance().isDrmProtected(request.channel)) {
            String configured = ConfigurationService.getInstance().read().getServerPort();
            String serverPort = isBlank(configured) ? "8888" : configured.trim();
            boolean confirmed = showConfirmationAlert("This channel has drm protected contents and will only run in the browser. It requires the local server to run on port " + serverPort + ". Do you want me to open a browser and try running this channel?");
            if (!confirmed) {
                return;
            }
            if (!RootApplication.ensureServerForWebPlayback()) {
                showErrorAlert("Unable to start local web server for DRM playback.");
                return;
            }
            String browserUrl = PlayerService.getInstance().buildDrmBrowserPlaybackUrl(request.account, request.channel, request.categoryId, mode);
            RootApplication.openInBrowser(browserUrl);
            return;
        }

        Scene scene = source.getScene();
        if (scene != null) {
            scene.setCursor(javafx.scene.Cursor.WAIT);
        }

        new Thread(() -> {
            try {
                String channelId = isBlank(request.channelId) ? request.channel.getChannelId() : request.channelId;
                PlayerResponse response;
                if (isBlank(request.seriesId)) {
                    response = PlayerService.getInstance().get(request.account, request.channel, channelId);
                } else {
                    response = PlayerService.getInstance().get(request.account, request.channel, channelId, request.seriesId, request.seriesCategoryId);
                }
                response.setFromChannel(request.channel, request.account);
                String evaluatedStreamUrl = response.getUrl();

                runLater(() -> {
                    if (playerPathIsEmbedded) {
                        if (useEmbeddedPlayerConfig) {
                            getPlayer().stopForReload();
                            getPlayer().play(response);
                        } else {
                            showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                        }
                    } else if (isBlank(request.playerPath) && useEmbeddedPlayerConfig) {
                        getPlayer().stopForReload();
                        getPlayer().play(response);
                    } else if (isBlank(request.playerPath)) {
                        showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                    } else {
                        com.uiptv.util.Platform.executeCommand(request.playerPath, evaluatedStreamUrl);
                    }
                });
            } catch (Exception e) {
                runLater(() -> showErrorAlert(request.errorPrefix + e.getMessage()));
            } finally {
                if (scene != null) {
                    runLater(() -> scene.setCursor(javafx.scene.Cursor.DEFAULT));
                }
            }
        }).start();
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
}
