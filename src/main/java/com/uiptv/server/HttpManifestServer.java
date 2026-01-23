package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;

import static com.uiptv.util.Platform.getWebServerRootPath;
import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpManifestServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String fileId = getWebServerRootPath() + ex.getRequestURI().getPath();
        generateJsonResponse(ex, StringUtils.EMPTY + IOUtils.toString(new FileInputStream(fileId), UTF_8));
    }
}
