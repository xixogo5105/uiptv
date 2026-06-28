package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.util.AppLog;
import com.uiptv.util.StringUtils;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateResponseText;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpWatchingNowSeriesM3u8EntryServer implements HttpHandler {
    private static final String LOG_PREFIX = "WatchingNowSeriesEntry: ";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String ALLOW = "Allow";
    private static final String LOCATION = "Location";
    private static final String ACCOUNT_ID_PARAM = "accountId";
    private static final String CATEGORY_ID_PARAM = "categoryId";
    private static final String SERIES_ID_PARAM = "seriesId";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        AppLog.addInfoLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "HTTP entry request method=" + method);
        if (!GET.equalsIgnoreCase(method) && !HEAD.equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set(ALLOW, GET + ", " + HEAD);
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String accountId = getParam(exchange, ACCOUNT_ID_PARAM);
        String categoryId = getParam(exchange, CATEGORY_ID_PARAM);
        String seriesId = getParam(exchange, SERIES_ID_PARAM);
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(seriesId)) {
            AppLog.addWarningLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "Missing required params");
            generateResponseText(exchange, 404, "Watching Now series entry not found.");
            return;
        }

        try {
            Account account = WatchingNowApplicationService.getInstance().getAccount(accountId);
            if (account == null) {
                AppLog.addWarningLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "Account not found: " + accountId);
                generateResponseText(exchange, 404, "Account not found.");
                return;
            }
            account = AccountMediaContext.from(account, Account.AccountAction.series).toAccount();

            var episodes = SeriesWatchingNowSnapshotService.getInstance().loadChannels(accountId, categoryId, seriesId);
            if (episodes.isEmpty()) {
                AppLog.addWarningLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "No episodes found for series: " + seriesId);
                generateResponseText(exchange, 404, "No episodes found for this series.");
                return;
            }

            var watchState = com.uiptv.service.SeriesWatchStateService.getInstance()
                    .getSeriesLastWatched(accountId, categoryId, seriesId);
            String token = BingeWatchService.getInstance().createSession(
                    account,
                    seriesId,
                    categoryId,
                    watchState != null && StringUtils.isNotBlank(watchState.getSeason()) ? watchState.getSeason() : "1",
                    episodes,
                    watchState
            );
            if (StringUtils.isBlank(token)) {
                AppLog.addWarningLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "Unable to create binge watch session");
                generateResponseText(exchange, 502, "Unable to prepare binge watch session.");
                return;
            }

            String playlistUrl = BingeWatchService.getInstance().buildPlaylistUrl(token);
            AppLog.addInfoLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "Redirecting to playlist: " + playlistUrl);
            exchange.setAttribute(
                    com.uiptv.util.WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE,
                    com.uiptv.util.WebActivityLog.describeBingeWatchPlaylist(
                            episodes.isEmpty() ? "Series" : episodes.getFirst().getName(),
                            watchState != null ? watchState.getSeason() : "1",
                            watchState != null ? String.valueOf(watchState.getEpisodeNum()) : "1",
                            episodes.size()
                    )
            );
            exchange.getResponseHeaders().add(LOCATION, playlistUrl);
            exchange.sendResponseHeaders(307, -1);
        } catch (Exception ex) {
            AppLog.addErrorLog(HttpWatchingNowSeriesM3u8EntryServer.class, LOG_PREFIX + "Exception: " + ex.getMessage());
            generateResponseText(exchange, 502, "Unable to resolve watching now series: " + ex.getMessage());
        }
    }
}