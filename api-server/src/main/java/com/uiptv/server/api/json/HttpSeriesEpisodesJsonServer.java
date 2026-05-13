package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogSeriesEpisodesQuery;
import com.uiptv.model.Channel;
import com.uiptv.util.ServerUtils;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpSeriesEpisodesJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        List<Channel> episodes = CatalogApplicationService.getInstance().listSeriesEpisodes(
                new CatalogSeriesEpisodesQuery(
                        getParam(ex, "accountId"),
                        getParam(ex, "categoryId"),
                        getParam(ex, "seriesId")
                )
        );
        generateJsonResponse(ex, ServerUtils.objectToJson(episodes));
    }
}
