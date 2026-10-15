package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.stream.Stream;

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
        // Use forward slashes to avoid Properties.load() interpreting backslashes as escapes on Windows
        String absolutePath = tempDir.resolve("thumbnails").toAbsolutePath().toString().replace('\\', '/');
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.tmp.cache.dir=" + absolutePath + "\n");
        setConfigFilePath(ini.getAbsolutePath());
        // On Windows, getAbsolutePath() normalizes to backslashes; compare normalized
        String expected = new File(absolutePath).getAbsolutePath();
        assertEquals(expected, ConfigFileReader.getThumbnailTmpCacheDir());
    }

    @Test
    void getThumbnailCacheTtlReturnsDefaultInHoursWhenMissing(@TempDir Path tempDir) throws Exception {
        setConfigFilePath(tempDir.resolve("uiptv.ini").toString());
        assertEquals(7 * 24, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    @ParameterizedTest
    @MethodSource("ttlTestCases")
    void getThumbnailCacheTtlParsesVariousFormats(String input, int expectedHours, @TempDir Path tempDir) throws Exception {
        File ini = writeIni(tempDir.resolve("uiptv.ini"), "thumbnail.cache.ttl=" + input + "\n");
        setConfigFilePath(ini.getAbsolutePath());
        assertEquals(expectedHours, ConfigFileReader.getThumbnailCacheTtlHours());
    }

    static Stream<Arguments> ttlTestCases() {
        return Stream.of(
                Arguments.of("14", 14 * 24),
                Arguments.of("12h", 12),
                Arguments.of("6H", 6),
                Arguments.of("14 days", 14 * 24),
                Arguments.of("14D", 14 * 24),
                Arguments.of("14 d", 14 * 24),
                Arguments.of("0", 24),
                Arguments.of("0h", 1)
        );
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