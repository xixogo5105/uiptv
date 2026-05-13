package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogVodDetailsQuery;
import com.uiptv.application.CatalogVodDetailsResult;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpVodDetailsJsonServer implements HttpHandler {
    private static final String KEY_COVER = "cover";
    private static final String KEY_DIRECTOR = "director";
    private static final String KEY_GENRE = "genre";
    private static final String KEY_IMDB_URL = "imdbUrl";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        CatalogVodDetailsResult details = CatalogApplicationService.getInstance().getVodDetails(
                new CatalogVodDetailsQuery(
                        getParam(ex, "accountId"),
                        getParam(ex, "categoryId"),
                        getParam(ex, "channelId"),
                        getParam(ex, "vodName")
                )
        );
        JSONObject vodInfo = new JSONObject();
        vodInfo.put("name", details.name());
        vodInfo.put(KEY_COVER, details.cover());
        vodInfo.put("plot", details.plot());
        vodInfo.put("cast", details.cast());
        vodInfo.put(KEY_DIRECTOR, details.director());
        vodInfo.put(KEY_GENRE, details.genre());
        vodInfo.put(KEY_RELEASE_DATE, details.releaseDate());
        vodInfo.put(KEY_RATING, details.rating());
        vodInfo.put("tmdb", details.tmdb());
        vodInfo.put(KEY_IMDB_URL, details.imdbUrl());
        vodInfo.put("duration", details.duration());
        JSONObject response = new JSONObject();
        response.put("vodInfo", vodInfo);
        generateJsonResponse(ex, response.toString());
    }
}
