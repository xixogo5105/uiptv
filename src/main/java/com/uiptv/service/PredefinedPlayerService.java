package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.util.PlayerUrlUtils;

import java.io.IOException;

public class PredefinedPlayerService implements AccountPlayerService {

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        com.uiptv.util.AppLog.addLog("Resolving playback URL for Predefined account: " + account.getAccountName());
        String rawUrl = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
        com.uiptv.util.AppLog.addLog("Using direct channel command for " + account.getType() + ".");
        
        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        com.uiptv.util.AppLog.addLog("Final resolved URL: " + finalUrl);
        com.uiptv.util.AppLog.addLog("Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }
}
