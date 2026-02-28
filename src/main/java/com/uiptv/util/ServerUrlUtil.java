package com.uiptv.util;

import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;
import com.uiptv.model.Configuration;

import java.io.IOException;

import static com.uiptv.util.StringUtils.isNotBlank;

public class ServerUrlUtil {
    private static volatile HostServices hostServices;

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

    public static void setHostServices(HostServices hostServicesInstance) {
        hostServices = hostServicesInstance;
    }

    public static void installServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerUrlUtil::stopServerWithShutdownMessage));
    }

    public static void startServer() {
        try {
            UIptvServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean ensureServerForWebPlayback() {
        try {
            UIptvServer.ensureStarted();
            return true;
        } catch (Exception e) {
            UIptvAlert.showError("Unable to start local web server for playback.", e);
            return false;
        }
    }

    public static void stopServerWithShutdownMessage() {
        try {
            UIptvServer.stop();
            UIptvAlert.showMessage("UIPTV Shutting down");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void openInBrowser(String url) {
        if (isNotBlank(url) && hostServices != null) {
            hostServices.showDocument(url);
            return;
        }
        UIptvAlert.showError("Unable to open browser for DRM playback.");
    }
}
