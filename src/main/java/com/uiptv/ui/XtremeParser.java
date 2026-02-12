package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.SeasonInfo;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.StringUtils.getXtremeStreamUrl;
import static com.uiptv.util.StringUtils.safeGetString;
import static com.uiptv.widget.UIptvAlert.showError;

public class XtremeParser {
    public static List<Category> parseCategories(Account account) {
        try {
            if (!account.getM3u8Path().endsWith("/")) {
                account.setM3u8Path(account.getM3u8Path() + "/");
            }
            URL m3u8Url = new URL(account.getM3u8Path() + "player_api.php?username=" + account.getUsername() + "&password=" + account.getPassword() + "&action=" + getCategoryAction(account.getAction()));
            if (account.getM3u8Path().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return doParseCategories(readFullyAsString(connection.getInputStream(), "UTF-8"));
            } else if (account.getM3u8Path().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return doParseCategories(readFullyAsString(connection.getInputStream(), "UTF-8"));
            }
            return doParseCategories(readFullyAsString(m3u8Url.openStream(), "UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Channel> parseChannels(String categoryId, Account account) {
        try {
            if (!account.getM3u8Path().endsWith("/")) {
                account.setM3u8Path(account.getM3u8Path() + "/");
            }
            URL m3u8Url = new URL(account.getM3u8Path() + "player_api.php?username=" + account.getUsername() + "&password=" + account.getPassword() + "&action=" + getChannelListAction(account.getAction()) + "&category_id=" + categoryId);
            if (account.getM3u8Path().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return doParseChannels(readFullyAsString(connection.getInputStream(), "UTF-8"), account);
            } else if (account.getM3u8Path().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(10000);
                return doParseChannels(readFullyAsString(connection.getInputStream(), "UTF-8"), account);
            }
            return doParseChannels(readFullyAsString(m3u8Url.openStream(), "UTF-8"), account);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EpisodeList parseEpisodes(String seriesId, Account account) {
        try {
            if (!account.getM3u8Path().endsWith("/")) {
                account.setM3u8Path(account.getM3u8Path() + "/");
            }
            URL m3u8Url = new URL(account.getM3u8Path() + "player_api.php?username=" + account.getUsername() + "&password=" + account.getPassword() + "&action=get_series_info&series_id=" + seriesId);
            if (account.getM3u8Path().startsWith("https")) {
                HttpsURLConnection connection = (HttpsURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(30000);
                return doParseEpisodes(readFullyAsString(connection.getInputStream(), "UTF-8"), account);
            } else if (account.getM3u8Path().startsWith("http")) {
                HttpURLConnection connection = (HttpURLConnection) m3u8Url.openConnection();
                connection.setConnectTimeout(30000);
                return doParseEpisodes(readFullyAsString(connection.getInputStream(), "UTF-8"), account);
            }
            return doParseEpisodes(readFullyAsString(m3u8Url.openStream(), "UTF-8"), account);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Category> doParseCategories(String json) {
        List<Category> categoryList = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonCategory = list.getJSONObject(i);
                categoryList.add(new Category(jsonCategory.getString("category_id"), jsonCategory.getString("category_name"), jsonCategory.getString("category_name"), true, 0));
            }
        } catch (Exception e) {
            showError("Error while processing response data" + e.getMessage());
        }
        return categoryList;
    }

    private static List<Channel> doParseChannels(String json, Account account) {
        List<Channel> categoryList = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonCategory = list.getJSONObject(i);
                categoryList.add(new Channel(
                        safeGetString(jsonCategory, account.getAction() == series ? "series_id" : "stream_id"),
                        safeGetString(jsonCategory, "name"),
                        null,
                        getXtremeStreamUrl(account, safeGetString(jsonCategory, "stream_id"), safeGetString(jsonCategory, "container_extension")),
                        null,
                        null,
                        null,
                        safeGetString(jsonCategory, account.getAction() == series ? "cover" : "stream_icon"),
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        } catch (Exception e) {
            showError("Error while processing response data" + e.getMessage());
        }
        return categoryList;
    }

    private static EpisodeList doParseEpisodes(String json, Account account) {
        EpisodeList episodeList = new EpisodeList();
        try {
            JSONObject data = new JSONObject(json);
            episodeList.seasonInfo = new SeasonInfo(data.getJSONObject("info"));
            for (Map.Entry<String, Object> entry : data.getJSONObject("episodes").toMap().entrySet()) {
                List seasonEpisodes = (List) entry.getValue();
                if (seasonEpisodes != null && !seasonEpisodes.isEmpty()) {
                    seasonEpisodes.forEach(episode -> {
                        episodeList.episodes.add(new Episode(account, (Map) episode));
                    });
                }
            }
        } catch (Exception e) {
            showError("Error while processing response data" + e.getMessage());
        }
        return episodeList;
    }

    private static String getCategoryAction(Account.AccountAction action) {
        switch (action) {
            case vod:
                return "get_vod_categories";
            case series:
                return "get_series_categories";
        }
        return "get_live_categories";
    }

    private static String getChannelListAction(Account.AccountAction action) {
        switch (action) {
            case vod:
                return "get_vod_streams";
            case series:
                return "get_series";
        }
        return "get_live_streams";
    }

    public static String readFullyAsString(InputStream inputStream, String encoding) throws IOException {
        return readFully(inputStream).toString(encoding);
    }

    private static ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos;
    }
}
