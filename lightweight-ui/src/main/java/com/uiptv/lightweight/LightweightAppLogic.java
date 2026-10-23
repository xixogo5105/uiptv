package com.uiptv.lightweight;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.ThemeMode;
import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;

/** Plain application decisions shared by the lightweight window and headless tests. */
final class LightweightAppLogic {
    private LightweightAppLogic() {
    }

    record ServerStatus(boolean running, String httpPort, boolean httpsEnabled, String httpsPort) {
    }

    static ServerStatus readServerStatus(ConfigurationApplicationService service) {
        return new ServerStatus(
                service.isServerRunning(),
                ServerUrlUtil.getConfiguredServerPort(),
                ServerUrlUtil.isHttpsServerEnabled(),
                ServerUrlUtil.getConfiguredHttpsServerPort());
    }

    static void toggleServer(ConfigurationApplicationService service) throws IOException {
        if (service.isServerRunning()) {
            service.stopServer();
        } else {
            service.startServer();
        }
    }

    static boolean shouldShowClearButton(boolean logsVisible, int entryCount) {
        return logsVisible || entryCount > 0;
    }

    static boolean isDarkTheme(ThemeMode mode, boolean systemDark) {
        return mode == ThemeMode.DARK || (mode.isSystem() && systemDark);
    }
}
