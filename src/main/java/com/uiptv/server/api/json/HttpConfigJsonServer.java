package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.ConfigurationService;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpConfigJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JSONObject response = new JSONObject();
        response.put("enableThumbnails", ConfigurationService.getInstance().read().isEnableThumbnails());
        generateJsonResponse(exchange, response.toString());
    }
}
