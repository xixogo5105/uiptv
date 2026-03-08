package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.FfmpegService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AccountType;
import com.uiptv.util.HttpUtil;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPlayerJsonServerTest extends DbBackedTest {

    @Test
    void handle_directPlayback_mergesRequestFields_andReturnsJson() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();

        Account account = new Account("web", "user", "pass", "http://demo", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://demo/list.m3u8", false);
        account.setDbId("acc-1");

        Channel dbChannel = new Channel();
        dbChannel.setChannelId("ch-1");

        PlayerResponse response = new PlayerResponse("http://stream.test/live.m3u8");
        response.setDrmType("widevine");
        response.setDrmLicenseUrl("http://license");
        response.setClearKeysJson("{\"kid\":\"key\"}");
        response.setInputstreamaddon("inputstream.adaptive");
        response.setManifestType("hls");

        AccountService accountService = Mockito.mock(AccountService.class);
        ChannelDb channelDb = Mockito.mock(ChannelDb.class);
        PlayerService playerService = Mockito.mock(PlayerService.class);
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(false);

        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<ChannelDb> channelDbStatic = Mockito.mockStatic(ChannelDb.class);
             MockedStatic<PlayerService> playerServiceStatic = Mockito.mockStatic(PlayerService.class);
             MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);
            channelDbStatic.when(ChannelDb::get).thenReturn(channelDb);
            playerServiceStatic.when(PlayerService::getInstance).thenReturn(playerService);
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);

            Mockito.when(accountService.getById("acc-1")).thenReturn(account);
            Mockito.when(channelDb.getChannelById("ch-1", "cat-1")).thenReturn(dbChannel);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            Mockito.when(playerService.get(Mockito.eq(account), Mockito.any(Channel.class), Mockito.eq("series-7"), Mockito.isNull(), Mockito.eq("")))
                    .thenReturn(response);

            StubHttpExchange exchange = new StubHttpExchange(
                    "/player?accountId=acc-1&categoryId=cat-1&channelId=ch-1&seriesId=series-7&mode=itv&name=Web%20Channel"
                            + "&logo=http://img/logo.png&cmd=http://cmd/stream&drmType=widevine"
                            + "&drmLicenseUrl=http://license&inputstreamaddon=inputstream.adaptive&manifestType=hls",
                    "GET"
            );
            handler.handle(exchange);

            ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
            Mockito.verify(playerService).get(Mockito.eq(account), channelCaptor.capture(), Mockito.eq("series-7"), Mockito.isNull(), Mockito.eq(""));

            Channel merged = channelCaptor.getValue();
            assertEquals("Web Channel", merged.getName());
            assertEquals("http://img/logo.png", merged.getLogo());
            assertEquals("http://cmd/stream", merged.getCmd());
            assertEquals("widevine", merged.getDrmType());
            assertEquals("http://license", merged.getDrmLicenseUrl());
            assertEquals("inputstream.adaptive", merged.getInputstreamaddon());
            assertEquals("hls", merged.getManifestType());

            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().contains("\"url\":\"http://stream.test/live.m3u8\""));
            assertTrue(exchange.getResponseBodyText().contains("\"drm\""));
            assertTrue(exchange.getResponseBodyText().contains("\"type\":\"widevine\""));
        }
    }

    @Test
    void webPlaybackHelpers_coverRedirectNormalization_transmuxing_andFallbacks() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();

        assertEquals("", invoke(handler, "sanitizeParam", new Class[]{String.class}, (String) null));
        assertEquals("", invoke(handler, "sanitizeParam", new Class[]{String.class}, " undefined "));
        assertEquals("value", invoke(handler, "sanitizeParam", new Class[]{String.class}, " value "));
        assertTrue((Boolean) invoke(handler, "isHvecEnabled", new Class[]{String.class}, "yes"));
        assertFalse((Boolean) invoke(handler, "isHvecEnabled", new Class[]{String.class}, "0"));

        assertEquals("http://host/live/play/movie.ts",
                invoke(handler, "normalizeWebPlaybackUrl", new Class[]{String.class, String.class}, "vod", "https://host/live/play/movie.ts"));
        assertEquals("http://host/play/movie.php?id=1",
                invoke(handler, "downgradeHttpsToHttp", new Class[]{String.class}, "https://host/play/movie.php?id=1"));
        assertTrue((Boolean) invoke(handler, "shouldForceWebHlsForUrl", new Class[]{String.class, String.class}, "series", "https://host/play/movie.php?id=1"));
        assertFalse((Boolean) invoke(handler, "shouldForceWebHlsForUrl", new Class[]{String.class, String.class}, "itv", "https://host/play/movie.php?id=1"));

        HttpUtil.HttpResult redirect = new HttpUtil.HttpResult(302, "", Map.of(), Map.of("Location", List.of("/final/segment.m3u8")));
        String resolved = invoke(handler, "resolveRedirectTarget",
                new Class[]{String.class, HttpUtil.HttpResult.class, boolean.class},
                "https://host/play/movie.php?id=1", redirect, true);
        assertEquals("http://host/final/segment.m3u8", resolved);

        try (MockedStatic<HttpUtil> httpUtilStatic = Mockito.mockStatic(HttpUtil.class)) {
            httpUtilStatic.when(() -> HttpUtil.sendRequest(
                    Mockito.eq("http://host/play/movie.php?id=1"),
                    Mockito.anyMap(),
                    Mockito.eq("GET"),
                    Mockito.isNull(),
                    Mockito.any(HttpUtil.RequestOptions.class)
            )).thenReturn(new HttpUtil.HttpResult(302, "", Map.of(), Map.of("Location", List.of("/redirected"))));
            httpUtilStatic.when(() -> HttpUtil.sendRequest(
                    Mockito.eq("http://host/redirected"),
                    Mockito.anyMap(),
                    Mockito.eq("GET"),
                    Mockito.isNull(),
                    Mockito.any(HttpUtil.RequestOptions.class)
            )).thenReturn(new HttpUtil.HttpResult(200, "", Map.of(), Map.of()));

            String chained = invoke(handler, "resolveWebPlaybackRedirects",
                    new Class[]{String.class, String.class}, "vod", "http://host/play/movie.php?id=1");
            assertEquals("http://host/redirected", chained);
        }

        PlayerResponse hlsResponse = new PlayerResponse("https://host/play/movie.php?id=1");
        FfmpegService ffmpegService = Mockito.mock(FfmpegService.class);
        try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
            ffmpegStatic.when(FfmpegService::getInstance).thenReturn(ffmpegService);
            Mockito.when(ffmpegService.startTransmuxing(Mockito.anyString(), Mockito.eq(true))).thenReturn(true);

            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class},
                    hlsResponse, "vod", "1");

            assertEquals("/hls/stream.m3u8?hvec=1", hlsResponse.getUrl());
            assertEquals("hls", hlsResponse.getManifestType());
        }

        PlayerResponse fallbackResponse = new PlayerResponse("https://host/play/movie.php?id=1");
        FfmpegService fallbackFfmpeg = Mockito.mock(FfmpegService.class);
        try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class);
             MockedStatic<ServerUrlUtil> serverUrlStatic = Mockito.mockStatic(ServerUrlUtil.class)) {
            ffmpegStatic.when(FfmpegService::getInstance).thenReturn(fallbackFfmpeg);
            serverUrlStatic.when(ServerUrlUtil::getLocalServerUrl).thenReturn("http://127.0.0.1:9090");
            Mockito.when(fallbackFfmpeg.startTransmuxing(Mockito.anyString(), Mockito.eq(true))).thenReturn(false);

            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class},
                    fallbackResponse, "series", "0");

            assertTrue(fallbackResponse.getUrl().startsWith("http://127.0.0.1:9090/proxy-stream?src="));
            assertTrue(fallbackResponse.getUrl().contains("http%3A%2F%2Fhost%2Fplay%2Fmovie.php%3Fid%3D1"));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        StubHttpExchange(String uri, String method) {
            this.requestUri = URI.create(uri);
            this.method = method;
        }

        String getResponseBodyText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            // No-op for the in-memory exchange test double.
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
            // No-op for the in-memory exchange test double.
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // No-op for the in-memory exchange test double.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
