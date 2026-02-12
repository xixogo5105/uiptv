package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.player.YoutubeDL;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.StringUtils.isBlank;

public class PlayerService {
    private static PlayerService instance;
    public static final EnumSet<AccountType> PRE_DEFINED_URLS = EnumSet.of(RSS_FEED, M3U8_URL, M3U8_LOCAL, XTREME_API);

    private PlayerService() {
    }

    public static synchronized PlayerService getInstance() {
        if (instance == null) {
            instance = new PlayerService();
        }
        return instance;
    }

    public PlayerResponse get(Account account, Channel channel) throws IOException {
        return get(account, channel, "");
    }

    public PlayerResponse get(Account account, Channel channel, String series) throws IOException {
        String urlPrefix = channel.getCmd();
        String finalUrl;

        if (PRE_DEFINED_URLS.contains(account.getType())) {
            finalUrl = resolveAndProcessUrl(urlPrefix);
        } else {
            finalUrl = resolveAndProcessUrl(fetchStalkerPortalUrl(account, series, urlPrefix));
        }

        PlayerResponse response = new PlayerResponse(finalUrl);
        response.setFromChannel(channel, account);
        return response;
    }

    private String fetchStalkerPortalUrl(final Account account, final String series, final String origUrl) {
        String freshUrl = parseUrl(FetchAPI.fetch(getParams(account, origUrl, series), account));
        if (isBlank(freshUrl)) {
            HandshakeService.getInstance().hardTokenRefresh(account);
            freshUrl = parseUrl(FetchAPI.fetch(getParams(account, origUrl, series), account));
        }
        return isBlank(freshUrl) ? origUrl : freshUrl;
    }

    private String parseUrl(String json) {
        try {
            return new JSONObject(json).getJSONObject("js").getString("cmd");
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Map<String, String> getParams(Account account, String urlPrefix, String series) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", Account.AccountAction.series.name().equalsIgnoreCase(account.getAction().name()) ? Account.AccountAction.vod.name() : account.getAction().name());
        params.put("action", "create_link");
        params.put("cmd", urlPrefix);
        params.put("series", Account.AccountAction.series.name().equalsIgnoreCase(account.getAction().name()) ? series : "");
        params.put("forced_storage", "0");
        params.put("disable_ad", "0");
        params.put("download", "0");
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    /**
     * Processes the URL, extracting the actual stream URL if it's a YouTube link.
     * This method ensures that the final URL passed to the player is a direct stream URL
     * for YouTube videos, while leaving other URLs untouched.
     *
     * @param url The URL to process.
     * @return The resolved streaming URL for YouTube videos, or the original URL for others.
     */
    private static String resolveAndProcessUrl(String url) {
        if (isBlank(url)) return url;

        String processedUrl = url;
        String[] uriParts = url.split(" ");
        if (uriParts.length > 1) {
            processedUrl = uriParts[1]; // Original logic to extract the actual URL part
        }

        // Check if the link is a YouTube video URL
        if (processedUrl != null && (processedUrl.contains("youtube.com/watch?v=") || processedUrl.contains("youtu.be/"))) {
            String streamingUrl = YoutubeDL.getStreamingUrl(processedUrl);
            if (streamingUrl != null && !streamingUrl.isEmpty()) {
                return streamingUrl;
            }
        }
        return processedUrl;
    }

}
