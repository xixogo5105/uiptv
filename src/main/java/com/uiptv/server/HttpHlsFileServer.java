package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.FfmpegService;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.uiptv.util.ServerUtils.CONTENT_TYPE_M3U8;
import static com.uiptv.util.ServerUtils.CONTENT_TYPE_TS;

public class HttpHlsFileServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        File file = new File(FfmpegService.getInstance().getOutputDir(), fileName);

        if (!file.exists()) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String contentType = fileName.endsWith(".m3u8") ? CONTENT_TYPE_M3U8 : CONTENT_TYPE_TS;
        
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, file.length());

        try (OutputStream os = ex.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            IOUtils.copy(fis, os);
        }
    }
}
