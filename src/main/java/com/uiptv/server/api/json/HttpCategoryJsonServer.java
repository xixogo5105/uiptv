package com.uiptv.server.api.json;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.StringUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpCategoryJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
        String response = StringUtils.EMPTY + CategoryService.getInstance().readToJson(account);
        generateJsonResponse(ex, response);
    }
}
