package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.application.WatchingNowVodActionRequest;
import com.uiptv.model.Account;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.ServerUtils.readRequestBodyText;

public class HttpWatchingNowVodActionServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("POST".equalsIgnoreCase(method)) {
            upsert(ex);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            remove(ex);
            return;
        }
        ex.getResponseHeaders().set("Allow", "POST,DELETE");
        ex.sendResponseHeaders(405, -1);
    }

    private void upsert(HttpExchange ex) throws IOException {
        JSONObject body;
        try {
            body = readBodyJson(ex);
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJson(ex, 413, "{\"status\":\"error\",\"message\":\"Request body is too large\"}");
            return;
        }
        String accountId = opt(body, "accountId");
        String categoryId = opt(body, "categoryId");
        String vodId = opt(body, "vodId");
        String vodName = opt(body, "vodName");
        String vodCmd = opt(body, "vodCmd");
        String vodLogo = opt(body, "vodLogo");
        if (isBlank(accountId) || isBlank(vodId)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"accountId and vodId are required\"}");
            return;
        }
        Account account = WatchingNowApplicationService.getInstance().getAccount(accountId);
        if (account == null) {
            writeJson(ex, 404, "{\"status\":\"error\",\"message\":\"account not found\"}");
            return;
        }
        WatchingNowApplicationService.getInstance().saveVod(
                new WatchingNowVodActionRequest(accountId, categoryId, vodId, vodName, vodCmd, vodLogo),
                account
        );
        writeJson(ex, 200, "{\"status\":\"ok\"}");
    }

    private void remove(HttpExchange ex) throws IOException {
        JSONObject body;
        try {
            body = readBodyJson(ex);
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJson(ex, 413, "{\"status\":\"error\",\"message\":\"Request body is too large\"}");
            return;
        }
        String accountId = opt(body, "accountId");
        String categoryId = opt(body, "categoryId");
        String vodId = opt(body, "vodId");
        if (isBlank(accountId) || isBlank(vodId)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"accountId and vodId are required\"}");
            return;
        }
        WatchingNowApplicationService.getInstance().removeVod(accountId, categoryId, vodId);
        writeJson(ex, 200, "{\"status\":\"ok\"}");
    }

    private JSONObject readBodyJson(HttpExchange ex) throws IOException {
        String body = readRequestBodyText(ex);
        if (body.isBlank()) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    private String opt(JSONObject body, String key) {
        if (body == null || !body.has(key) || body.isNull(key)) {
            return "";
        }
        return String.valueOf(body.opt(key)).trim();
    }

    private void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST,DELETE,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }
}
