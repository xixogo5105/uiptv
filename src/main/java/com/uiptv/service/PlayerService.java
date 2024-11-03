package com.uiptv.service;

import com.uiptv.model.Account;
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
import static com.uiptv.util.StringUtils.isNotBlank;

public class PlayerService {
    private static PlayerService instance;
    public static final EnumSet<AccountType> preDefinedUrls = EnumSet.of(RSS_FEED, M3U8_URL, M3U8_LOCAL, XTREME_API);

    private PlayerService() {
    }

    public static synchronized PlayerService getInstance() {
        if (instance == null) {
            instance = new PlayerService();
        }
        return instance;
    }

    public String get(Account account, String urlPrefix) throws IOException {
        return get(account, urlPrefix, "");
    }

    public String get(Account account, String urlPrefix, String series) throws IOException {
        if (preDefinedUrls.contains(account.getType())) return urlPrefix;
        try {
            if (isNotBlank(urlPrefix) && urlPrefix.contains("play_token=")) {
                if (isNotBlank(urlPrefix.split("play_token=")[1])) return processUrl(urlPrefix);
            }
        } catch (Exception ignored) {

        }
        String streamReadyUrl = parseUrl(FetchAPI.fetch(getParams(account, urlPrefix, series), account));
        if (isBlank(streamReadyUrl)) {
            HandshakeService.getInstance().hardTokenRefresh(account);
            streamReadyUrl = parseUrl(FetchAPI.fetch(getParams(account, urlPrefix, series), account));
        }
        return streamReadyUrl;
    }

    public String runBookmark(Account account, String urlPrefix) {
        HandshakeService.getInstance().connect(account);
        return parseUrl(FetchAPI.fetch(getParams(account, urlPrefix, ""), account));
    }

    private String parseUrl(String json) {
        try {
            return processUrl(new JSONObject(json).getJSONObject("js").getString("cmd"));
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

    private static String processUrl(String url) {
        if (isBlank(url)) return url;
        String[] uriParts = url.split(" ");
        return (uriParts.length <= 1) ? url : uriParts[1];
    }

}

