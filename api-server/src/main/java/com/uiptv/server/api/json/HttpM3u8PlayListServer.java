package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.PlaybackApplicationService;
import com.uiptv.application.PlaybackPlaylistRequest;
import com.uiptv.application.PlaybackPlaylistResult;

import java.io.IOException;
import static com.uiptv.util.ServerUtils.generateM3u8Response;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpM3u8PlayListServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        PlaybackPlaylistResult result = PlaybackApplicationService.getInstance().buildPlaylist(
                new PlaybackPlaylistRequest(
                        getParam(ex, "accountId"),
                        getParam(ex, "categoryId"),
                        getParam(ex, "channelId")
                )
        );
        generateM3u8Response(ex, result.body(), result.filename());
    }
}
