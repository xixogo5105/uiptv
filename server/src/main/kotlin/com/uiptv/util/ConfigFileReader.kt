package com.uiptv.util

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

object ConfigFileReader {
    private const val CONFIG_PATH_PROPERTY = "uiptv.config.path"
    private const val CONFIG_PATH_ENV = "UIPTV_CONFIG_PATH"

    @JvmStatic
    fun getDbPathFromConfigFile(): String? {
        return readProperty("db.path")
    }

    @JvmStatic
    fun readProperty(key: String): String? {
        val properties = Properties()
        return try {
            FileInputStream(resolveConfigFilePath()).use { inputStream ->
                properties.load(inputStream)
                properties.getProperty(key)
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
}
