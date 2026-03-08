package com.uiptv.util;

import com.uiptv.server.UIptvServer;
import com.uiptv.db.DatabaseAccessException;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;
import com.uiptv.model.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.util.StringUtils.isNotBlank;

public class ServerUrlUtil {
    private static final AtomicReference<HostServices> hostServices = new AtomicReference<>();

    private ServerUrlUtil() {
    }

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
        } catch (DatabaseAccessException | IllegalStateException _) {
            // Fall back to the default local server port when configuration cannot be read.
        }
        return "http://127.0.0.1:" + port;
    }

    public static void setHostServices(HostServices hostServicesInstance) {
        hostServices.set(hostServicesInstance);
    }

    public static void installServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(ServerUrlUtil::stopServerWithShutdownMessage));
    }

    public static void startServer() {
        try {
            UIptvServer.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start local web server", e);
        }
    }

    public static boolean ensureServerForWebPlayback() {
        try {
            UIptvServer.ensureStarted();
            return true;
        } catch (IOException | IllegalStateException e) {
            UIptvAlert.showErrorKey("serverUnableToStartLocalWebServerForPlayback", e);
            return false;
        }
    }

    public static void stopServerWithShutdownMessage() {
        UIptvServer.stop();
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
