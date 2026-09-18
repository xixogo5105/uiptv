package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static com.uiptv.util.Platform.getUserHomeDirPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigFileReaderTest {

    @AfterEach
    void tearDown() throws Exception {
        Field field = ConfigFileReader.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        field.set(null, getUserHomeDirPath() + File.separator + "uiptv.ini");
    }

    @Test
    void getDbPathFromConfigFileReturnsNullWhenIniMissing(@TempDir Path tempDir) throws Exception {
        setConfigFilePath(tempDir.resolve("missing.ini").toString());
        assertNull(ConfigFileReader.getDbPathFromConfigFile());
    }

    @Test
    void getDbPathFromConfigFileReadsDbPath(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), """
                db.path=/external/ssd/uiptv.db
                """);
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals("/external/ssd/uiptv.db", ConfigFileReader.getDbPathFromConfigFile());
    }

    @Test
    void getThumbnailTmpCacheDirReturnsNullWhenKeyMissing(@TempDir Path tempDir) throws Exception {
        setConfigFilePath(tempDir.resolve("uiptv.ini").toString());
        assertNull(ConfigFileReader.getThumbnailTmpCacheDir());
    }

    @Test
    void getThumbnailTmpCacheDirReturnsNullWhenRelativePath(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), """
                thumbnail.tmp.cache.dir=relative/path
                """);
        setConfigFilePath(ini.getAbsolutePath());
        assertNull(ConfigFileReader.getThumbnailTmpCacheDir());
    }

    @Test
    void getThumbnailTmpCacheDirReturnsAbsolutePath(@TempDir Path tempDir) throws Exception {
        // Use a platform-appropriate absolute path
        String absolutePath = tempDir.resolve("thumbnails").toAbsolutePath().toString();
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.tmp.cache.dir=" + absolutePath + "\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(absolutePath, ConfigFileReader.getThumbnailTmpCacheDir());
    }

    @Test
    void getThumbnailCacheTtlReturnsDefaultInHoursWhenMissing(@TempDir Path tempDir) throws Exception {
        setConfigFilePath(tempDir.resolve("uiptv.ini").toString());
        assertEquals(7 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlInterpretsBareNumberAsDays(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=14\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(14 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlInterpretsLowercaseHAsHours(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=12h\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(12, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlInterpretsUppercaseHAsHours(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=6H\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(6, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlSanitizesWordsAfterNumberAsDays(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=14 days\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(14 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlSanitizesSuffixDAsDays(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=14D\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(14 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlSanitizesSpaceBeforeDAsDays(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=14 d\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(14 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlClampsDaysToMinimum(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=0\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlClampsHoursToMinimum(@TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=0h\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(1, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @Test
    void getThumbnailCacheTtlReturnsDefaultWhenFileCorrupt(@TempDir Path tempDir) throws Exception {
        File ini = new File(tempDir.toFile(), "uiptv.ini");
        try (FileWriter writer = new FileWriter(ini)) {
            writer.write("this is not valid ini content {[\n");
        }
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(7 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    private static void setConfigFilePath(String path) throws Exception {
        Field field = ConfigFileReader.class.getDeclaredField("configFilePath");
        field.setAccessible(true);
        field.set(null, path);
    }

    private static File writeIni(Path path, String content) throws Exception {
        File ini = path.toFile();
        try (FileWriter writer = new FileWriter(ini)) {
            writer.write(content);
        }
        return ini;
    }
}
