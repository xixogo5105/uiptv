package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;

import static com.uiptv.util.ServerUtils.generateM3u8Response;
import static com.uiptv.util.ServerUtils.getParam;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpM3u8PlayListServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        Channel channel = ChannelDb.get().getChannelById(getParam(ex, "channelId"), getParam(ex, "categoryId"));
        HandshakeService.getInstance().hardTokenRefresh(account);
        String cmd = PlayerService.getInstance().get(account, URLDecoder.decode(channel.getCmd(), UTF_8));
        String response = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"" + account.getDbId() + "\" tvg-name=\"" + channel.getName() + "\" group-title=\"" + account.getAccountName() + "\"," + channel.getName() + "\n" + StringUtils.EMPTY + cmd + "\n";
        generateM3u8Response(ex, response, getParam(ex, "accountId") + "-" + getParam(ex, "categoryId") + "-" + getParam(ex, "channelId") + ".m3u8");
    }
}
