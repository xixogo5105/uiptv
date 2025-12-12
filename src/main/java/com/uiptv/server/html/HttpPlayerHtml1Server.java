package com.uiptv.server.html;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static com.uiptv.util.Platform.getWebServerRootPath;
import static com.uiptv.util.ServerUtils.generateHtmlResponse;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpPlayerHtml1Server implements HttpHandler {
    public static final String CHANNEL_HTML_TEMPLATE = getWebServerRootPath() + File.separator + "play1.html";

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        generateHtmlResponse(httpExchange, StringUtils.EMPTY + IOUtils.toString(new FileInputStream(CHANNEL_HTML_TEMPLATE), UTF_8));
    }
}
