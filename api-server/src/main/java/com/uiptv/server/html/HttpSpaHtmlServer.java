package com.uiptv.server.html;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.uiptv.util.Platform.getWebServerRootPath;
import static com.uiptv.util.ServerUtils.generateHtmlResponse;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpSpaHtmlServer implements HttpHandler {
    public static final String SPA_HTML_TEMPLATE = getWebServerRootPath() + File.separator + "index.html";
    private final String templatePath;

    public HttpSpaHtmlServer() {
        this("index.html");
    }

    public HttpSpaHtmlServer(String htmlFileName) {
        this.templatePath = getWebServerRootPath() + File.separator + htmlFileName;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        // This handler is registered on "/", "/index.html" and "/drm.html", so it serves every
        // SPA page load, deep link, 404 and missing favicon. The stream must be closed: leaking
        // one descriptor per request eventually exhausts the process file-descriptor limit and
        // the web UI starts failing with "Too many open files", which escapes into the servlet
        // container as an opaque 500.
        String html;
        try (InputStream in = new FileInputStream(templatePath)) {
            html = IOUtils.toString(in, UTF_8);
        }
        generateHtmlResponse(httpExchange, StringUtils.EMPTY + html);
    }
}
