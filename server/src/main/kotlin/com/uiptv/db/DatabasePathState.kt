package com.uiptv.db

internal object DatabasePathState {
    @Volatile
    private var resolution: DatabasePathResolution = DatabasePathResolver.resolvePath()

    @JvmStatic
    fun currentPath(): String = resolution.path

    @JvmStatic
    fun currentSource(): DatabasePathSource = resolution.source

    @JvmStatic
    @Synchronized
    fun reload() {
        resolution = DatabasePathResolver.resolvePath()
    }

    @JvmStatic
    @Synchronized
    fun override(path: String) {
        resolution = DatabasePathResolution(path, DatabasePathSource.RUNTIME_OVERRIDE)
    }
}
