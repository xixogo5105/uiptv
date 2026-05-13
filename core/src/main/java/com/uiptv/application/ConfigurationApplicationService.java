package com.uiptv.application;

import com.uiptv.db.SQLConnection;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.ThemeCssOverrideService;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;

public class ConfigurationApplicationService {
    private final ConfigurationService configurationService = ConfigurationService.getInstance();
    private final ThemeCssOverrideService themeCssOverrideService = ThemeCssOverrideService.getInstance();

    private ConfigurationApplicationService() {
    }

    public static ConfigurationApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public Configuration readConfiguration() {
        return configurationService.read();
    }

    public void saveConfiguration(Configuration configuration) {
        configurationService.save(configuration);
    }

    public ThemeCssOverride readThemeOverrides() {
        return themeCssOverrideService.read();
    }

    public void saveThemeOverrides(ThemeCssOverride override) {
        themeCssOverrideService.save(override);
    }

    public String getDatabasePath() {
        return SQLConnection.getDatabasePath();
    }

    public boolean isServerRunning() {
        return ServerUrlUtil.isServerRunning();
    }

    public void startServer() throws IOException {
        ServerUrlUtil.startServerChecked();
    }

    public void stopServer() {
        ServerUrlUtil.stopServer();
    }

    public boolean ensureServerStarted() throws IOException {
        return ServerUrlUtil.ensureServerStarted();
    }

    public void clearWatchingNowState() {
        SeriesWatchStateService.getInstance().clearAllSeriesLastWatched();
        SeriesWatchingNowSnapshotService.getInstance().clearAll();
        VodWatchStateService.getInstance().clearAll();
    }

    private static class SingletonHelper {
        private static final ConfigurationApplicationService INSTANCE = new ConfigurationApplicationService();
    }
}
