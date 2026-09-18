package com.uiptv;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.RootApplication;

public class Main {
    static void main(String[] args) {
        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration != null && configuration.isLightweightModeEnabled()) {
                Class<?> lightweightApp = Class.forName("com.uiptv.lightweight.LightweightApp");
                java.lang.reflect.Method launch = lightweightApp.getMethod("launch", String[].class);
                launch.invoke(null, (Object) args);
                return;
            }
        } catch (Exception e) {
            // Fallback to full desktop mode if lightweight module is unavailable
        }
        RootApplication.main(args);
    }
}
