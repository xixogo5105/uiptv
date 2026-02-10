package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpChannelJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
        String categoryId = getParam(ex, "categoryId");
        String response;

        if ("All".equalsIgnoreCase(categoryId)) {
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

        generateJsonResponse(ex, response);
    }
}
