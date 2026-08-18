package com.uiptv.player;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.ui.RootApplication;
import com.uiptv.util.HttpUtil;
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
