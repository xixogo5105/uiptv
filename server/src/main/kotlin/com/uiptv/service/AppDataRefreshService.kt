package com.uiptv.service

object AppDataRefreshService {
    @JvmStatic
    fun getInstance(): AppDataRefreshService = this
    fun refreshAfterDatabaseChange() {
        AccountService.refreshFromDatabase()
        BookmarkService.notifyBookmarksChanged()
        ConfigurationService.notifyConfigurationChanged()
    }
}
