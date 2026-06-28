package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AppLog;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateResponseText;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpWatchingNowVodM3u8EntryServer implements HttpHandler {
    private static final String LOG_PREFIX = "WatchingNowVodEntry: ";
    private static final String GET = "GET";
    private static final String HEAD = "HEAD";
    private static final String ALLOW = "Allow";
    private static final String LOCATION = "Location";
    private static final String ACCOUNT_ID_PARAM = "accountId";
    private static final String CATEGORY_ID_PARAM = "categoryId";
    private static final String VOD_ID_PARAM = "vodId";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        AppLog.addInfoLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "HTTP entry request method=" + method);
        if (!GET.equalsIgnoreCase(method) && !HEAD.equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set(ALLOW, GET + ", " + HEAD);
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String accountId = getParam(exchange, ACCOUNT_ID_PARAM);
        String categoryId = getParam(exchange, CATEGORY_ID_PARAM);
        String vodId = getParam(exchange, VOD_ID_PARAM);
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(vodId)) {
            AppLog.addWarningLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "Missing required params");
            generateResponseText(exchange, 404, "Watching Now VOD entry not found.");
            return;
        }

        try {
            Account account = AccountService.getInstance().getById(accountId);
            if (account == null) {
                AppLog.addWarningLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "Account not found: " + accountId);
                generateResponseText(exchange, 404, "Account not found.");
                return;
            }

            Channel channel = resolveVodChannel(account, categoryId, vodId);
            if (channel == null || StringUtils.isBlank(channel.getCmd())) {
                AppLog.addWarningLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "VOD channel not found: " + vodId);
                generateResponseText(exchange, 404, "VOD not found.");
                return;
            }

            HandshakeService.getInstance().hardTokenRefresh(account);
            String originalCmd = channel.getCmd();
            channel.setCmd(URLDecoder.decode(originalCmd, StandardCharsets.UTF_8));

            PlayerResponse response = PlayerService.getInstance().get(account, channel);
            channel.setCmd(originalCmd);

            if (response == null || StringUtils.isBlank(response.getUrl())) {
                AppLog.addWarningLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "Unable to resolve VOD URL");
                generateResponseText(exchange, 502, "Unable to resolve VOD playback URL.");
                return;
            }

            String streamUrl = response.getUrl();
            AppLog.addInfoLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "Redirecting to: " + streamUrl);
            exchange.setAttribute(
                    com.uiptv.util.WebActivityLog.ACTIVITY_DESCRIPTION_ATTRIBUTE,
                    com.uiptv.util.WebActivityLog.describePublishedM3uEntry(
                            channel.getName(),
                            account.getAccountName(),
                            categoryId
                    )
            );
            exchange.getResponseHeaders().add(LOCATION, streamUrl);
            exchange.sendResponseHeaders(307, -1);
        } catch (Exception ex) {
            AppLog.addErrorLog(HttpWatchingNowVodM3u8EntryServer.class, LOG_PREFIX + "Exception: " + ex.getMessage());
            generateResponseText(exchange, 502, "Unable to resolve watching now VOD: " + ex.getMessage());
        }
    }

    private Channel resolveVodChannel(Account account, String categoryId, String vodId) {
        if (account == null || StringUtils.isBlank(vodId)) {
            return null;
        }
        String safeCategoryId = categoryId == null ? "" : categoryId;
        Channel direct = VodChannelDb.get().getChannelByChannelId(vodId, safeCategoryId, account.getDbId());
        if (direct != null) {
            return direct;
        }
        List<Channel> matches = VodChannelDb.get().getAll(
                " WHERE accountId=? AND channelId=?",
                new String[]{account.getDbId(), vodId}
        );
        if (matches.isEmpty()) {
            return null;
        }
        return matches.getFirst();
    }
}