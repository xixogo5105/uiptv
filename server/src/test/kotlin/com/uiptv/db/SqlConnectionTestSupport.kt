package com.uiptv.db

object SqlConnectionTestSupport {
    private const val DB_PATH_PROPERTY = "uiptv.db.path"

    @JvmStatic
    @Synchronized
    fun useDatabasePath(path: String) {
        System.setProperty(DB_PATH_PROPERTY, path)
        SqlConnectionRuntime.close()
        DatabasePathState.override(path)
        SqlConnectionRuntime.initialize()
    }

    @JvmStatic
    @Synchronized
    fun restoreConfiguredPath() {
        System.clearProperty(DB_PATH_PROPERTY)
        SqlConnectionRuntime.close()
        DatabasePathState.reload()
        SqlConnectionRuntime.initialize()
    }

    @JvmStatic
    @Synchronized
    fun reinitialize() {
        SqlConnectionRuntime.close()
        SqlConnectionRuntime.initialize()
    }

    @JvmStatic
    @Synchronized
    fun shutdown() {
        SqlConnectionRuntime.close()
    }
}
