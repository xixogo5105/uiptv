package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.ui.LogDisplayUI;
import com.uiptv.util.PlayerUrlUtils;

import java.io.IOException;

public class PredefinedPlayerService implements AccountPlayerService {

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        LogDisplayUI.addLog("Resolving playback URL for Predefined account: " + account.getAccountName());
        String rawUrl = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
        LogDisplayUI.addLog("Using direct channel command for " + account.getType() + ".");
        
        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        LogDisplayUI.addLog("Final resolved URL: " + finalUrl);
        LogDisplayUI.addLog("Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }
}
