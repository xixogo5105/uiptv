package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.util.PlayerUrlUtils;

import java.io.IOException;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class XtremePlayerService implements AccountPlayerService {

    @Override
    public PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException {
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService.class, "Resolving playback URL for Xtreme account: " + account.getAccountName());
        String rawUrl = constructXtremeUrl(account, channel, parentSeriesId);
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService.class, "Constructed fresh Xtreme URL for " + account.getAction());
        
        String finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl));
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService.class, "Final resolved URL: " + finalUrl);
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService.class, "Playback URL resolved.");
        
        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    private String constructXtremeUrl(Account account, Channel channel, String parentSeriesId) {
        if (channel == null) return "";
        String fallbackCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel);
        if (isNotBlank(fallbackCmd)) {
            com.uiptv.util.AppLog.addInfoLog(XtremePlayerService.class, "Found channel cmd: " + fallbackCmd);
        }
        String baseUrl = resolveBaseUrl(account);
        if (isBlank(baseUrl)) {
            com.uiptv.util.AppLog.addWarningLog(XtremePlayerService.class, "Xtreme base URL is blank. Falling back to channel cmd.");
            return fallbackCmd;
        }
        if (!hasRequiredParts(account, channel)) {
            com.uiptv.util.AppLog.addWarningLog(XtremePlayerService.class, "Xtreme URL parts missing (username/password/channelId). Falling back to channel cmd.");
            return fallbackCmd;
        }
        String extension = inferExtensionFromCmd(fallbackCmd);
        String type = resolveStreamType(account, parentSeriesId);
        String resolvedExtension = defaultExtension(extension, type);
        return baseUrl + type + "/" + account.getUsername() + "/" + account.getPassword() + "/" + channel.getChannelId() + "." + resolvedExtension;
    }

    private String resolveBaseUrl(Account account) {
        String baseUrl = isBlank(account.getM3u8Path()) ? account.getUrl() : account.getM3u8Path();
        if (isBlank(baseUrl)) {
            return "";
        }
        int playerApiIndex = baseUrl.indexOf("player_api.php");
        if (playerApiIndex >= 0) {
            baseUrl = baseUrl.substring(0, playerApiIndex);
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private boolean hasRequiredParts(Account account, Channel channel) {
        return isNotBlank(account.getUsername())
                && isNotBlank(account.getPassword())
                && isNotBlank(channel.getChannelId());
    }

    private String resolveStreamType(Account account, String parentSeriesId) {
        Account.AccountAction action = account.getAction();
        if (isNotBlank(parentSeriesId) || action == Account.AccountAction.series) {
            return "series";
        }
        if (action == Account.AccountAction.vod) {
            return "movie";
        }
        return "live";
    }

    private String defaultExtension(String extension, String type) {
        if (isNotBlank(extension)) {
            return extension;
        }
        return "live".equals(type) ? "ts" : "mp4";
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
