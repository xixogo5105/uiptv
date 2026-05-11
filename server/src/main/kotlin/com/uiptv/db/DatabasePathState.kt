package com.uiptv.db

internal object DatabasePathState {
    @Volatile
    private var currentPath: String = DatabasePathResolver.resolve()

    @JvmStatic
    fun currentPath(): String = currentPath

    @JvmStatic
    @Synchronized
    fun override(path: String) {
        currentPath = path
    }
}
