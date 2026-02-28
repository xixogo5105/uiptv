package com.uiptv.util;

import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;

public class ServerUrlUtil {

    public static String getLocalServerUrl() {
        String port = "8888";
        try {
            ConfigurationService service = ConfigurationService.getInstance();
            if (service != null) {
                Configuration config = service.read();
                if (config != null) {
                    String configured = config.getServerPort();
                    if (configured != null && !configured.trim().isEmpty()) {
                        port = configured.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "http://127.0.0.1:" + port;
    }
}
