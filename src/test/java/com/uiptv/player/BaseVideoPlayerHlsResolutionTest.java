package com.uiptv.player;

import com.uiptv.service.ConfigurationService;
import com.uiptv.util.HttpUtil;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseVideoPlayerHlsResolutionTest {
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

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
            if (!latch.await(5, TimeUnit.SECONDS)) {
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
            Mockito.when(configurationService.isResolveChainAndDeepRedirectsEnabled()).thenReturn(true);
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
            Mockito.when(configurationService.isResolveChainAndDeepRedirectsEnabled()).thenReturn(false);

            String resolved = player.resolve(uri);

            assertEquals(uri, resolved);
            httpUtil.verifyNoInteractions();
        }
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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

    private static final class TestPlayer extends BaseVideoPlayer {
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
        @Override public com.uiptv.api.VideoPlayerInterface.PlayerType getType() { return com.uiptv.api.VideoPlayerInterface.PlayerType.DUMMY; }

        String resolve(String uri) {
            return resolveHlsPlaylistChain(uri);
        }
    }
}
