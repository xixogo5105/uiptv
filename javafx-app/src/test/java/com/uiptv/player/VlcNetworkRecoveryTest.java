package com.uiptv.player;

import com.uiptv.model.PlayerResponse;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in smoke test: installed VLC 3.x plus a generated MPEG-TS fixture, no provider credentials. */
@EnabledIfSystemProperty(named = "uiptv.vlc.fixture", matches = ".+")
class VlcNetworkRecoveryTest extends DbBackedUiTest {
    @Test
    void threeStalledHttpStreamsThenGoodVideoAndHttpError() throws Exception {
        FxTestSupport.initJavaFx();
        byte[] video = Files.readAllBytes(Path.of(System.getProperty("uiptv.vlc.fixture")));
        List<Socket> sockets = new CopyOnWriteArrayList<>();
        var releaseStalls = new CountDownLatch(1);
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try (var server = new ServerSocket(0, 20, java.net.InetAddress.getLoopbackAddress())) {
            var stalledRequests = new java.util.concurrent.atomic.AtomicInteger();
            workers.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        sockets.add(socket);
                        workers.submit(() -> {
                            try (socket) {
                                var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                                String request = input.readLine();
                                // Drain request headers before deciding how this test server responds.
                                for (String line; (line = input.readLine()) != null && !line.isEmpty();) {
                                    // Header contents are intentionally irrelevant to this fixture.
                                }
                                if (request.contains("/stall")) {
                                    stalledRequests.incrementAndGet();
                                    releaseStalls.await(40, TimeUnit.SECONDS);
                                } else if (request.contains("/good")) {
                                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: " + video.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                                    socket.getOutputStream().write(video);
                                } else {
                                    socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                                }
                            } catch (Exception error) {
                                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                            }
                        });
                    } catch (Exception _) { return; }
                }
            });
            String base = "http://127.0.0.1:" + server.getLocalPort();
            VlcVideoPlayer player = runOnFxThread(VlcVideoPlayer::new);
            Stage stage = runOnFxThread(() -> {
                Stage window = new Stage();
                window.setScene(new Scene(new StackPane(player.getPlayerContainer()), 640, 400));
                window.show();
                return window;
            });
            try {
                player.playbackReady().toCompletableFuture().get(10, TimeUnit.SECONDS);
                for (int attempt = 1; attempt <= 3; attempt++) {
                    int expected = attempt;
                    runOnFxThread(() -> { player.showLoading(); player.play(new PlayerResponse(base + "/stall.ts")); return null; });
                    await(() -> stalledRequests.get() >= expected, 8000);
                    long close = System.nanoTime();
                    runOnFxThread(() -> { player.stop(); return null; });
                    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - close) < 1000, "Close must stay responsive");
                }
                long start = System.nanoTime();
                runOnFxThread(() -> { player.showLoading(); player.play(new PlayerResponse(base + "/good.ts")); return null; });
                await(() -> {
                    try { return runOnFxThread(() -> {
                        var image = ((ImageView) player.getVideoView()).getImage();
                        return image != null && !player.loadingSpinner.isVisible()
                                && (image.getPixelReader().getArgb(50, 50) & 0x00ffffff) != 0;
                    }); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, 10000);
                System.out.println("VLC real-network recovery: first frame after three stalled streams in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
                runOnFxThread(() -> { player.stopForReload(); player.showLoading(); player.play(new PlayerResponse(base + "/missing.ts")); return null; });
                await(() -> {
                    try { return runOnFxThread(() -> player.errorLabel.isVisible() && player.getPlayerContainer().isVisible()); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, 10000);
            } finally {
                releaseStalls.countDown();
                runOnFxThread(() -> { player.disposePlayer(); stage.close(); return null; });
            }
        } finally {
            releaseStalls.countDown();
            for (Socket socket : sockets) socket.close();
            workers.shutdownNow();
        }
    }

    private static void await(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for real VLC/network interaction");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
    }
}
