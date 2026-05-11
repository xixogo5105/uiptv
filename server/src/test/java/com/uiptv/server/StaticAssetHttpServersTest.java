package com.uiptv.server;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StaticAssetHttpServersTest {

    @Test
    void cssJavascriptManifestHandlers_returnContent() throws Exception {
        String css = WebStaticContentSupport.INSTANCE.readStaticUtf8("/css/uiptv.css");
        assertFalse(css.isBlank());

        String js = WebStaticContentSupport.INSTANCE.readStaticUtf8("/javascript/spa.js");
        assertFalse(js.isBlank());

        String manifest = WebStaticContentSupport.INSTANCE.readStaticUtf8("/manifest.json");
        assertTrue(manifest.contains("name"));
    }

    @Test
    void staticHandlers_return404ForMissingFiles() throws Exception {
        assertThrows(Exception.class, () -> WebStaticContentSupport.INSTANCE.readStaticUtf8("/css/missing.css"));
        assertThrows(Exception.class, () -> WebStaticContentSupport.INSTANCE.readStaticUtf8("/javascript/missing.js"));
        assertThrows(Exception.class, () -> WebStaticContentSupport.INSTANCE.readStaticUtf8("/manifest-missing.json"));
    }

    @Test
    void spaHtmlServer_rendersTemplate() throws Exception {
        String html = WebStaticContentSupport.INSTANCE.readSpaHtml("index.html");
        assertTrue(html.toLowerCase().contains("<html"));
    }

    @Test
    void iconServer_servesIcon_andHandlesMissingIcon() throws Exception {
        Path iconPath = Path.of(com.uiptv.util.Platform.getWebServerRootPath(), "icon.ico");
        if (Files.exists(iconPath)) {
            assertTrue(WebStaticContentSupport.INSTANCE.readIconBytes().length > 0);
        } else {
            assertThrows(Exception.class, () -> WebStaticContentSupport.INSTANCE.readIconBytes());
        }
    }
}
