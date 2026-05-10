package com.uiptv.util

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

object ConfigFileReader {
    private val CONFIG_FILE_PATH = Platform.getUserHomeDirPath() + File.separator + "uiptv.ini"

    @JvmStatic
    fun getDbPathFromConfigFile(): String? {
        val properties = Properties()
        return try {
            FileInputStream(CONFIG_FILE_PATH).use { inputStream ->
                properties.load(inputStream)
                properties.getProperty("db.path")
            }
        } catch (_: IOException) {
            null
        }
    }
}
