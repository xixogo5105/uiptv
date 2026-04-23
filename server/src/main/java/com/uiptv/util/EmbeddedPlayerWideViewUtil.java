package com.uiptv.util;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;

public final class EmbeddedPlayerWideViewUtil {
    private EmbeddedPlayerWideViewUtil() {
    }

    public static boolean isWideViewEnabled() {
        Configuration configuration = ConfigurationService.getInstance().read();
        return configuration != null && configuration.isEmbeddedPlayer() && configuration.isWideView();
    }
}
