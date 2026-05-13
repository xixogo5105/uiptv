package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogSeriesDetailsQuery;
import com.uiptv.application.CatalogSeriesDetailsResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpSeriesDetailsJsonServer implements HttpHandler {
    private static final String KEY_EPISODES = "episodes";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        CatalogSeriesDetailsResult details = CatalogApplicationService.getInstance().getSeriesDetails(
                new CatalogSeriesDetailsQuery(
                        getParam(ex, "accountId"),
                        getParam(ex, "categoryId"),
                        getParam(ex, "seriesId"),
                        getParam(ex, "seriesName")
                )
        );

        JSONObject response = new JSONObject();
        response.put("seasonInfo", details.seasonInfo() == null ? new JSONObject() : details.seasonInfo());
        response.put(KEY_EPISODES, details.episodes() == null ? new JSONArray() : details.episodes());
        response.put("episodesMeta", details.episodesMeta() == null ? new JSONArray() : details.episodesMeta());
        generateJsonResponse(ex, response.toString());
    }
}
