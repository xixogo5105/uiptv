package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.BingeWatchService;
import com.uiptv.util.AppLog;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateResponseText;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpBingeWatchEntryServer implements HttpHandler {
    private static final String LOG_PREFIX = "BingeWatch: ";
    private static final String EMPTY_VALUE = "";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String ALLOW = "Allow";
    private static final String LOCATION = "Location";
    private static final String TOKEN_PARAM = "token";
    private static final String EPISODE_ID_PARAM = "episodeId";
    private static final String TOKEN_LOG = " token=";
    private static final String EPISODE_ID_LOG = " episodeId=";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        AppLog.addLog(LOG_PREFIX + "HTTP entry request method=" + method
                + " uri=" + exchange.getRequestURI());
        if (!GET.equalsIgnoreCase(method) && !HEAD.equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set(ALLOW, GET + ", " + HEAD);
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String token = getParam(exchange, TOKEN_PARAM);
        String episodeId = getParam(exchange, EPISODE_ID_PARAM);
        if (isBlank(token) || isBlank(episodeId)) {
            AppLog.addLog(LOG_PREFIX + "HTTP entry missing params" + TOKEN_LOG + defaultString(token)
                    + EPISODE_ID_LOG + defaultString(episodeId));
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        try {
            BingeWatchService.ResolvedEpisode resolved = BingeWatchService.getInstance().resolveEpisode(token, episodeId);
            if (resolved == null || isBlank(resolved.url())) {
                AppLog.addLog(LOG_PREFIX + "HTTP entry resolve failed" + TOKEN_LOG + token + EPISODE_ID_LOG + episodeId);
                generateResponseText(exchange, 404, "Binge watch item not found.");
                return;
            }
            AppLog.addLog(LOG_PREFIX + "HTTP entry redirect" + TOKEN_LOG + token
                    + EPISODE_ID_LOG + episodeId
                    + " location=" + resolved.url());
            exchange.getResponseHeaders().add(LOCATION, resolved.url());
            exchange.sendResponseHeaders(307, -1);
        } catch (Exception ex) {
            AppLog.addLog(LOG_PREFIX + "HTTP entry exception" + TOKEN_LOG + token
                    + EPISODE_ID_LOG + episodeId
                    + " error=" + ex.getMessage());
            generateResponseText(exchange, 502, "Unable to resolve binge watch episode: " + ex.getMessage());
        }
    }

    private static String defaultString(String value) {
        return value == null ? EMPTY_VALUE : value;
    }
}
