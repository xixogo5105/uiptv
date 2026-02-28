package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.util.StringUtils;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpCategoryJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, "[]");
            return;
        }
        applyMode(account, getParam(ex, "mode"));
        String response = StringUtils.EMPTY + CategoryService.getInstance().readToJson(account);
        generateJsonResponse(ex, response);
    }

    private void applyMode(Account account, String mode) {
        if (account == null || !isNotBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception ignored) {
            account.setAction(Account.AccountAction.itv);
        }
    }
}
