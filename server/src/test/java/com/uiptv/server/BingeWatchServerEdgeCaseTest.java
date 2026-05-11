package com.uiptv.server;

import com.uiptv.service.BingeWatchService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BingeWatchServerEdgeCaseTest {

    @Test
    void playlistServer_returns404WhenMissingToken() throws Exception {
        BingeWatchService service = Mockito.mock(BingeWatchService.class);
        try (MockedStatic<BingeWatchService> staticService = Mockito.mockStatic(BingeWatchService.class)) {
            staticService.when(BingeWatchService::getInstance).thenReturn(service);
            Mockito.when(service.renderPlaylist(Mockito.any())).thenReturn("");
            assertNull(BingeWatchRouteSupport.INSTANCE.renderPlaylist(null, service));
        }
    }

    @Test
    void entryServer_rejectsUnsupportedMethod_andMissingParams() throws Exception {
        assertEquals(405, BingeWatchRouteSupport.INSTANCE.resolveEntry("POST", "tok", "ep").getStatusCode());
        assertEquals(404, BingeWatchRouteSupport.INSTANCE.resolveEntry("GET", "tok", null).getStatusCode());
    }

    @Test
    void entryServer_handlesResolveFailures() throws Exception {
        BingeWatchService service = Mockito.mock(BingeWatchService.class);
        try (MockedStatic<BingeWatchService> staticService = Mockito.mockStatic(BingeWatchService.class)) {
            staticService.when(BingeWatchService::getInstance).thenReturn(service);
            Mockito.when(service.resolveEpisode("tok", "ep"))
                    .thenReturn(null)
                    .thenThrow(new RuntimeException("boom"));

            assertEquals(404, BingeWatchRouteSupport.INSTANCE.resolveEntry("GET", "tok", "ep", service).getStatusCode());

            assertEquals(502, BingeWatchRouteSupport.INSTANCE.resolveEntry("GET", "tok", "ep", service).getStatusCode());
        }
    }
}
