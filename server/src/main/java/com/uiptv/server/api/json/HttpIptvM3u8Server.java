package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.M3U8PublicationService;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateM3u8Response;

public class HttpIptvM3u8Server implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String response = M3U8PublicationService.getInstance().getPublishedM3u8();
        String path = ex.getRequestURI().getPath();
        String filename = "iptv.m3u8";
        if (path.endsWith(".m3u")) {
            filename = "iptv.m3u";
        }
        generateM3u8Response(ex, response, filename);
    }
}