package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.*;
import com.uiptv.util.AppLog;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

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
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(false);

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
        assertFalse((Boolean) invoke(handler, "shouldForceWebHlsForUrl", new Class[]{String.class, String.class}, "vod", "https://host/play/movie.php?id=1"));
        assertFalse((Boolean) invoke(handler, "shouldForceWebHlsForUrl", new Class[]{String.class, String.class}, "itv", "https://host/play/movie.php?id=1"));

        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);

            PlayerResponse directNoProbeResponse = new PlayerResponse("http://host/live/play/tokenized/9412");
            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    directNoProbeResponse, "vod", "0", false);
            assertEquals("http://host/live/play/tokenized/9412", directNoProbeResponse.getUrl());

            PlayerResponse directLiveAdaptiveResponse = new PlayerResponse("https://host/index.m3u8?token=abc123");
            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    directLiveAdaptiveResponse, "itv", "0", false);
            assertEquals("https://host/index.m3u8?token=abc123", directLiveAdaptiveResponse.getUrl());

            PlayerResponse directLiveTsResponse = new PlayerResponse("http://host/live/play/live.php?stream=1001&extension=ts&play_token=pt1001");
            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    directLiveTsResponse, "itv", "0", false);
            assertEquals("http://host/live/play/live.php?stream=1001&extension=ts&play_token=pt1001", directLiveTsResponse.getUrl());

            PlayerResponse hlsResponse = new PlayerResponse("https://host/play/movie.php?id=1");
            FfmpegService ffmpegService = Mockito.mock(FfmpegService.class);
            try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
                ffmpegStatic.when(FfmpegService::getInstance).thenReturn(ffmpegService);
                Mockito.when(ffmpegService.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(true);

                invoke(handler, "applyWebPlaybackProcessing",
                        new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                        hlsResponse, "series", "1", false);

                assertEquals("/hls/stream.m3u8?hvec=1", hlsResponse.getUrl());
                assertEquals("hls", hlsResponse.getManifestType());
            }

            PlayerResponse preferredHlsResponse = new PlayerResponse("https://host/index.m3u8?token=abc123");
            FfmpegService preferredHlsFfmpeg = Mockito.mock(FfmpegService.class);
            try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
                ffmpegStatic.when(FfmpegService::getInstance).thenReturn(preferredHlsFfmpeg);
                Mockito.when(preferredHlsFfmpeg.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(true);

                invoke(handler, "applyWebPlaybackProcessing",
                        new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                        preferredHlsResponse, "itv", "0", true);

                assertEquals("/hls/stream.m3u8", preferredHlsResponse.getUrl());
                assertEquals("hls", preferredHlsResponse.getManifestType());
            }

            PlayerResponse preferredHlsTsResponse = new PlayerResponse("http://host/live/play/live.php?stream=1001&extension=ts&play_token=pt1001");
            FfmpegService preferredHlsTsFfmpeg = Mockito.mock(FfmpegService.class);
            try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
                ffmpegStatic.when(FfmpegService::getInstance).thenReturn(preferredHlsTsFfmpeg);
                Mockito.when(preferredHlsTsFfmpeg.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(true);

                invoke(handler, "applyWebPlaybackProcessing",
                        new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                        preferredHlsTsResponse, "itv", "0", true);

                assertEquals("/hls/stream.m3u8", preferredHlsTsResponse.getUrl());
                assertEquals("hls", preferredHlsTsResponse.getManifestType());
            }

            PlayerResponse vodProxyResponse = new PlayerResponse("https://host/play/movie.php?id=1");
            try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class);
                 MockedStatic<ServerUrlUtil> serverUrlStatic = Mockito.mockStatic(ServerUrlUtil.class)) {
                FfmpegService directFfmpeg = Mockito.mock(FfmpegService.class);
                ffmpegStatic.when(FfmpegService::getInstance).thenReturn(directFfmpeg);
                serverUrlStatic.when(ServerUrlUtil::getLocalServerUrl).thenReturn("http://127.0.0.1:9090");

                invoke(handler, "applyWebPlaybackProcessing",
                        new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                        vodProxyResponse, "vod", "0", false);

                Mockito.verify(directFfmpeg).stopTransmuxing();
                assertTrue(vodProxyResponse.getUrl().startsWith("http://127.0.0.1:9090/proxy-stream?src="));
            }

            PlayerResponse fallbackResponse = new PlayerResponse("https://host/play/movie.php?id=1");
            FfmpegService fallbackFfmpeg = Mockito.mock(FfmpegService.class);
            try (MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class);
                 MockedStatic<ServerUrlUtil> serverUrlStatic = Mockito.mockStatic(ServerUrlUtil.class)) {
                ffmpegStatic.when(FfmpegService::getInstance).thenReturn(fallbackFfmpeg);
                serverUrlStatic.when(ServerUrlUtil::getLocalServerUrl).thenReturn("http://127.0.0.1:9090");
                Mockito.when(fallbackFfmpeg.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(false);

                invoke(handler, "applyWebPlaybackProcessing",
                        new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                        fallbackResponse, "series", "0", false);

                assertEquals("http://host/play/movie.php?id=1", fallbackResponse.getUrl());
            }

            PlayerResponse directVodResponse = new PlayerResponse("https://host/live/play/tokenized/9412");
            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    directVodResponse, "vod", "0", false);
            assertEquals("http://host/live/play/tokenized/9412", directVodResponse.getUrl());
        }
    }

    @Test
    void handle_clientDisconnectWhileWritingResponse_isIgnored() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(false);
        List<String> logs = new ArrayList<>();
        Consumer<String> listener = logs::add;

        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            AppLog.registerListener(listener);
            try (BrokenPipeHttpExchange exchange = new BrokenPipeHttpExchange(
                        "/player?mode=itv&url=http://stream.test/live.m3u8",
                        "GET"
                )) {

                handler.handle(exchange);

                assertEquals(200, exchange.getResponseCode());
                assertTrue(logs.stream().noneMatch(log -> log.contains("HttpPlayerJsonServer failed")));
            } finally {
                AppLog.unregisterListener(listener);
            }
        }
    }

    @Test
    void handle_directVodPlayback_usesVodChannelLookup_whenRequestCategoryDiffers() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();

        Account account = new Account("stalker", "user", "pass", "http://demo", null, null, null, null, null, null,
                com.uiptv.util.AccountType.STALKER_PORTAL, null, null, false);
        account.setDbId("acc-1525");

        Channel vodChannel = new Channel();
        vodChannel.setChannelId("9412");
        vodChannel.setName("Shehzada - 2023");
        vodChannel.setCmd("ffmpeg http://provider/live/play/token/9412");

        PlayerResponse response = new PlayerResponse("http://provider/live/play/token/9412");

        AccountService accountService = Mockito.mock(AccountService.class);
        ChannelDb channelDb = Mockito.mock(ChannelDb.class);
        VodChannelDb vodChannelDb = Mockito.mock(VodChannelDb.class);
        PlayerService playerService = Mockito.mock(PlayerService.class);
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(false);

        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<ChannelDb> channelDbStatic = Mockito.mockStatic(ChannelDb.class);
             MockedStatic<VodChannelDb> vodChannelDbStatic = Mockito.mockStatic(VodChannelDb.class);
             MockedStatic<PlayerService> playerServiceStatic = Mockito.mockStatic(PlayerService.class);
             MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class)) {
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);
            channelDbStatic.when(ChannelDb::get).thenReturn(channelDb);
            vodChannelDbStatic.when(VodChannelDb::get).thenReturn(vodChannelDb);
            playerServiceStatic.when(PlayerService::getInstance).thenReturn(playerService);
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);

            Mockito.when(accountService.getById("acc-1525")).thenReturn(account);
            Mockito.when(channelDb.getChannelById("9412", "359")).thenReturn(null);
            Mockito.when(vodChannelDb.getChannelByChannelId("9412", "359", "acc-1525")).thenReturn(null);
            Mockito.when(vodChannelDb.getChannelByChannelIdAndAccount("9412", "acc-1525")).thenReturn(vodChannel);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            Mockito.doReturn(response)
                    .when(playerService)
                    .get(Mockito.nullable(Account.class), Mockito.nullable(Channel.class), Mockito.nullable(String.class), Mockito.nullable(String.class), Mockito.nullable(String.class));

            StubHttpExchange exchange = new StubHttpExchange(
                    "/player?accountId=acc-1525&categoryId=359&channelId=9412&mode=vod&name=Shehzada%20-%202023&cmd=eyJ0eXBlIjoibW92aWUiLCJzdHJlYW1faWQiOiI5NDEyIn0",
                    "GET"
            );
            handler.handle(exchange);

            ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
            Mockito.verify(playerService).get(
                    Mockito.eq(account),
                    channelCaptor.capture(),
                    Mockito.nullable(String.class),
                    Mockito.nullable(String.class),
                    Mockito.nullable(String.class)
            );
            assertEquals("ffmpeg http://provider/live/play/token/9412", channelCaptor.getValue().getCmd());
            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().contains("\"url\":\"http://provider/live/play/token/9412\""));
        }
    }

    @Test
    void webPlayback_transcodingIsFallbackWhenEnabled() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(true);
        PlayerResponse response = new PlayerResponse("https://host/play/movie.php?id=1");
        FfmpegService ffmpegService = Mockito.mock(FfmpegService.class);

        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            ffmpegStatic.when(FfmpegService::getInstance).thenReturn(ffmpegService);
            Mockito.when(ffmpegService.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(false);
            Mockito.when(ffmpegService.startTranscoding(Mockito.anyString(), Mockito.eq(false))).thenReturn(true);

            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    response, "series", "0", false);

            Mockito.verify(ffmpegService, Mockito.times(2)).startTransmuxing(Mockito.anyString(), Mockito.eq(false));
            Mockito.verify(ffmpegService).startTranscoding(Mockito.anyString(), Mockito.eq(false));
            assertEquals("/hls/stream.m3u8", response.getUrl());
            assertEquals("hls", response.getManifestType());
        }
    }

    @Test
    void webPlayback_transcodingIsSkippedWhenDisabled() throws Exception {
        HttpPlayerJsonServer handler = new HttpPlayerJsonServer();
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Configuration configuration = new Configuration();
        configuration.setEnableFfmpegTranscoding(false);
        PlayerResponse response = new PlayerResponse("https://host/play/movie.php?id=1");
        FfmpegService ffmpegService = Mockito.mock(FfmpegService.class);

        try (MockedStatic<ConfigurationService> configurationServiceStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<FfmpegService> ffmpegStatic = Mockito.mockStatic(FfmpegService.class)) {
            configurationServiceStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            ffmpegStatic.when(FfmpegService::getInstance).thenReturn(ffmpegService);
            Mockito.when(ffmpegService.startTransmuxing(Mockito.anyString(), Mockito.eq(false))).thenReturn(false);

            invoke(handler, "applyWebPlaybackProcessing",
                    new Class[]{PlayerResponse.class, String.class, String.class, boolean.class},
                    response, "series", "0", false);

            Mockito.verify(ffmpegService, Mockito.times(2)).startTransmuxing(Mockito.anyString(), Mockito.eq(false));
            Mockito.verify(ffmpegService, Mockito.never()).startTranscoding(Mockito.anyString(), Mockito.anyBoolean());
            assertEquals("http://host/play/movie.php?id=1", response.getUrl());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    private static class StubHttpExchange extends HttpExchange implements AutoCloseable {
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

    private static final class BrokenPipeHttpExchange extends StubHttpExchange {
        private BrokenPipeHttpExchange(String uri, String method) {
            super(uri, method);
        }

        @Override
        public OutputStream getResponseBody() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("Broken pipe");
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    throw new IOException("Broken pipe");
                }
            };
        }
    }
}
