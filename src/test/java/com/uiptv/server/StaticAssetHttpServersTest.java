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
    void iconServer_servesIcon_andHandlesMissingIcon() throws Exception {
        HttpIconServer handler = new HttpIconServer();
        TestHttpExchange exchange = new TestHttpExchange("/icon.ico", "GET");
        handler.handle(exchange);
        assertEquals(200, exchange.getResponseCode());
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("image/x-icon"));
        assertTrue(exchange.getResponseBodyBytes().length > 0);

        Path iconPath = Path.of("src", "main", "resources", "icon.ico");
        Path tempPath = iconPath.resolveSibling("icon.ico.bak");
        Files.move(iconPath, tempPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            TestHttpExchange missingExchange = new TestHttpExchange("/icon.ico", "GET");
            handler.handle(missingExchange);
            assertEquals(404, missingExchange.getResponseCode());
        } finally {
            Files.move(tempPath, iconPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
