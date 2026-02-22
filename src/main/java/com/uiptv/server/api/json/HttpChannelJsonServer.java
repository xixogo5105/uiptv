package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpChannelJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        applyMode(account, getParam(ex, "mode"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
        String categoryId = getParam(ex, "categoryId");
        String movieId = getParam(ex, "movieId");
        String response;

        if (account.getAction() == Account.AccountAction.series
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(movieId)
                && !"All".equalsIgnoreCase(categoryId)) {
            Category category = CategoryDb.get().getCategoryByDbId(categoryId, account);
            String categoryApiId = category != null ? category.getCategoryId() : categoryId;
            response = StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(
                    ChannelService.getInstance().getSeries(categoryApiId, movieId, account, null, null)
            );
        } else if ("All".equalsIgnoreCase(categoryId)) {
            List<Category> categories = CategoryDb.get().getCategories(account);
            JSONArray allChannels = new JSONArray();
            for (Category cat : categories) {
                if (!"All".equalsIgnoreCase(cat.getTitle())) {
                    String channelsJson = ChannelService.getInstance().readToJson(cat, account);
                    if (channelsJson != null && !channelsJson.isEmpty()) {
                        JSONArray channelsArray = new JSONArray(channelsJson);
                        for (int i = 0; i < channelsArray.length(); i++) {
                            allChannels.put(channelsArray.getJSONObject(i));
                        }
                    }
                }
            }
            response = allChannels.toString();
        } else {
            Category category = CategoryDb.get().getCategoryByDbId(categoryId, account);
            response = StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account);
        }

        generateJsonResponse(ex, dedupeJsonResponse(response));
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

    private String dedupeJsonResponse(String response) {
        try {
            JSONArray array = new JSONArray(response);
            JSONArray deduped = new JSONArray();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("channelId", "").trim() + "|"
                        + item.optString("cmd", "").trim() + "|"
                        + item.optString("name", "").trim().toLowerCase();
                if (seen.add(key)) {
                    deduped.put(item);
                }
            }
            return deduped.toString();
        } catch (Exception ignored) {
            return response;
        }
    }
}
