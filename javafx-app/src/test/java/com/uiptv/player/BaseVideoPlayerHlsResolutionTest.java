package com.uiptv.player;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uiptv.model.Configuration;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.ui.EpgModeUI;
import com.uiptv.ui.RootApplication;
import com.uiptv.util.HttpUtil;
import com.uiptv.util.I18n;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseVideoPlayerHlsResolutionTest {
    @BeforeAll
    static void initJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void resolveHlsPlaylistChainStopsWhenPlaylistCycleIsDetected() throws Exception {
        TestPlayer player = runOnFxThread(TestPlayer::new);
        String masterUrl = "http://example.com/master.m3u8";
        String variantUrl = "http://example.com/variant.m3u8";

        HttpUtil.HttpResult master = new HttpUtil.HttpResult(200,
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant.m3u8\n",
                Map.of(),
                Map.of());
        HttpUtil.HttpResult variant = new HttpUtil.HttpResult(200,
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nmaster.m3u8\n",
                Map.of(),
                Map.of());

        ConfigurationService configurationService = mock(ConfigurationService.class);
        try (MockedStatic<ConfigurationService> configurationServiceStatic = mockStatic(ConfigurationService.class);
             MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            when(configurationService.isResolveChainAndDeepRedirectsEnabled(any())).thenReturn(true);
            when(configurationService.isVlcHttpUserAgentEnabled()).thenReturn(true);
            httpUtil.when(() -> HttpUtil.sendRequest(eq(masterUrl), anyMap(), eq("GET")))
                    .thenReturn(master);
            httpUtil.when(() -> HttpUtil.sendRequest(eq(variantUrl), anyMap(), eq("GET")))
                    .thenReturn(variant);

            String resolved = player.resolve(masterUrl);

            assertEquals(masterUrl, resolved);
            httpUtil.verify(() -> HttpUtil.sendRequest(eq(masterUrl), anyMap(), eq("GET")));
            httpUtil.verify(() -> HttpUtil.sendRequest(eq(variantUrl), anyMap(), eq("GET")));
        }
    }

    @Test
    void resolveHlsPlaylistChainSkipsResolutionWhenFeatureIsDisabled() throws Exception {
        TestPlayer player = runOnFxThread(TestPlayer::new);
        String uri = "http://example.com/master.m3u8";

        ConfigurationService configurationService = mock(ConfigurationService.class);
        try (MockedStatic<ConfigurationService> configurationServiceStatic = mockStatic(ConfigurationService.class);
             MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            when(configurationService.isResolveChainAndDeepRedirectsEnabled(any())).thenReturn(false);

            String resolved = player.resolve(uri);

            assertEquals(uri, resolved);
            httpUtil.verifyNoInteractions();
        }
    }

    @Test
    void layoutModeButtonDoesNotLookSelectedWhenWideViewIsSaved() throws Exception {
        LayoutButtonState state = runOnFxThread(() -> {
            Configuration configuration = new Configuration();
            configuration.setEmbeddedPlayer(true);
            configuration.setWideView(true);

            ConfigurationService configurationService = mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                when(configurationService.read()).thenReturn(configuration);

                TestPlayer player = new TestPlayer();
                return new LayoutButtonState(
                        player.layoutButtonVisible(),
                        player.layoutButtonHasStyle("player-layout-mode-button"),
                        player.layoutButtonHasStyle("player-icon-button-active"),
                        player.layoutButtonFocusTraversable(),
                        player.layoutIconContent(),
                        player.layoutButtonAccessibleText()
                );
            }
        });

        assertTrue(state.visible());
        assertTrue(state.layoutStyle());
        assertFalse(state.activeStyle());
        assertFalse(state.focusTraversable());
        assertEquals("M3 5H21V19H3V5ZM5 7V17H11V7H5ZM13 7V17H19V7H13Z", state.iconContent());
    }

    @Test
    void layoutModeButtonTogglesWideViewPreference() throws Exception {
        LayoutButtonState state = runOnFxThread(() -> {
            Configuration configuration = new Configuration();
            configuration.setEmbeddedPlayer(true);
            List<Boolean> savedWideViews = new ArrayList<>();

            ConfigurationService configurationService = mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                when(configurationService.read()).thenReturn(configuration);

                TestPlayer player = new TestPlayer();
                player.onLayoutSave(wideView -> {
                    savedWideViews.add(wideView);
                    configuration.setWideView(wideView);
                });
                player.fireLayoutModeButton();
                assertEquals(List.of(true), savedWideViews);
                player.fireLayoutModeButton();
                assertEquals(List.of(true, false), savedWideViews);
                return new LayoutButtonState(
                        player.layoutButtonVisible(),
                        player.layoutButtonHasStyle("player-layout-mode-button"),
                        player.layoutButtonHasStyle("player-icon-button-active"),
                        player.layoutButtonFocusTraversable(),
                        player.layoutIconContent(),
                        player.layoutButtonAccessibleText()
                );
            }
        });

        assertTrue(state.visible());
        assertEquals("M3 5H21V19H3V5ZM5 7V17H14V7H5ZM16 7V17H19V7H16Z", state.iconContent());
    }

    @Test
    void layoutModeButtonSitsNextToZoomControl() throws Exception {
        boolean adjacent = runOnFxThread(() -> {
            ConfigurationService configurationService = mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                when(configurationService.read()).thenReturn(new Configuration());
                return new TestPlayer().layoutButtonIsImmediatelyBeforeAspectRatioButton();
            }
        });

        assertTrue(adjacent);
    }

    @Test
    void doubleClickOnControlBarDoesNotToggleFullscreen() throws Exception {
        int fullscreenToggles = runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            Event.fireEvent(player.btnFullscreen, mouseClick(player.btnFullscreen, 2));
            return player.fullscreenToggleCount;
        });

        assertEquals(0, fullscreenToggles);
    }

    @Test
    void doubleClickOnPlayerStillTogglesFullscreen() throws Exception {
        int fullscreenToggles = runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            Event.fireEvent(player.playerContainer, mouseClick(player.playerContainer, 2));
            return player.fullscreenToggleCount;
        });

        assertEquals(1, fullscreenToggles);
    }

    @Test
    void controlBarRemainsVisibleWhilePointerIsInsideIt() throws Exception {
        boolean visible = runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            player.playerContainer.setVisible(true);
            player.playerContainer.setManaged(true);
            player.controlsContainer.setVisible(true);

            Event.fireEvent(player.controlsContainer,
                    mouseEvent(MouseEvent.MOUSE_ENTERED, player.controlsContainer, 1));
            player.idleTimer.getOnFinished().handle(null);
            return player.controlsContainer.isVisible();
        });

        assertTrue(visible);
    }

    @Test
    void adjacentChannelNavigationKeepsControlBarVisible() throws Exception {
        boolean visible = runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            player.playerContainer.setVisible(true);
            player.playerContainer.setManaged(true);
            player.controlsContainer.setVisible(true);

            try {
                PlayerChannelNavigation.setQueue(new PlayerChannelNavigation.Queue(
                        List.of(
                                () -> player.play(new PlayerResponse("http://example.com/first")),
                                () -> player.play(new PlayerResponse("http://example.com/second"))),
                        List.of("First", "Second"),
                        0));
                player.btnNextChannel.fire();
                return player.controlsContainer.isVisible();
            } finally {
                PlayerChannelNavigation.clear();
            }
        });

        assertTrue(visible);
    }

    @Test
    void guideButtonIsReadyForLivePlaybackBeforeGuideDetailsArrive() throws Exception {
        runOnFxThread(() -> {
            try (MockedStatic<EpgModeUI> epgMode = mockStatic(EpgModeUI.class)) {
                epgMode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);
                TestPlayer player = new TestPlayer();
                Account liveAccount = new Account();
                liveAccount.setAction(Account.AccountAction.itv);
                player.updateChannelInfo(new Channel(), liveAccount);

                assertTrue(player.btnGuide.isVisible());
                assertTrue(player.btnGuide.isManaged());
                player.btnGuide.fire();
                assertTrue(player.guideOverlay.isVisible(), "Guide should explain when details are not ready");

                epgMode.when(EpgModeUI::isFeatureEnabled).thenReturn(false);
                player.guideOverlay.updateAvailability();
                assertFalse(player.btnGuide.isVisible());
                assertFalse(player.guideOverlay.isVisible());
                epgMode.when(EpgModeUI::isFeatureEnabled).thenReturn(true);

                Account vodAccount = new Account();
                vodAccount.setAction(Account.AccountAction.vod);
                player.updateChannelInfo(new Channel(), vodAccount);
                assertFalse(player.btnGuide.isVisible());
                assertFalse(player.btnGuide.isManaged());
            }
            return null;
        });
    }

    @Test
    void liveChannelHasFixedProgressAndLiveLabelEvenWhenVlcReportsDuration() throws Exception {
        runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            Account account = new Account();
            account.setAction(Account.AccountAction.itv);
            player.updateChannelInfo(new Channel(), account);
            player.updatePlaybackTimeUi(250_000, 260_000);

            assertEquals(I18n.tr("playerLive"), player.timeLabel.getText());
            assertTrue(player.timeSlider.isDisabled());
            assertEquals(1.0, player.timeSlider.getValue());

            account.setAction(Account.AccountAction.vod);
            player.updateChannelInfo(new Channel(), account);
            player.updatePlaybackTimeUi(125_000, 250_000);
            assertFalse(player.timeSlider.isDisabled());
            assertEquals(0.5, player.timeSlider.getValue());
            assertEquals("02:05 / 04:10", player.timeLabel.getText());
            return null;
        });
    }

    @Test
    void fullscreenTemporarilySuppressesPrimaryStageAlwaysOnTop() throws Exception {
        runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            Stage primaryStage = mock(Stage.class);
            when(primaryStage.isAlwaysOnTop()).thenReturn(true);

            try (MockedStatic<RootApplication> rootApplication = mockStatic(RootApplication.class)) {
                rootApplication.when(RootApplication::getPrimaryStage).thenReturn(primaryStage);

                player.suppressPrimaryStageAlwaysOnTopForVideoOverlay();
                verify(primaryStage).setAlwaysOnTop(false);

                player.restorePrimaryStageAlwaysOnTopAfterVideoOverlay();
                verify(primaryStage).setAlwaysOnTop(true);
            }

            return null;
        });
    }

    @Test
    void pipOverlayStageIsOwnedByPrimaryStageAndAlwaysOnTop() throws Exception {
        runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            Stage overlayStage = mock(Stage.class);
            Stage primaryStage = mock(Stage.class);
            when(overlayStage.isShowing()).thenReturn(false);

            try (MockedStatic<RootApplication> rootApplication = mockStatic(RootApplication.class)) {
                rootApplication.when(RootApplication::getPrimaryStage).thenReturn(primaryStage);

                player.configureOverlayStage(overlayStage);
            }

            verify(overlayStage).initOwner(primaryStage);
            verify(overlayStage).setAlwaysOnTop(true);
            return null;
        });
    }

    @Test
    void exitFullscreenDoesNotReattachPlayerContainerTwice() throws Exception {
        runOnFxThread(() -> {
            TestPlayer player = new TestPlayer();
            StackPane originalParent = new StackPane(player.playerContainer);
            player.originalParent = originalParent;
            player.originalIndex = 0;
            player.fullscreenRoot = new StackPane();
            player.fullscreenStage = new Stage();

            player.exitFullscreen();

            assertEquals(1, originalParent.getChildren().size());
            assertEquals(player.playerContainer, originalParent.getChildren().getFirst());
            return null;
        });
    }

    private static <T> T runOnFxThread(FxTestSupport.FxCallable<T> task) throws Exception {
        return FxTestSupport.runOnFxThread(task);
    }

    private static MouseEvent mouseClick(Node target, int clickCount) {
        return mouseEvent(MouseEvent.MOUSE_CLICKED, target, clickCount);
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> eventType,
                                         Node target,
                                         int clickCount) {
        return new MouseEvent(
                eventType,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                clickCount,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        );
    }

    private record LayoutButtonState(
            boolean visible,
            boolean layoutStyle,
            boolean activeStyle,
            boolean focusTraversable,
            String iconContent,
            String accessibleText
    ) {
    }

    private static final class TestPlayer extends BaseVideoPlayer {
        private Consumer<Boolean> layoutSaveHandler = _ -> {};
        private int fullscreenToggleCount;

        @Override public void toggleFullscreen() {
            fullscreenToggleCount++;
        }

        @Override protected javafx.scene.Node getVideoView() { return null; }
        @Override protected void playMedia(String uri) { /* Test stub: playback is not exercised here. */ }
        @Override protected void stopMedia() { /* Test stub: playback is not exercised here. */ }
        @Override protected void disposeMedia() { /* Test stub: playback is not exercised here. */ }
        @Override protected void setVolume(double volume) { /* Test stub: playback is not exercised here. */ }
        @Override protected void setMute(boolean mute) { /* Test stub: playback is not exercised here. */ }
        @Override protected void seek(float position) { /* Test stub: playback is not exercised here. */ }
        @Override protected void seekBySeconds(int deltaSeconds) { /* Test stub: playback is not exercised here. */ }
        @Override protected void updateVideoSize() { /* Test stub: layout is not exercised here. */ }
        @Override protected void pauseMedia() { /* Test stub: playback is not exercised here. */ }
        @Override protected void resumeMedia() { /* Test stub: playback is not exercised here. */ }
        @Override protected boolean isPlaying() { return false; }
        @Override public com.uiptv.player.api.VideoPlayerInterface.PlayerType getType() { return com.uiptv.player.api.VideoPlayerInterface.PlayerType.DUMMY; }
        @Override protected void saveWideViewPreferenceAsync(boolean wideView) { layoutSaveHandler.accept(wideView); }

        String resolve(String uri) {
            return resolveHlsPlaylistChain(uri);
        }

        void onLayoutSave(Consumer<Boolean> layoutSaveHandler) {
            this.layoutSaveHandler = layoutSaveHandler == null ? _ -> {} : layoutSaveHandler;
        }

        boolean layoutButtonVisible() {
            return btnLayoutMode.isVisible();
        }

        boolean layoutButtonHasStyle(String styleClass) {
            return btnLayoutMode.getStyleClass().contains(styleClass);
        }

        boolean layoutButtonFocusTraversable() {
            return btnLayoutMode.isFocusTraversable();
        }

        String layoutIconContent() {
            return layoutModeIcon.getContent();
        }

        String layoutButtonAccessibleText() {
            return btnLayoutMode.getAccessibleText();
        }

        void fireLayoutModeButton() {
            btnLayoutMode.fire();
        }

        boolean layoutButtonIsImmediatelyBeforeAspectRatioButton() {
            if (!(btnLayoutMode.getParent() instanceof javafx.scene.layout.HBox buttonRow)) {
                return false;
            }
            return buttonRow.getChildren().indexOf(btnLayoutMode) + 1
                    == buttonRow.getChildren().indexOf(btnAspectRatio);
        }

        void configureOverlayStage(Stage stage) {
            configureVideoOverlayStage(stage);
        }

    }
}
