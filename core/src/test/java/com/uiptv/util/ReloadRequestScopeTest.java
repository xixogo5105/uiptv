package com.uiptv.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReloadRequestScopeTest {
    private HttpServer server(java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", handler::accept); server.start(); return server;
    }
    private String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
    private void trickle(com.sun.net.httpserver.HttpExchange exchange) {
        try {
            exchange.sendResponseHeaders(200, 0);
            for (int i = 0; i < 300; i++) {
                exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); Thread.sleep(20);
            }
        } catch (Exception _) { } finally { exchange.close(); }
    }
    @Test void deadlineAbortsTricklingBodyInsteadOfResettingOnEachByte() throws Exception {
        HttpServer server = server(this::trickle);
        try (var scope = new ReloadRequestScope(Duration.ofSeconds(3), Duration.ofMillis(250), 4, null)) {
            long start = System.nanoTime();
            assertThrows(ReloadRequestScope.LimitExceeded.class, () -> HttpUtil.sendRequest(url(server), null, "GET"));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000);
            assertEquals(1, scope.requests());
        } finally { server.stop(0); }
    }
    @Test void accountDeadlineAlsoBoundsAnInFlightRequest() throws Exception {
        HttpServer server = server(this::trickle);
        try (var scope = new ReloadRequestScope(Duration.ofMillis(250), Duration.ofSeconds(3), 4, null)) {
            long start = System.nanoTime();
            assertThrows(ReloadRequestScope.LimitExceeded.class, () -> HttpUtil.sendRequest(url(server), null, "GET"));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000);
        } finally { server.stop(0); }
    }
    @Test void redirectsCannotMultiplyTheRequestBudgetWithoutBound() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            int call = calls.incrementAndGet();
            try {
                exchange.getResponseHeaders().set("Location", "/redirect-" + call);
                exchange.sendResponseHeaders(302, -1);
            } catch (Exception _) { } finally { exchange.close(); }
        });
        try (var scope = new ReloadRequestScope(Duration.ofSeconds(3), Duration.ofSeconds(1), 4, null)) {
            assertThrows(java.io.IOException.class, () -> HttpUtil.sendRequest(url(server), null, "GET"));
            assertEquals(6, calls.get(), "Initial request plus the normal five redirects");
        } finally { server.stop(0); }
    }

    @Test void streamDeadlineRemainsActiveAfterHeadersHaveArrived() throws Exception {
        HttpServer server = server(this::trickle);
        try (var scope = new ReloadRequestScope(Duration.ofSeconds(3), Duration.ofMillis(250), 4, null)) {
            var response = HttpUtil.openStream(url(server), null, "GET", null, HttpUtil.RequestOptions.defaults());
            try {
                assertThrows(java.io.IOException.class, () -> response.bodyStream().readAllBytes());
                assertThrows(ReloadRequestScope.LimitExceeded.class, scope::check);
            } finally { try { response.close(); } catch (java.io.IOException _) { } }
        } finally { server.stop(0); }
    }
    @Test void requestCapStopsBeforeAnotherNetworkCallAndIsRemovedOnClose() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            calls.incrementAndGet();
            try { exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write("{}".getBytes()); }
            catch (Exception _) { } finally { exchange.close(); }
        });
        try {
            try (var scope = new ReloadRequestScope(Duration.ofSeconds(3), Duration.ofSeconds(1), 2, null)) {
                HttpUtil.sendRequest(url(server), null, "GET"); HttpUtil.sendRequest(url(server), null, "GET");
                assertThrows(ReloadRequestScope.LimitExceeded.class, () -> HttpUtil.sendRequest(url(server), null, "GET"));
                assertEquals(2, calls.get());
            }
            assertEquals(200, HttpUtil.sendRequest(url(server), null, "GET").statusCode());
            assertEquals(3, calls.get());
        } finally { server.stop(0); }
    }
    @Test void interruptCancelsAnActiveBodyReadPromptly() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        HttpServer server = server(exchange -> { entered.countDown(); trickle(exchange); });
        var finished = new CompletableFuture<Boolean>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try (var scope = new ReloadRequestScope(Duration.ofSeconds(10), Duration.ofSeconds(5), 4, null)) {
                HttpUtil.sendRequest(url(server), null, "GET"); finished.complete(false);
            } catch (Exception expected) { finished.complete(Thread.currentThread().isInterrupted()); }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS)); worker.interrupt();
            assertTrue(finished.get(2, TimeUnit.SECONDS));
        } finally { worker.interrupt(); server.stop(0); }
    }
}
