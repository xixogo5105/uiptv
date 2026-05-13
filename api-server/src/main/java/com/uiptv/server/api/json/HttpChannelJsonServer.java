package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogChannelsQuery;
import com.uiptv.application.CatalogMode;
import com.uiptv.model.Channel;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpChannelJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        List<Channel> channels = CatalogApplicationService.getInstance().listChannels(
                new CatalogChannelsQuery(
                        getParam(ex, "accountId"),
                        CatalogMode.fromRequest(getParam(ex, "mode")),
                        getParam(ex, "categoryId"),
                        getParam(ex, "movieId")
                )
        );
        generateJsonResponse(ex, StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(channels));
    }
}
