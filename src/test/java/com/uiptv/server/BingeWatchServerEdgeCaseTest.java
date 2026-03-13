package com.uiptv.server;

import com.uiptv.service.BingeWatchService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BingeWatchServerEdgeCaseTest {

    @Test
    void playlistServer_returns404WhenMissingToken() throws Exception {
        HttpBingeWatchPlaylistServer handler = new HttpBingeWatchPlaylistServer();
        BingeWatchService service = Mockito.mock(BingeWatchService.class);
        try (MockedStatic<BingeWatchService> staticService = Mockito.mockStatic(BingeWatchService.class)) {
            staticService.when(BingeWatchService::getInstance).thenReturn(service);
            Mockito.when(service.renderPlaylist(Mockito.any())).thenReturn("");

            TestHttpExchange exchange = new TestHttpExchange("/bingewatch.m3u8", "GET");
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }
    }

    @Test
    void entryServer_rejectsUnsupportedMethod_andMissingParams() throws Exception {
        HttpBingeWatchEntryServer handler = new HttpBingeWatchEntryServer();

        TestHttpExchange postExchange = new TestHttpExchange("/bingwatch", "POST");
        handler.handle(postExchange);
        assertEquals(405, postExchange.getResponseCode());

        TestHttpExchange missing = new TestHttpExchange("/bingwatch?token=tok", "GET");
        handler.handle(missing);
        assertEquals(404, missing.getResponseCode());
    }

    @Test
    void entryServer_handlesResolveFailures() throws Exception {
        HttpBingeWatchEntryServer handler = new HttpBingeWatchEntryServer();

        BingeWatchService service = Mockito.mock(BingeWatchService.class);
        try (MockedStatic<BingeWatchService> staticService = Mockito.mockStatic(BingeWatchService.class)) {
            staticService.when(BingeWatchService::getInstance).thenReturn(service);
            Mockito.when(service.resolveEpisode("tok", "ep"))
                    .thenReturn(null)
                    .thenThrow(new RuntimeException("boom"));

            TestHttpExchange notFound = new TestHttpExchange("/bingwatch?token=tok&episodeId=ep", "GET");
            handler.handle(notFound);
            assertEquals(404, notFound.getResponseCode());

            TestHttpExchange error = new TestHttpExchange("/bingwatch?token=tok&episodeId=ep", "GET");
            handler.handle(error);
            assertEquals(502, error.getResponseCode());
        }
    }
}
