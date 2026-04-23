package com.uiptv.util;

import com.uiptv.server.UIptvServer;
import com.uiptv.db.DatabaseAccessException;
import com.uiptv.service.ConfigurationService;
import com.uiptv.model.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;

public class ServerUrlUtil {

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

    public static void installServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(UIptvServer::stop));
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
            UIptvServer.start();
            return true;
        } catch (IOException e) {
            AppLog.addErrorLog(ServerUrlUtil.class, "Unable to start local web server for playback: " + e.getMessage());
            return false;
        }
    }
}
