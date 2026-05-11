package com.uiptv.db

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DatabasePathResolverTest {
    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun clearProperties() {
        System.clearProperty("uiptv.db.path")
        System.clearProperty("uiptv.config.path")
        System.clearProperty("user.home")
    }

    @Test
    fun resolvePath_prefersSystemProperty() {
        System.setProperty("uiptv.db.path", "/tmp/from-property.db")

        val resolution = DatabasePathResolver.resolvePath()

        assertEquals(DatabasePathSource.SYSTEM_PROPERTY, resolution.source)
        assertEquals("/tmp/from-property.db", resolution.path)
    }

    @Test
    fun resolvePath_usesConfiguredIniWhenPresent() {
        val configFile = tempDir.resolve("uiptv.ini")
        Files.writeString(configFile, "db.path=/tmp/from-config.db\n")
        System.setProperty("uiptv.config.path", configFile.toString())

        val resolution = DatabasePathResolver.resolvePath()

        assertEquals(DatabasePathSource.CONFIG_FILE, resolution.source)
        assertEquals("/tmp/from-config.db", resolution.path)
    }

    @Test
    fun resolvePath_systemPropertyOverridesConfiguredIni() {
        val configFile = tempDir.resolve("uiptv.ini")
        Files.writeString(configFile, "db.path=/tmp/from-config.db\n")
        System.setProperty("uiptv.config.path", configFile.toString())
        System.setProperty("uiptv.db.path", "/tmp/from-property.db")

        val resolution = DatabasePathResolver.resolvePath()

        assertEquals(DatabasePathSource.SYSTEM_PROPERTY, resolution.source)
        assertEquals("/tmp/from-property.db", resolution.path)
    }

    @Test
    fun resolvePath_fallsBackToDefaultHomePath() {
        System.setProperty("user.home", tempDir.toString())

        val resolution = DatabasePathResolver.resolvePath()

        assertEquals(DatabasePathSource.DEFAULT_HOME, resolution.source)
        assertEquals(tempDir.resolve("uiptv").resolve("uiptv.db").toString(), resolution.path)
    }

    @Test
    fun resolvePath_ignoresUnreadableOrMissingConfigFile() {
        System.setProperty("user.home", tempDir.toString())
        System.setProperty("uiptv.config.path", tempDir.resolve("missing.ini").toString())

        val resolution = DatabasePathResolver.resolvePath()

        assertEquals(DatabasePathSource.DEFAULT_HOME, resolution.source)
        assertEquals(tempDir.resolve("uiptv").resolve("uiptv.db").toString(), resolution.path)
    }
}
