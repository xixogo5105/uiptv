package com.uiptv.application;

import com.uiptv.util.ConfigFileReader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class EpgDiskCacheTest {
    @TempDir Path temp;
    private Object original;
    @BeforeEach void configure() throws Exception {
        var field = ConfigFileReader.class.getDeclaredField("configFilePath"); field.setAccessible(true); original = field.get(null);
        Path ini = temp.resolve("uiptv.ini");
        Files.writeString(ini, "epg.tmp.cache.dir=" + temp.resolve("epg").toString().replace('\\', '/') + "\nepg.cache.ttl=24h\n");
        field.set(null, ini.toString());
    }
    @AfterEach void restore() throws Exception {
        var field = ConfigFileReader.class.getDeclaredField("configFilePath"); field.setAccessible(true); field.set(null, original);
    }
    @Test void persistedXmlSurvivesReloadExpiresAndRefreshClearsOnlyOwnedFiles() throws Exception {
        String source = "https://example.test/guide?secret=private";
        String xml = "<tv><channel id='station'><display-name>Station</display-name></channel></tv>";
        EpgDiskCache.parseAndSave(source, new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path cached = EpgDiskCache.path(source);
        assertFalse(cached.toString().contains("private"));
        assertNotNull(EpgDiskCache.read(source, false));
        Files.setLastModifiedTime(cached, FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        assertNull(EpgDiskCache.read(source, false));
        assertNotNull(EpgDiskCache.read(source, true));
        assertThrows(Exception.class, () -> EpgDiskCache.parseAndSave(source, new ByteArrayInputStream("invalid XML".getBytes())));
        assertEquals(xml, Files.readString(cached));
        Path unrelated = cached.getParent().resolve("keep.txt"); Files.writeString(unrelated, "keep");
        new EpgApplicationService().clearCache();
        assertFalse(Files.exists(cached)); assertTrue(Files.exists(unrelated));
    }
}
