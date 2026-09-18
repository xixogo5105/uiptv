package com.uiptv;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.RootApplication;
import javafx.application.Application;

public class Main {
    static void main(String[] args) {
        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration != null && configuration.isLightweightModeEnabled()) {
                Class<?> appClass = Class.forName("com.uiptv.lightweight.LightweightApp");
                java.lang.reflect.Method launch = Application.class.getMethod("launch", Class.class, String[].class);
                launch.invoke(null, appClass, (Object) args);
                return;
            }
        } catch (Exception e) {
            System.out.print(e.getMessage());
        }
        RootApplication.main(args);
    }
}
