package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.AccountApplicationService;
import com.uiptv.util.StringUtils;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.objectToJson;
import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpAccountJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String response = StringUtils.EMPTY + objectToJson(AccountApplicationService.getInstance().listAccounts());
        generateJsonResponse(httpExchange, response);
    }
}
