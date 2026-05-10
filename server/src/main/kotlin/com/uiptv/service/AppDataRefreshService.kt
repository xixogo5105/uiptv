package com.uiptv.service

object AppDataRefreshService {
    @JvmStatic
    fun getInstance(): AppDataRefreshService = this
    fun refreshAfterDatabaseChange() {
        AccountService.getInstance().refreshFromDatabase()
        BookmarkService.getInstance().notifyBookmarksChanged()
        ConfigurationService.getInstance().notifyConfigurationChanged()
    }
}
