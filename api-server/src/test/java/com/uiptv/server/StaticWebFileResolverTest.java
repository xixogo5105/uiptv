package com.uiptv.server;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticWebFileResolverTest {

    @Test
    void containmentChecksIncludeDecodedTraversalAndSymlinks(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path root = java.nio.file.Files.createDirectory(directory.resolve("web"));
        Path nested = java.nio.file.Files.createDirectories(root.resolve("nested"));
        java.nio.file.Files.writeString(nested.resolve("valid.txt"), "asset");
        Path secret = java.nio.file.Files.writeString(directory.resolve("secret.txt"), "outside");
        try (var platform = org.mockito.Mockito.mockStatic(com.uiptv.util.Platform.class)) {
            platform.when(com.uiptv.util.Platform::getWebServerRootPath).thenReturn(root.toString());
            assertEquals("asset", StaticWebFileResolver.readUtf8(StaticWebFileResolver.resolve(
                    new TestHttpExchange("/nested/valid.txt", "GET"))));
            assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(new TestHttpExchange("/%2e%2e/secret.txt", "GET")));
            assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(new TestHttpExchange("/nested", "GET")));
            platform.when(com.uiptv.util.Platform::getWebServerRootPath).thenReturn(directory.resolve("missing").toString());
            assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(new TestHttpExchange("/nested/valid.txt", "GET")));
            platform.when(com.uiptv.util.Platform::getWebServerRootPath).thenReturn(root.toString());
            try { java.nio.file.Files.createSymbolicLink(root.resolve("escape.txt"), secret); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException _) {
                org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable on this filesystem");
            }
            assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(new TestHttpExchange("/escape.txt", "GET")));
        }
    }

    @Test
    void resolveAndReadUtf8_returnRealFile() throws Exception {
        TestHttpExchange exchange = new TestHttpExchange("/javascript/spa.js", "GET");
        Path resolved = StaticWebFileResolver.resolve(exchange);
        Path normalized = resolved.normalize();
        assertEquals(Path.of("web", "javascript", "spa.js"), normalized.subpath(normalized.getNameCount() - 3, normalized.getNameCount()));

        String body = StaticWebFileResolver.readUtf8(resolved);
        assertNotNull(body);
        assertFalse(body.isBlank());
    }

    @Test
    void resolve_rejectsInvalidRequestsAndPaths() {
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolve(null));

        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestURI()).thenReturn(null);
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
