package com.uiptv;

import com.uiptv.lightweight.LightweightApp;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.RootApplication;
import com.uiptv.util.AppLog;

public class Main {
    static void main(String[] args) {
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
}
