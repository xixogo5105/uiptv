package com.uiptv.player;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.HttpUtil;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseVideoPlayerHlsResolutionTest {
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);
    private static final long FX_WAIT_TIMEOUT_SECONDS = 3L;

    @BeforeAll
    static void initJavaFx() throws Exception {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException e) {
                if (!String.valueOf(e.getMessage()).contains("Toolkit already initialized")) {
                    throw e;
                }
                latch.countDown();
            }
            if (!latch.await(FX_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX platform failed to start");
            }
        }
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

        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.isResolveChainAndDeepRedirectsEnabled(Mockito.any())).thenReturn(true);
            Mockito.when(configurationService.isVlcHttpUserAgentEnabled()).thenReturn(true);
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.eq(masterUrl), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(master);
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.eq(variantUrl), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(variant);

            String resolved = player.resolve(masterUrl);

            assertEquals(masterUrl, resolved);
            httpUtil.verify(() -> HttpUtil.sendRequest(Mockito.eq(masterUrl), Mockito.anyMap(), Mockito.eq("GET")));
            httpUtil.verify(() -> HttpUtil.sendRequest(Mockito.eq(variantUrl), Mockito.anyMap(), Mockito.eq("GET")));
        }
    }

    @Test
    void resolveHlsPlaylistChainSkipsResolutionWhenFeatureIsDisabled() throws Exception {
        TestPlayer player = runOnFxThread(TestPlayer::new);
        String uri = "http://example.com/master.m3u8";

        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.isResolveChainAndDeepRedirectsEnabled(Mockito.any())).thenReturn(false);

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

            ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                Mockito.when(configurationService.read()).thenReturn(configuration);

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

            ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                Mockito.when(configurationService.read()).thenReturn(configuration);

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
            ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
            try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
                configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
                Mockito.when(configurationService.read()).thenReturn(new Configuration());
                return new TestPlayer().layoutButtonIsImmediatelyBeforeAspectRatioButton();
            }
        });

        assertTrue(adjacent);
    }

    private static <T> T runOnFxThread(FxCallable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(FX_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for JavaFX task");
        }
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception e) {
                throw e;
            }
            throw new RuntimeException(t);
        }
        return result.get();
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
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
    }
}
