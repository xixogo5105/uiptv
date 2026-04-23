package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticWebFileResolverTest {

    @Test
    void resolveAndReadUtf8_returnRealFile() throws Exception {
        TestHttpExchange exchange = new TestHttpExchange("/javascript/spa.js", "GET");
        Path resolved = StaticWebFileResolver.resolve(exchange);
        assertTrue(resolved.toString().endsWith("web/javascript/spa.js"));

        String body = StaticWebFileResolver.readUtf8(resolved);
        assertNotNull(body);
        assertFalse(body.isBlank());
    }

    @Test
    void resolve_rejectsInvalidRequestsAndPaths() {
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(null));

        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        Mockito.when(exchange.getRequestURI()).thenReturn(null);
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(exchange));

        TestHttpExchange blankPath = new TestHttpExchange("/", "GET");
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(blankPath));

        TestHttpExchange traversal = new TestHttpExchange("/../pom.xml", "GET");
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(traversal));
    }

    @Test
    void resolve_rejectsMissingFile() {
        TestHttpExchange missing = new TestHttpExchange("/css/does-not-exist.css", "GET");
        IOException ex = assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(missing));
        assertTrue(ex.getMessage().contains("File not found"));
    }
}
