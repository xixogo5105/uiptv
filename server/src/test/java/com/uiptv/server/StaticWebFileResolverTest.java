package com.uiptv.server;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticWebFileResolverTest {

    @Test
    void resolveAndReadUtf8_returnRealFile() throws Exception {
        Path resolved = StaticWebFileResolver.resolveRequestPath("/javascript/spa.js");
        Path normalized = resolved.normalize();
        assertEquals(Path.of("web", "javascript", "spa.js"), normalized.subpath(normalized.getNameCount() - 3, normalized.getNameCount()));

        String body = StaticWebFileResolver.readUtf8(resolved);
        assertNotNull(body);
        assertFalse(body.isBlank());
    }

    @Test
    void resolve_rejectsInvalidRequestsAndPaths() {
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolveRequestPath(null));
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolveRequestPath("/"));
        assertThrows(IOException.class, () -> StaticWebFileResolver.resolveRequestPath("/../pom.xml"));
    }

    @Test
    void resolve_rejectsMissingFile() {
        IOException ex = assertThrows(IOException.class, () -> StaticWebFileResolver.resolveRequestPath("/css/does-not-exist.css"));
        assertTrue(ex.getMessage().contains("File not found"));
    }
}
