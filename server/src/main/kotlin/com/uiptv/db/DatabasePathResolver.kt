package com.uiptv.db

import com.uiptv.util.Platform
import com.uiptv.util.StringUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

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
    private const val CONFIG_PATH_PROPERTY = "uiptv.config.path"
    private const val CONFIG_PATH_ENV = "UIPTV_CONFIG_PATH"
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

    private fun resolveConfiguredPath(): String? {
        val properties = Properties()
        return try {
            FileInputStream(resolveConfigFilePath()).use { inputStream ->
                properties.load(inputStream)
                properties.getProperty(CONFIG_DB_PATH_KEY)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun resolveConfigFilePath(): String {
        val systemPath = System.getProperty(CONFIG_PATH_PROPERTY)
        if (!systemPath.isNullOrBlank()) {
            return systemPath
        }

        val envPath = System.getenv(CONFIG_PATH_ENV)
        if (!envPath.isNullOrBlank()) {
            return envPath
        }

        return Platform.getUserHomeDirPath() + File.separator + "uiptv.ini"
    }

    private fun defaultPath(): String =
        Platform.getUserHomeDirPath() + File.separator + "uiptv.db"
}
