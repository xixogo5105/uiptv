package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.uiptv.util.Platform.getWebServerRootPath;

public class HttpIconServer implements HttpHandler {
    // Assuming the icon is in the resources folder or web root. 
    // Since getWebServerRootPath points to 'web', and the user said src/main/resources/icon.ico,
    // I might need to adjust. But usually resources are copied or I need to find the path.
    // For now, I'll assume it's copied to web root or I'll try to read from classpath if possible, 
    // but this server seems to serve files from disk.
    // Let's assume the user puts 'icon.png' in the 'web' folder for simplicity, 
    // or I'll try to read the specific path provided if I can access it.
    // The user provided: /Volumes/backup/code/uiptv/src/main/resources/icon.ico
    
    private static final String ICON_PATH = "/Volumes/backup/code/uiptv/src/main/resources/icon.ico";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try (FileInputStream fis = new FileInputStream(ICON_PATH)) {
            byte[] bytes = IOUtils.toByteArray(fis);
            ex.getResponseHeaders().add("Content-Type", "image/x-icon");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            // Fallback or 404
            ex.sendResponseHeaders(404, -1);
        }
    }
}
