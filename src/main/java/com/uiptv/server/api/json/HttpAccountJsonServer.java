package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.AccountService;
import com.uiptv.util.StringUtils;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpAccountJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String response = StringUtils.EMPTY + AccountService.getInstance().readToJson();
        generateJsonResponse(httpExchange, response);
    }
}
