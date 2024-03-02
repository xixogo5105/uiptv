package com.uiptv.server;

import com.uiptv.util.StringUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;

import static com.uiptv.util.Platform.getWebServerRootPath;
import static com.uiptv.util.ServerUtils.generateCssResponse;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpCssServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String fileId = getWebServerRootPath() + ex.getRequestURI().getPath();
        generateCssResponse(ex, StringUtils.EMPTY + IOUtils.toString(new FileInputStream(fileId), UTF_8), fileId);
    }
}
