package com.uiptv.player;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

import java.lang.reflect.Method;
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
    void freshSessionStartsWhileOldStopIsBlockedAndReleaseWaitsForStop() throws Exception {
        var firstFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var secondFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        EmbeddedMediaPlayer first = firstFactory.mediaPlayers().newEmbeddedMediaPlayer();
        EmbeddedMediaPlayer second = secondFactory.mediaPlayers().newEmbeddedMediaPlayer();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstControls = first.controls();
        doAnswer(_ -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return null; })
                .when(firstControls).stop();
        AtomicInteger factories = new AtomicInteger();
        VlcVideoPlayer player = runOnFxThread(() -> new VlcVideoPlayer(() -> factories.getAndIncrement() == 0 ? firstFactory : secondFactory));
        try {
            player.playbackReady().toCompletableFuture().get(5, TimeUnit.SECONDS);
            runOnFxThread(() -> { player.stopForReload(); player.showLoading(); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(2, factories.get());
            verify(first, never()).release();
            var started = new CountDownLatch(1);
            when(second.media().play(anyString(), any(String[].class))).thenAnswer(_ -> { started.countDown(); return true; });
            runOnFxThread(() -> { player.play(new PlayerResponse("file:///test-stream.ts")); return null; });
            assertTrue(started.await(2, TimeUnit.SECONDS), "New stream must not wait for the old stop");
            release.countDown();
            verify(first, timeout(5000)).release();
            verify(firstFactory, timeout(5000)).release();
        } finally {
            release.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void switchingDuringBlockedStartDoesNotReleaseItOrDelayNewPlayer() throws Exception {
        var firstFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var secondFactory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        EmbeddedMediaPlayer first = firstFactory.mediaPlayers().newEmbeddedMediaPlayer();
        var entered = new CountDownLatch(1);
        var finishStart = new CountDownLatch(1);
        var stopIssued = new CountDownLatch(1);
        when(first.media().play(anyString(), any(String[].class))).thenAnswer(_ -> {
            entered.countDown(); assertTrue(finishStart.await(10, TimeUnit.SECONDS)); return true;
        });
        var firstControls = first.controls();
        doAnswer(_ -> { stopIssued.countDown(); return null; }).when(firstControls).stop();
        AtomicInteger factories = new AtomicInteger();
        VlcVideoPlayer player = runOnFxThread(() -> new VlcVideoPlayer(() -> factories.getAndIncrement() == 0 ? firstFactory : secondFactory));
        try {
            runOnFxThread(() -> { player.play(new PlayerResponse("file:///test-stream.ts")); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runOnFxThread(() -> { player.stopForReload(); return null; });
            assertTrue(stopIssued.await(5, TimeUnit.SECONDS));
            player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(first, never()).release();
            finishStart.countDown();
            verify(first, timeout(5000)).release();
        } finally {
            finishStart.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void slowNativeInitializationDoesNotBlockClosingOrPreparingAnotherSession() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var first = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var second = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        AtomicInteger factories = new AtomicInteger();
        VlcVideoPlayer player = runOnFxThread(() -> new VlcVideoPlayer(() -> {
            assertFalse(javafx.application.Platform.isFxApplicationThread());
            if (factories.getAndIncrement() == 0) {
                entered.countDown();
                try { assertTrue(finish.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return first;
            }
            return second;
        }));
        try {
            var oldReady = player.playbackReady().toCompletableFuture();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runOnFxThread(() -> { player.stop(); player.showLoading(); return null; });
            player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS);
            finish.countDown();
            oldReady.get(5, TimeUnit.SECONDS);
            verify(first, timeout(5000)).release();
        } finally {
            finish.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void stuckSessionsAreBoundedAndCapacityReturnsAfterCleanup() throws Exception {
        var release = new CountDownLatch(1);
        var factories = new java.util.concurrent.CopyOnWriteArrayList<MediaPlayerFactory>();
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> {
            var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
            var controls = factory.mediaPlayers().newEmbeddedMediaPlayer().controls();
            doAnswer(_ -> { assertTrue(release.await(10, TimeUnit.SECONDS)); return null; }).when(controls).stop();
            factories.add(factory);
            return factory;
        }));
        try {
            for (int i = 0; i < 4; i++) {
                player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS);
                runOnFxThread(() -> { player.stop(); return null; });
            }
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(4, factories.size(), "Do not allocate unlimited native players for permanently stuck streams");
            release.countDown();
            for (var factory : factories) verify(factory, timeout(5000)).release();
            player.playbackReady().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(5, factories.size());
        } finally {
            release.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void closingDuringReloadCannotReopenThePlayerWhenPreparationEventuallyCompletes() throws Exception {
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> {
            entered.countDown();
            try { assertTrue(finish.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return factory;
        }));
        try {
            runOnFxThread(() -> {
                player.currentMediaUri = "file:///test-stream.ts";
                player.refreshAndPlay();
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            runOnFxThread(() -> { player.stop(); return null; });
            finish.countDown();
            verify(factory, timeout(5000)).release();
            runOnFxThread(() -> {
                assertFalse(player.getPlayerContainer().isVisible());
                return null;
            });
            verify(factory.mediaPlayers().newEmbeddedMediaPlayer().media(), never()).play(anyString(), any(String[].class));
        } finally {
            finish.countDown();
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void loadingAndFailureAreVisibleBeforeAStreamUrlExists() throws Exception {
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS)));
        try {
            runOnFxThread(() -> {
                player.stop();
                player.showLoading();
                assertTrue(player.getPlayerContainer().isVisible());
                assertTrue(player.getPlayerContainer().isManaged());
                assertTrue(player.loadingSpinner.isVisible());
                player.showPlaybackError();
                assertFalse(player.loadingSpinner.isVisible());
                assertTrue(player.errorLabel.isVisible());
                assertTrue(player.controlsContainer.isVisible());
                return null;
            });
        } finally { runOnFxThread(() -> { player.disposePlayer(); return null; }); }
    }

    @Test
    void unexpectedLiveFinishedEventSchedulesFreshPlaybackRecovery() throws Exception {
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var nativePlayer = mock(EmbeddedMediaPlayer.class, RETURNS_DEEP_STUBS);
        when(factory.mediaPlayers().newEmbeddedMediaPlayer()).thenReturn(nativePlayer);
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> factory));
        try {
            player.playbackReady().toCompletableFuture().get(5, TimeUnit.SECONDS);
            runOnFxThread(() -> {
                player.currentAccount = liveAccount();
                player.currentChannel = liveChannel();
                player.isRetrying.set(true);
                invokeVlcEvent(player, "finished", nativePlayer);
                return null;
            });
            FxTestSupport.waitForFxEvents();
            assertEquals(1, player.retryCount);
            assertTrue(player.loadingSpinner.isVisible());
            assertFalse(player.errorLabel.isVisible());
        } finally {
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void unexpectedLiveErrorEventSchedulesFreshPlaybackRecovery() throws Exception {
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var nativePlayer = mock(EmbeddedMediaPlayer.class, RETURNS_DEEP_STUBS);
        when(factory.mediaPlayers().newEmbeddedMediaPlayer()).thenReturn(nativePlayer);
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> factory));
        try {
            player.playbackReady().toCompletableFuture().get(5, TimeUnit.SECONDS);
            runOnFxThread(() -> {
                player.currentAccount = liveAccount();
                player.currentChannel = liveChannel();
                player.isRetrying.set(true);
                invokeVlcEvent(player, "error", nativePlayer);
                return null;
            });
            FxTestSupport.waitForFxEvents();
            assertEquals(1, player.retryCount);
            assertTrue(player.loadingSpinner.isVisible());
            assertFalse(player.errorLabel.isVisible());
        } finally {
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    @Test
    void onDemandFinishedEventDoesNotScheduleLiveRecovery() throws Exception {
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var nativePlayer = mock(EmbeddedMediaPlayer.class, RETURNS_DEEP_STUBS);
        when(factory.mediaPlayers().newEmbeddedMediaPlayer()).thenReturn(nativePlayer);
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> factory));
        try {
            player.playbackReady().toCompletableFuture().get(5, TimeUnit.SECONDS);
            runOnFxThread(() -> {
                Account account = liveAccount();
                account.setAction(Account.AccountAction.vod);
                player.currentAccount = account;
                player.currentChannel = liveChannel();
                player.isRetrying.set(true);
                invokeVlcEvent(player, "finished", nativePlayer);
                return null;
            });
            FxTestSupport.waitForFxEvents();
            assertEquals(0, player.retryCount);
            assertFalse(player.loadingSpinner.isVisible());
        } finally {
            runOnFxThread(() -> { player.disposePlayer(); return null; });
        }
    }

    private static Account liveAccount() {
        Account account = new Account();
        account.setAction(Account.AccountAction.itv);
        return account;
    }

    private static Channel liveChannel() {
        Channel channel = new Channel();
        channel.setChannelId("live-channel");
        return channel;
    }

    private static void invokeVlcEvent(VlcVideoPlayer player, String event, MediaPlayer nativePlayer) throws Exception {
        Method method = VlcVideoPlayer.class.getDeclaredMethod("createMediaPlayerEvents");
        method.setAccessible(true);
        MediaPlayerEventAdapter listener = (MediaPlayerEventAdapter) method.invoke(player);
        if ("finished".equals(event)) {
            listener.finished(nativePlayer);
        } else {
            listener.error(nativePlayer);
        }
    }

    @Test
    void startupDeadlineDetachesStalledPlayerAndLeavesErrorVisible() throws Exception {
        var factory = mock(MediaPlayerFactory.class, RETURNS_DEEP_STUBS);
        var player = runOnFxThread(() -> new VlcVideoPlayer(() -> factory));
        try {
            player.playbackReady().toCompletableFuture().get(5, TimeUnit.SECONDS);
            runOnFxThread(() -> {
                player.showLoading();
                var field = BaseVideoPlayer.class.getDeclaredField("startupTimeout");
                field.setAccessible(true);
                var timer = (javafx.animation.PauseTransition) field.get(player);
                timer.getOnFinished().handle(new javafx.event.ActionEvent());
                assertTrue(player.errorLabel.isVisible());
                assertTrue(player.getPlayerContainer().isVisible());
                assertFalse(player.loadingSpinner.isVisible());
                return null;
            });
            verify(factory, timeout(5000)).release();
        } finally { runOnFxThread(() -> { player.disposePlayer(); return null; }); }
    }
}
