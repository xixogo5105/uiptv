package com.uiptv.service

object AppDataRefreshService {
    fun refreshAfterDatabaseChange() {
        AccountService.refreshFromDatabase()
        BookmarkService.notifyBookmarksChanged()
        ConfigurationService.notifyConfigurationChanged()
    }
}
