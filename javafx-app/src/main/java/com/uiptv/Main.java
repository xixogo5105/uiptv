package com.uiptv;

import com.uiptv.lightweight.LightweightApp;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.RootApplication;
import com.uiptv.util.AppLog;

public class Main {
    static void main(String[] args) {
        configureGraphicsPipeline();
        // Decide the log level before anything else. slf4j-simple fixes its level when the first
        // logger is created and caches it, so this has to happen before the configuration read
        // below can log, and before the lightweight path starts Jetty. The lightweight path never
        // reaches RootApplication.main, so without this it would keep slf4j's default of INFO and
        // print the server's startup banner on every launch.
        AppLog.setTerminalLoggingEnabled(AppLog.isShowLogsRequested(args));
        com.uiptv.application.EpgCacheRefreshService.startApplication();
        Runtime.getRuntime().addShutdownHook(new Thread(com.uiptv.application.EpgCacheRefreshService::stopForApplicationExit,"epg-cache-shutdown"));
        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration != null && configuration.isLightweightModeEnabled()) {
                LightweightApp.launch();
                return;
            }
        } catch (Exception e) {
            AppLog.addErrorLog(Main.class, e.getMessage());
        }
        RootApplication.main(args);
    }

    static void configureGraphicsPipeline() {
        // JavaFX 27 defaults to Metal on macOS. Live captures showed its renderer
        // stuck in nDrawIndexedQuads while the FX thread waited for the frame.
        // Select the supported OpenGL fallback before any UI toolkit starts,
        // including the lightweight window, and preserve explicit user choices.
        if (System.getProperty("os.name", "").startsWith("Mac")) {
            System.getProperties().putIfAbsent("prism.order", "es2,sw");
        }
    }
}
