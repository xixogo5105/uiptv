package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.objectToJson;

public class HttpBookmarksJsonServer implements HttpHandler {
    private static final String ALLOWED_METHODS = "GET,POST,PUT,DELETE,OPTIONS";
    private static final String PARAM_BOOKMARK_IDS = "bookmarkIds";
    private static final String PARAM_BOOKMARK_ORDERS = "bookmarkOrders";
    private static final String PARAM_CATEGORY_ID = "categoryId";
    private static final String PARAM_ORDERED_BOOKMARK_DB_IDS = "orderedBookmarkDbIds";
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", ALLOWED_METHODS);
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,*");
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if ("GET".equalsIgnoreCase(method)) {
            if ("categories".equalsIgnoreCase(queryParam(ex, "view"))) {
                generateJsonResponse(ex, objectToJson(BookmarkService.getInstance().getAllCategories()));
                return;
            }
            generateJsonResponse(ex, BookmarkService.getInstance().readToJson());
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            upsertBookmark(ex);
            return;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            updateBookmarkOrder(ex);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            deleteBookmark(ex);
            return;
        }
        ex.getResponseHeaders().set("Allow", ALLOWED_METHODS);
        ex.sendResponseHeaders(405, -1);
    }

    private void updateBookmarkOrder(HttpExchange ex) throws IOException {
        JSONObject body = readBodyJson(ex);
        Map<String, Integer> bookmarkOrders = extractBookmarkOrders(body);

        if (bookmarkOrders.isEmpty()) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"bookmarkOrders is required\"}");
            return;
        }

        BookmarkService.getInstance().saveBookmarkOrders(bookmarkOrders);
        writeJson(ex, 200, "{\"status\":\"ok\",\"action\":\"reordered\"}");
    }

    private Map<String, Integer> extractBookmarkOrders(JSONObject body) {
        Map<String, Integer> bookmarkOrders = extractExplicitBookmarkOrders(body);
        if (!bookmarkOrders.isEmpty()) {
            return bookmarkOrders;
        }

        List<String> orderedDbIds = extractOrderedBookmarkIds(body);
        for (int i = 0; i < orderedDbIds.size(); i++) {
            bookmarkOrders.put(orderedDbIds.get(i), i + 1);
        }
        return bookmarkOrders;
    }

    private Map<String, Integer> extractExplicitBookmarkOrders(JSONObject body) {
        Map<String, Integer> bookmarkOrders = new LinkedHashMap<>();
        if (body == null || !body.has(PARAM_BOOKMARK_ORDERS) || body.isNull(PARAM_BOOKMARK_ORDERS)) {
            return bookmarkOrders;
        }

        JSONObject ordersObject = body.optJSONObject(PARAM_BOOKMARK_ORDERS);
        if (ordersObject == null) {
            return bookmarkOrders;
        }

        for (String bookmarkId : ordersObject.keySet()) {
            if (isBlank(bookmarkId)) {
                continue;
            }
            int orderNumber = ordersObject.optInt(bookmarkId, -1);
            if (orderNumber > 0) {
                bookmarkOrders.put(bookmarkId, orderNumber);
            }
        }
        return bookmarkOrders;
    }

    private List<String> extractOrderedBookmarkIds(JSONObject body) {
        List<String> orderedDbIds = new ArrayList<>();
        JSONArray idsArray = extractOrderedBookmarkIdArray(body);
        if (idsArray == null) {
            return orderedDbIds;
        }
        for (int i = 0; i < idsArray.length(); i++) {
            String id = String.valueOf(idsArray.opt(i));
            if (!isBlank(id) && !"null".equalsIgnoreCase(id)) {
                orderedDbIds.add(id);
            }
        }
        return orderedDbIds;
    }

    private JSONArray extractOrderedBookmarkIdArray(JSONObject body) {
        if (body == null) {
            return null;
        }
        if (body.has(PARAM_ORDERED_BOOKMARK_DB_IDS) && !body.isNull(PARAM_ORDERED_BOOKMARK_DB_IDS)) {
            return body.optJSONArray(PARAM_ORDERED_BOOKMARK_DB_IDS);
        }
        if (body.has(PARAM_BOOKMARK_IDS) && !body.isNull(PARAM_BOOKMARK_IDS)) {
            return body.optJSONArray(PARAM_BOOKMARK_IDS);
        }
        return null;
    }

    private void upsertBookmark(HttpExchange ex) throws IOException {
        JSONObject body = readBodyJson(ex);
        String accountId = opt(body, "accountId", queryParam(ex, "accountId"));
        String categoryId = opt(body, PARAM_CATEGORY_ID, queryParam(ex, PARAM_CATEGORY_ID));
        String mode = opt(body, "mode", queryParam(ex, "mode"));
        String channelId = opt(body, "channelId", queryParam(ex, "channelId"));
        String channelName = opt(body, "name", queryParam(ex, "name"));
        String cmd = opt(body, "cmd", queryParam(ex, "cmd"));
        if (isBlank(channelId) && !isBlank(opt(body, "id", ""))) {
            channelId = opt(body, "id", "");
        }

        Account account = AccountService.getInstance().getById(accountId);
        if (account == null || isBlank(channelId) || isBlank(channelName)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"Missing account/channel details\"}");
            return;
        }
        applyMode(account, mode);

        String categoryTitle = "";
        if (!isBlank(categoryId)) {
            Category category = CategoryDb.get().getCategoryByDbId(categoryId, account);
            if (category != null) {
                categoryTitle = category.getTitle();
            }
        }

        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(channelName);
        channel.setCmd(cmd);
        channel.setLogo(opt(body, "logo", ""));
        channel.setDrmType(opt(body, "drmType", ""));
        channel.setDrmLicenseUrl(opt(body, "drmLicenseUrl", ""));
        channel.setClearKeysJson(opt(body, "clearKeysJson", ""));
        channel.setInputstreamaddon(opt(body, "inputstreamaddon", ""));
        channel.setManifestType(opt(body, "manifestType", ""));

        String portal = isBlank(account.getServerPortalUrl()) ? account.getUrl() : account.getServerPortalUrl();
        Bookmark bookmark = new Bookmark(account.getAccountName(), categoryTitle, channelId, channelName, cmd, portal, categoryId);
        bookmark.setAccountAction(account.getAction());
        bookmark.setFromChannel(channel);
        bookmark.setChannelJson(channel.toJson());

        Bookmark existing = BookmarkService.getInstance().getBookmark(bookmark);
        if (existing != null) {
            writeJson(ex, 200, "{\"status\":\"ok\",\"action\":\"exists\",\"bookmarkId\":\"" + escape(existing.getDbId()) + "\"}");
            return;
        }

        BookmarkService.getInstance().save(bookmark);
        Bookmark saved = BookmarkService.getInstance().getBookmark(bookmark);
        writeJson(ex, 200, "{\"status\":\"ok\",\"action\":\"saved\",\"bookmarkId\":\"" + escape(saved != null ? saved.getDbId() : "") + "\"}");
    }

    private void deleteBookmark(HttpExchange ex) throws IOException {
        String bookmarkId = queryParam(ex, "bookmarkId");
        if (isBlank(bookmarkId)) {
            JSONObject body = readBodyJson(ex);
            bookmarkId = opt(body, "bookmarkId", "");
        }
        if (isBlank(bookmarkId)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"bookmarkId is required\"}");
            return;
        }
        BookmarkService.getInstance().remove(bookmarkId);
        writeJson(ex, 200, "{\"status\":\"ok\",\"action\":\"removed\"}");
    }

    private JSONObject readBodyJson(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readAllBytes();
            if (data.length == 0) {
                return new JSONObject();
            }
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception _) {
            return new JSONObject();
        }
    }

    private String opt(JSONObject json, String key, String fallback) {
        if (json != null && json.has(key) && !json.isNull(key)) {
            String value = json.optString(key, fallback);
            if (!isBlank(value)) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private void applyMode(Account account, String mode) {
        if (account == null || isBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception _) {
            account.setAction(Account.AccountAction.itv);
        }
    }

    private void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", ALLOWED_METHODS);
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String queryParam(HttpExchange ex, String key) {
        try {
            String value = getParam(ex, key);
            return value == null ? "" : value;
        } catch (Exception _) {
            return "";
        }
    }
}
