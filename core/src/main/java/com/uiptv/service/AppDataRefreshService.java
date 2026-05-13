package com.uiptv.service;

@SuppressWarnings("java:S6548")
public class AppDataRefreshService {
    private AppDataRefreshService() {
    }

    private static class SingletonHelper {
        private static final AppDataRefreshService INSTANCE = new AppDataRefreshService();
    }

    public static AppDataRefreshService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public void refreshAfterDatabaseChange() {
        AccountService.getInstance().refreshFromDatabase();
        BookmarkService.getInstance().notifyBookmarksChanged();
        ConfigurationService.getInstance().notifyConfigurationChanged();
    }
}
