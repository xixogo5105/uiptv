package com.uiptv.ui.util;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.util.StringUtils.isNotBlank;

public class UiServerUrlUtil {
    private static final AtomicReference<HostServices> hostServices = new AtomicReference<>();
    private static final ConfigurationApplicationService configurationApplicationService = ConfigurationApplicationService.getInstance();

    private UiServerUrlUtil() {
    }

    public static void setHostServices(HostServices hostServicesInstance) {
        hostServices.set(hostServicesInstance);
    }

    public static boolean ensureServerForWebPlayback() {
        return ensureServerForWebPlayback(null);
    }

    public static boolean ensureServerForWebPlayback(String friendlyFailureMessage) {
        try {
            configurationApplicationService.ensureServerStarted();
            return true;
        } catch (IOException | IllegalStateException e) {
            if (isNotBlank(friendlyFailureMessage)) {
                UIptvAlert.showErrorAlert(friendlyFailureMessage, e);
            } else {
                UIptvAlert.showErrorKey("serverUnableToStartLocalWebServerForPlayback", e);
            }
            return false;
        }
    }

    public static void stopServerWithShutdownMessage() {
        configurationApplicationService.stopServer();
        UIptvAlert.showMessageKey("serverShuttingDown");
    }

    public static void openInBrowser(String url) {
        HostServices services = hostServices.get();
        if (isNotBlank(url) && services != null) {
            services.showDocument(url);
            return;
        }
        UIptvAlert.showErrorKey("serverUnableToOpenBrowserForDrmPlayback");
    }
}
