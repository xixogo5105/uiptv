package com.uiptv.player;

import com.uiptv.model.PlayerResponse;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VlcPlaybackLifecycleTest extends DbBackedUiTest {
    @BeforeAll static void init() throws Exception { FxTestSupport.initJavaFx(); }

    @Test
    void newStreamWaitsForNativeReleaseAndPreparesPlayerBeforeTokenResolution() throws Exception {
        var firstFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var secondFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        EmbeddedMediaPlayer first = firstFactory.mediaPlayers().newEmbeddedMediaPlayer();
        EmbeddedMediaPlayer second = secondFactory.mediaPlayers().newEmbeddedMediaPlayer();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var controls = first.controls();
        doAnswer(_ -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; })
                .when(controls).stop();
        AtomicInteger factories = new AtomicInteger();
        VlcVideoPlayer player = runOnFxThread(() -> new VlcVideoPlayer(() -> factories.getAndIncrement() == 0 ? firstFactory : secondFactory));
        try {
            runOnFxThread(() -> { player.stopForReload(); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var ready = player.playbackReady().toCompletableFuture();
            assertFalse(ready.isDone(), "A new token must not be requested during old-stream teardown");
            assertEquals(1, factories.get());
            assertTrue(runOnFxThread(() -> true), "Native teardown must not block JavaFX");
            release.countDown();
            ready.get(5, TimeUnit.SECONDS);
            assertEquals(2, factories.get(), "New native player is ready before requesting the provider URL");
            verify(first).release(); verify(firstFactory).release();
            var started = new CountDownLatch(1);
            when(second.media().play(anyString(), any(String[].class))).thenAnswer(_ -> { started.countDown(); return true; });
            runOnFxThread(() -> { player.play(new PlayerResponse("file:///test-stream.ts")); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void switchingDuringBlockedNativeStartNeverReleasesPlayerConcurrently() throws Exception {
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        EmbeddedMediaPlayer nativePlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();
        var entered = new CountDownLatch(1);
        var finishStart = new CountDownLatch(1);
        when(nativePlayer.media().play(anyString(), any(String[].class))).thenAnswer(_ -> {
            entered.countDown(); assertTrue(finishStart.await(5, TimeUnit.SECONDS)); return true;
        });
        VlcVideoPlayer player = runOnFxThread(() -> new VlcVideoPlayer(() -> factory));
        try {
            runOnFxThread(() -> { player.play(new PlayerResponse("file:///test-stream.ts")); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runOnFxThread(() -> { player.stopForReload(); return null; });
            var ready = player.playbackReady().toCompletableFuture();
            assertThrows(java.util.concurrent.TimeoutException.class, () -> ready.get(200, TimeUnit.MILLISECONDS),
                    "Release and next-player preparation must wait for the native start call to return");
            verify(nativePlayer, never()).release();
            finishStart.countDown();
            ready.get(5, TimeUnit.SECONDS);
            verify(nativePlayer).release();
        } finally {
            finishStart.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }
}
