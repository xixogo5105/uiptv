package com.uiptv.server;

import com.uiptv.server.html.HttpSpaHtmlServer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticAssetHttpServersTest {

    @Test
    void cssJavascriptManifestHandlers_returnContent() throws Exception {
        HttpCssServer cssServer = new HttpCssServer();
        TestHttpExchange cssExchange = new TestHttpExchange("/css/uiptv.css", "GET");
        cssServer.handle(cssExchange);
        assertEquals(200, cssExchange.getResponseCode());
        assertTrue(cssExchange.getResponseHeaders().getFirst("Content-Type").contains("text/css"));
        assertFalse(cssExchange.getResponseBodyText().isBlank());

        HttpJavascriptServer jsServer = new HttpJavascriptServer();
        TestHttpExchange jsExchange = new TestHttpExchange("/javascript/spa.js", "GET");
        jsServer.handle(jsExchange);
        assertEquals(200, jsExchange.getResponseCode());
        assertTrue(jsExchange.getResponseHeaders().getFirst("Content-Type").contains("text/javascript"));
        assertFalse(jsExchange.getResponseBodyText().isBlank());

        HttpManifestServer manifestServer = new HttpManifestServer();
        TestHttpExchange manifestExchange = new TestHttpExchange("/manifest.json", "GET");
        manifestServer.handle(manifestExchange);
        assertEquals(200, manifestExchange.getResponseCode());
        assertTrue(manifestExchange.getResponseHeaders().getFirst("Content-Type").contains("application/json"));
        assertTrue(manifestExchange.getResponseBodyText().contains("name"));
    }

    @Test
    void staticHandlers_return404ForMissingFiles() throws Exception {
        HttpCssServer cssServer = new HttpCssServer();
        TestHttpExchange cssExchange = new TestHttpExchange("/css/missing.css", "GET");
        cssServer.handle(cssExchange);
        assertEquals(404, cssExchange.getResponseCode());

        HttpJavascriptServer jsServer = new HttpJavascriptServer();
        TestHttpExchange jsExchange = new TestHttpExchange("/javascript/missing.js", "GET");
        jsServer.handle(jsExchange);
        assertEquals(404, jsExchange.getResponseCode());

        HttpManifestServer manifestServer = new HttpManifestServer();
        TestHttpExchange manifestExchange = new TestHttpExchange("/manifest-missing.json", "GET");
        manifestServer.handle(manifestExchange);
        assertEquals(404, manifestExchange.getResponseCode());
    }

    @Test
    void staticHandlers_rejectNonGetRequests() throws Exception {
        HttpCssServer cssServer = new HttpCssServer();
        TestHttpExchange cssExchange = new TestHttpExchange("/css/uiptv.css", "POST");
        cssServer.handle(cssExchange);
        assertEquals(405, cssExchange.getResponseCode());
    }

    @Test
    void spaHtmlServer_rendersTemplate() throws Exception {
        HttpSpaHtmlServer handler = new HttpSpaHtmlServer();
        TestHttpExchange exchange = new TestHttpExchange("/index.html", "GET");
        handler.handle(exchange);
        assertEquals(200, exchange.getResponseCode());
        assertTrue(exchange.getResponseBodyText().toLowerCase().contains("<html"));
    }

    @Test
    void spaHtmlServer_rendersMyflixFromRepositoryRoot() throws Exception {
        Path repositoryRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if ("api-server".equals(repositoryRoot.getFileName().toString())) {
            repositoryRoot = repositoryRoot.getParent();
        }
        try (UserDirScope userDirScope = UserDirScope.open(repositoryRoot)) {
            HttpSpaHtmlServer handler = new HttpSpaHtmlServer("myflix.html");
            TestHttpExchange exchange = new TestHttpExchange("/myflix.html", "GET");
            handler.handle(exchange);

            assertEquals(repositoryRoot.toString(), userDirScope.currentUserDir());
            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().toLowerCase().contains("<html"));
        }
    }

    @Test
    void iconServer_servesIcons_andHandlesMissingIcon() throws Exception {
        HttpIconServer handler = new HttpIconServer();

        Path iconPath = Path.of(com.uiptv.util.Platform.getWebServerRootPath(), "icon.ico");
        if (Files.exists(iconPath)) {
            TestHttpExchange icoExchange = new TestHttpExchange("/icon.ico", "GET");
            handler.handle(icoExchange);
            assertEquals(200, icoExchange.getResponseCode());
            assertTrue(icoExchange.getResponseHeaders().getFirst("Content-Type").contains("image/x-icon"));
            assertTrue(icoExchange.getResponseBodyBytes().length > 0);
        } else {
            TestHttpExchange icoExchange = new TestHttpExchange("/icon.ico", "GET");
            handler.handle(icoExchange);
            assertEquals(404, icoExchange.getResponseCode());
        }

        Path pngPath = Path.of(com.uiptv.util.Platform.getWebServerRootPath(), "icon.png");
        TestHttpExchange pngExchange = new TestHttpExchange("/icon.png", "GET");
        handler.handle(pngExchange);
        if (Files.exists(pngPath)) {
            assertEquals(200, pngExchange.getResponseCode());
            assertTrue(pngExchange.getResponseHeaders().getFirst("Content-Type").contains("image/png"));
            assertTrue(pngExchange.getResponseBodyBytes().length > 0);
        } else {
            assertEquals(404, pngExchange.getResponseCode());
        }

        TestHttpExchange missingExchange = new TestHttpExchange("/missing-icon.png", "GET");
        handler.handle(missingExchange);
        assertEquals(404, missingExchange.getResponseCode());
    }

    @Test
    void iconServer_rejectsNonGetRequests() throws Exception {
        HttpIconServer handler = new HttpIconServer();
        TestHttpExchange exchange = new TestHttpExchange("/icon.png", "POST");
        handler.handle(exchange);
        assertEquals(405, exchange.getResponseCode());
        assertEquals("GET", exchange.getResponseHeaders().getFirst("Allow"));
    }

    private static final class UserDirScope implements AutoCloseable {
        private final String previousUserDir;

        private UserDirScope(Path userDir) {
            previousUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", userDir.toString());
        }

        static UserDirScope open(Path userDir) {
            return new UserDirScope(userDir);
        }

        String currentUserDir() {
            return System.getProperty("user.dir");
        }

        @Override
        public void close() {
            System.setProperty("user.dir", previousUserDir);
        }
    }
}
