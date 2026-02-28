package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.ui.LogDisplayUI;
import com.uiptv.util.PlayerUrlUtils;

import java.io.IOException;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class XtremePlayerService implements AccountPlayerService {

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        LogDisplayUI.addLog("Resolving playback URL for Xtreme account: " + account.getAccountName());
        String rawUrl = constructXtremeUrl(account, channel, parentSeriesId);
        LogDisplayUI.addLog("Constructed fresh Xtreme URL for " + account.getAction());
        
        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        LogDisplayUI.addLog("Final resolved URL: " + finalUrl);
        LogDisplayUI.addLog("Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    private String constructXtremeUrl(Account account, Channel channel, String parentSeriesId) {
        if (channel == null) return "";
        String fallbackCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
        if (isNotBlank(fallbackCmd)) {
            LogDisplayUI.addLog("Found channel cmd: " + fallbackCmd);
        }

        String baseUrl = account.getUrl();
        if (isBlank(baseUrl)) {
            LogDisplayUI.addLog("Xtreme base URL is blank. Falling back to channel cmd.");
            return fallbackCmd;
        }

        if (baseUrl.endsWith("player_api.php")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "player_api.php".length());
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        String username = account.getUsername();
        String password = account.getPassword();
        String id = channel.getChannelId();
        if (isBlank(username) || isBlank(password) || isBlank(id)) {
            LogDisplayUI.addLog("Xtreme URL parts missing (username/password/channelId). Falling back to channel cmd.");
            return fallbackCmd;
        }
        String extension = inferExtensionFromCmd(fallbackCmd);

        String type = "live";
        Account.AccountAction action = account.getAction();
        if (isNotBlank(parentSeriesId) || action == Account.AccountAction.series) {
            type = "series";
            if (isBlank(extension)) extension = "mp4";
        } else if (action == Account.AccountAction.vod) {
            type = "movie";
            if (isBlank(extension)) extension = "mp4";
        } else {
            if (isBlank(extension)) extension = "ts";
        }

        return baseUrl + type + "/" + username + "/" + password + "/" + id + "." + extension;
    }

    private String inferExtensionFromCmd(String cmd) {
        if (isBlank(cmd)) {
            return "";
        }
        String playable = PlayerUrlUtils.extractPlayableUrl(cmd);
        if (isBlank(playable)) {
            return "";
        }

        int queryIdx = playable.indexOf('?');
        String noQuery = queryIdx >= 0 ? playable.substring(0, queryIdx) : playable;
        int fragmentIdx = noQuery.indexOf('#');
        String clean = fragmentIdx >= 0 ? noQuery.substring(0, fragmentIdx) : noQuery;
        int slashIdx = clean.lastIndexOf('/');
        String lastSegment = slashIdx >= 0 ? clean.substring(slashIdx + 1) : clean;
        int dotIndex = lastSegment.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= lastSegment.length() - 1) {
            return "";
        }
        String ext = lastSegment.substring(dotIndex + 1);
        return ext.matches("^[a-zA-Z0-9]+$") ? ext : "";
    }
}
