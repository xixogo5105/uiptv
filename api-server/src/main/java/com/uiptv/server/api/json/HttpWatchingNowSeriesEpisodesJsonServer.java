package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.model.Channel;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpWatchingNowSeriesEpisodesJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String seriesId = getParam(ex, "seriesId");
        String categoryId = getParam(ex, "categoryId");
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, "[]");
            return;
        }
        List<Channel> episodesAsChannels = WatchingNowApplicationService.getInstance()
                .listSeriesEpisodes(getParam(ex, "accountId"), categoryId, seriesId);
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels));
    }
}
