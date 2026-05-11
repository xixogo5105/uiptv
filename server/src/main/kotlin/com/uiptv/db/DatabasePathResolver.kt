package com.uiptv.db

import com.uiptv.util.ConfigFileReader
import com.uiptv.util.Platform
import com.uiptv.util.StringUtils
import java.io.File

internal object DatabasePathResolver {
    private const val DB_PATH_PROPERTY = "uiptv.db.path"
    private const val DB_PATH_ENV = "UIPTV_DB_PATH"
    private const val CONFIG_DB_PATH_KEY = "db.path"

    fun resolve(): String {
        val systemPath = System.getProperty(DB_PATH_PROPERTY)
        if (StringUtils.isNotBlank(systemPath)) {
            return systemPath!!
        }

        val envPath = System.getenv(DB_PATH_ENV)
        if (StringUtils.isNotBlank(envPath)) {
            return envPath!!
        }

        val configuredPath = ConfigFileReader.readProperty(CONFIG_DB_PATH_KEY)
        if (StringUtils.isNotBlank(configuredPath)) {
            return configuredPath!!
        }

        return Platform.getUserHomeDirPath() + File.separator + "uiptv.db"
    }
}
