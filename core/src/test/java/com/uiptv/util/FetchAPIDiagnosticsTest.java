package com.uiptv.util;

import com.uiptv.model.Account;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link FetchAPI#fetchWithDiagnostics}.
 *
 * <p>Previously every failure mode - connection refused, non-200 status, empty body - collapsed
 * into the same empty string, so an unreachable provider was indistinguishable from a provider
 * with no data. That ambiguity is what made intermittent bulk reload failures unattributable.
 */
class FetchAPIDiagnosticsTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchWithDiagnostics_returnsBody_whenServerRespondsWith200() {
        respondWith(200, "{\"js\":[]}");

        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(Map.of("type", "itv"), account(), HttpUtil.RequestOptions.defaults());

        assertFalse(result.transportFailure(), "A 200 response is not a transport failure");
        assertEquals("{\"js\":[]}", result.body());
        assertEquals(200, result.statusCode());
        assertEquals("", result.failureReason());
    }

    @Test
    void fetchWithDiagnostics_reportsTransportFailure_whenServerReturnsErrorStatus() {
        respondWith(503, "service unavailable");

        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(Map.of("type", "itv"), account(), HttpUtil.RequestOptions.defaults());

        assertTrue(result.transportFailure(), "HTTP 503 must be reported as a failure, not as empty data");
        assertEquals(503, result.statusCode());
        assertTrue(result.failureReason().contains("503"), "Reason should name the status: " + result.failureReason());
        assertTrue(result.endpoint().contains("127.0.0.1"), "Endpoint should identify the failed URL: " + result.endpoint());
        assertTrue(result.body().isEmpty());
    }

    @Test
    void fetchWithDiagnostics_reportsTransportFailure_whenBodyIsEmptyDespiteStatus200() {
        respondWith(200, "");

        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(Map.of("type", "itv"), account(), HttpUtil.RequestOptions.defaults());

        assertTrue(result.transportFailure(), "An empty 200 body is a failure, not a valid empty catalog");
        assertEquals(200, result.statusCode());
        assertTrue(result.failureReason().contains("Empty response body"), result.failureReason());
    }

    @Test
    void fetchWithDiagnostics_reportsTransportFailure_whenHostIsUnreachable() throws IOException {
        // Nothing is listening on this port: the connection is refused.
        Account unreachable = account();
        unreachable.setServerPortalUrl("http://127.0.0.1:" + unusedPort() + "/portal.php");

        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(Map.of("type", "itv"), unreachable, HttpUtil.RequestOptions.defaults());

        assertTrue(result.transportFailure(), "Connection failure must be reported as a failure");
        assertTrue(result.body().isEmpty());
        assertEquals(-1, result.statusCode(), "An incomplete request has no HTTP status");
        assertTrue(result.endpoint().contains("127.0.0.1"), result.endpoint());
        assertFalse(result.failureReason().isEmpty(), "A refused connection must still report a reason");
    }

    @Test
    void fetchWithDiagnostics_reportsTransportFailure_whenTargetHostIsNotSpecified() {
        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(Map.of("type", "itv"), new Account(), HttpUtil.RequestOptions.defaults());

        assertTrue(result.transportFailure());
        assertTrue(result.failureReason().contains("Target host is not specified"), result.failureReason());
    }

    @Test
    void fetch_preservesLegacyContract_ofReturningEmptyStringOnFailure() {
        respondWith(500, "boom");

        assertEquals("", FetchAPI.fetch(Map.of("type", "itv"), account()),
                "fetch() must keep returning an empty string so existing callers are unaffected");
    }

    private void respondWith(int status, String body) {
        server.createContext("/", exchange -> {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            }
            exchange.close();
        });
    }

    private Account account() {
        Account account = new Account();
        account.setAccountName("diag");
        account.setAction(itv);
        account.setServerPortalUrl("http://127.0.0.1:" + port + "/portal.php");
        account.setUrl("http://127.0.0.1:" + port + "/portal.php");
        account.setType(AccountType.M3U8_URL);
        return account;
    }

    private int unusedPort() throws IOException {
        java.net.ServerSocket socket = new java.net.ServerSocket(0);
        int free = socket.getLocalPort();
        socket.close();
        return free;
    }
}
