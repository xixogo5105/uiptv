package com.uiptv.service;

import com.uiptv.util.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class LogoResolverReloadTest {
    private LogoResolverService fresh() throws Exception {
        var constructor = LogoResolverService.class.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance();
    }
    @Test void catalogueRefreshNeverStartsArtworkNetworkRequests() throws Exception {
        var resolver = fresh();
        try (var http = mockStatic(HttpUtil.class);
             var _ = new ReloadRequestScope(Duration.ofSeconds(3), Duration.ofSeconds(1), 24, null)) {
            for (int i = 0; i < 1000; i++) assertEquals("", resolver.resolve("Reload fixture " + i, ""));
            http.verifyNoInteractions();
        }
    }
    @Test void failedArtworkCatalogIsNotRetriedForEveryChannel() throws Exception {
        var resolver = fresh();
        try (var http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.sendRequest(anyString(), isNull(), eq("GET")))
                    .thenReturn(new HttpUtil.HttpResult(503, "", Map.of(), Map.of()));
            var refresh = LogoResolverService.class.getDeclaredMethod("ensureCatalogLoaded"); refresh.setAccessible(true);
            for (int i = 0; i < 1000; i++) refresh.invoke(resolver);
            http.verify(() -> HttpUtil.sendRequest(anyString(), isNull(), eq("GET")), times(1));
        }
    }
}
