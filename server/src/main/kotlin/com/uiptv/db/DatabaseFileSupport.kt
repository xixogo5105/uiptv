package com.uiptv.db

import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

internal object DatabaseFileSupport {
    @Throws(IOException::class)
    fun ensureDatabasePathReady(dbPath: String) {
        val databaseFile = File(dbPath)
        databaseFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Unable to create database directory: ${parent.absolutePath}")
            }
        }
        FileUtils.touch(databaseFile)
    }
}
