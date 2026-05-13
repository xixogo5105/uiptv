package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogMode;
import com.uiptv.application.CatalogPagedChannelsResult;
import com.uiptv.application.CatalogWebChannelsQuery;
import com.uiptv.model.Channel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpWebChannelJsonServer implements HttpHandler {
    private static final String PARAM_API_OFFSET = "apiOffset";
    private static final int DEFAULT_PAGE_SIZE = 120;
    private static final int MAX_PAGE_SIZE = 240;
    private static final int DEFAULT_PREFETCH = 3;
    private static final int MAX_PREFETCH = 5;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        CatalogPagedChannelsResult result = CatalogApplicationService.getInstance().listWebChannels(
                new CatalogWebChannelsQuery(
                        getParam(ex, "accountId"),
                        CatalogMode.fromRequest(getParam(ex, "mode")),
                        getParam(ex, "categoryId"),
                        getParam(ex, "movieId"),
                        parseInt(getParam(ex, "page"), 0, 0, Integer.MAX_VALUE),
                        parseInt(getParam(ex, "pageSize"), DEFAULT_PAGE_SIZE, 20, MAX_PAGE_SIZE),
                        parseInt(getParam(ex, "prefetchPages"), DEFAULT_PREFETCH, 1, MAX_PREFETCH),
                        parseInt(getParam(ex, PARAM_API_OFFSET), 0, 0, 1)
                )
        );

        JSONObject response = new JSONObject();
        response.put("items", new JSONArray(result.items() == null ? List.<Channel>of() : result.items()));
        response.put("nextPage", result.nextPage());
        response.put("hasMore", result.hasMore());
        response.put(PARAM_API_OFFSET, result.apiOffset());
        generateJsonResponse(ex, response.toString());
    }

    private int parseInt(String value, int defaultValue, int minValue, int maxValue) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minValue) return minValue;
            return Math.min(parsed, maxValue);
        } catch (Exception _) {
            return defaultValue;
        }
    }
}
