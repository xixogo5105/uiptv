package com.uiptv.server.api.json;

import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.StringUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpChannelJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
        Category category = CategoryDb.get().getCategoryByDbId(getParam(ex, "categoryId"), account);

        String response = StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account);
        generateJsonResponse(ex, response);
    }
}
