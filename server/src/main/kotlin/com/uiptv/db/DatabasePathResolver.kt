package com.uiptv.db

import com.uiptv.util.ConfigFileReader
import com.uiptv.util.StringUtils
import java.io.File

internal enum class DatabasePathSource {
    SYSTEM_PROPERTY,
    ENVIRONMENT,
    CONFIG_FILE,
    DEFAULT_HOME,
    RUNTIME_OVERRIDE
}

internal data class DatabasePathResolution(
    val path: String,
    val source: DatabasePathSource
)

internal object DatabasePathResolver {
    private const val DB_PATH_PROPERTY = "uiptv.db.path"
    private const val DB_PATH_ENV = "UIPTV_DB_PATH"
    private const val CONFIG_DB_PATH_KEY = "db.path"

    fun resolve(): String = resolvePath().path

    fun resolvePath(): DatabasePathResolution {
        val systemPath = System.getProperty(DB_PATH_PROPERTY)
        if (StringUtils.isNotBlank(systemPath)) {
            return DatabasePathResolution(systemPath!!, DatabasePathSource.SYSTEM_PROPERTY)
        }

        val envPath = System.getenv(DB_PATH_ENV)
        if (StringUtils.isNotBlank(envPath)) {
            return DatabasePathResolution(envPath!!, DatabasePathSource.ENVIRONMENT)
        }

        val configuredPath = resolveConfiguredPath()
        if (StringUtils.isNotBlank(configuredPath)) {
            return DatabasePathResolution(configuredPath!!, DatabasePathSource.CONFIG_FILE)
        }

        return DatabasePathResolution(defaultPath(), DatabasePathSource.DEFAULT_HOME)
    }

    private fun resolveConfiguredPath(): String? =
        ConfigFileReader.readProperty(CONFIG_DB_PATH_KEY)

    private fun defaultPath(): String =
        com.uiptv.util.Platform.getUserHomeDirPath() + File.separator + "uiptv.db"
}
