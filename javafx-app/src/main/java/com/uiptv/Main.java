package com.uiptv;

import com.uiptv.lightweight.LightweightApp;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.RootApplication;

public class Main {
    static void main(String[] args) {
        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration != null && configuration.isLightweightModeEnabled()) {
                LightweightApp.launch(args);
                return;
            }
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
        RootApplication.main(args);
    }
}
