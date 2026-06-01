package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.BingeWatchService;
import com.uiptv.util.AppLog;
import com.uiptv.util.WebActivityLog;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateM3u8Response;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpBingeWatchPlaylistServer implements HttpHandler {
    private static final String LOG_PREFIX = "BingeWatch: ";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String token = getParam(exchange, "token");
        AppLog.addInfoLog(HttpBingeWatchPlaylistServer.class, LOG_PREFIX + "HTTP playlist request token=" + (token == null ? "" : token));
        String playlist = BingeWatchService.getInstance().renderPlaylist(token);
        if (isBlank(token) || isBlank(playlist)) {
            AppLog.addWarningLog(HttpBingeWatchPlaylistServer.class, LOG_PREFIX + "HTTP playlist request failed token=" + (token == null ? "" : token));
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        AppLog.addInfoLog(HttpBingeWatchPlaylistServer.class, LOG_PREFIX + "HTTP playlist response token=" + token + " length=" + playlist.length());
        setActivityDescription(exchange, token);
        generateM3u8Response(exchange, playlist, "binge-watch-" + token + ".m3u8");
    }

    private void setActivityDescription(HttpExchange exchange, String token) {
        List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(token);
        if (items.isEmpty()) {
            return;
        }
        BingeWatchService.PlaylistItem first = items.getFirst();
        exchange.setAttribute(
                WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE,
                WebActivityLog.describeBingeWatchPlaylist(first.episodeName(), first.season(), first.episodeNumber(), items.size())
        );
    }
}
