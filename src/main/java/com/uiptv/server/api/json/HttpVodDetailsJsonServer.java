package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.ImdbMetadataService;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpVodDetailsJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String vodName = getParam(ex, "vodName");

        JSONObject vodInfo = new JSONObject();
        vodInfo.put("name", isBlank(vodName) ? "VOD" : vodName);
        vodInfo.put("cover", "");
        vodInfo.put("plot", "");
        vodInfo.put("cast", "");
        vodInfo.put("director", "");
        vodInfo.put("genre", "");
        vodInfo.put("releaseDate", "");
        vodInfo.put("rating", "");
        vodInfo.put("tmdb", "");
        vodInfo.put("imdbUrl", "");
        vodInfo.put("duration", "");

        if (account != null && account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        Channel providerChannel = null;
        if (account != null && !isBlank(channelId)) {
            providerChannel = ChannelDb.get().getChannelById(channelId, categoryId);
        }

        if (providerChannel != null) {
            mergeMissing(vodInfo, "name", providerChannel.getName());
            mergeMissing(vodInfo, "cover", providerChannel.getLogo());
            mergeMissing(vodInfo, "plot", providerChannel.getDescription());
            mergeMissing(vodInfo, "releaseDate", providerChannel.getReleaseDate());
            mergeMissing(vodInfo, "rating", providerChannel.getRating());
            mergeMissing(vodInfo, "duration", providerChannel.getDuration());
        }

        String queryTitle = isBlank(vodName) ? vodInfo.optString("name", "") : vodName;
        JSONObject imdbFirst = ImdbMetadataService.getInstance().findBestEffortMovieDetails(queryTitle, "");
        mergeMissing(vodInfo, "name", imdbFirst.optString("name", ""));
        mergeMissing(vodInfo, "cover", imdbFirst.optString("cover", ""));
        mergeMissing(vodInfo, "plot", imdbFirst.optString("plot", ""));
        mergeMissing(vodInfo, "cast", imdbFirst.optString("cast", ""));
        mergeMissing(vodInfo, "director", imdbFirst.optString("director", ""));
        mergeMissing(vodInfo, "genre", imdbFirst.optString("genre", ""));
        mergeMissing(vodInfo, "releaseDate", imdbFirst.optString("releaseDate", ""));
        mergeMissing(vodInfo, "rating", imdbFirst.optString("rating", ""));
        mergeMissing(vodInfo, "tmdb", imdbFirst.optString("tmdb", ""));
        mergeMissing(vodInfo, "imdbUrl", imdbFirst.optString("imdbUrl", ""));

        JSONObject response = new JSONObject();
        response.put("vodInfo", vodInfo);
        generateJsonResponse(ex, response.toString());
    }

    private void mergeMissing(JSONObject target, String key, String incoming) {
        if (isBlank(target.optString(key, "")) && !isBlank(incoming)) {
            target.put(key, incoming);
        }
    }
}
