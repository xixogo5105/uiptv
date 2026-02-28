package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.SeasonInfo;
import com.uiptv.util.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.nullSafeEncode;
import static com.uiptv.util.StringUtils.safeGetString;
import static com.uiptv.widget.UIptvAlert.showError;

public class XtremeParser {
    public static List<Category> parseCategories(Account account) {
        try {
            return doParseCategories(fetchPlayerApi(account, getCategoryAction(account.getAction()), null));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Channel> parseChannels(String categoryId, Account account) {
        try {
            Map<String, String> extraParams = new LinkedHashMap<>();
            extraParams.put("category_id", categoryId);
            return doParseChannels(fetchPlayerApi(account, getChannelListAction(account.getAction()), extraParams), account);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Channel> parseAllChannels(Account account) {
        try {
            return doParseChannels(fetchPlayerApi(account, getChannelListAction(account.getAction()), null), account);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EpisodeList parseEpisodes(String seriesId, Account account) {
        try {
            Map<String, String> extraParams = new LinkedHashMap<>();
            extraParams.put("series_id", seriesId);
            return doParseEpisodes(fetchPlayerApi(account, "get_series_info", extraParams), account);
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
                Category category = new Category(jsonCategory.getString("category_id"), jsonCategory.getString("category_name"), jsonCategory.getString("category_name"), true, 0);
                category.setExtraJson(jsonCategory.toString());
                categoryList.add(category);
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
                Channel channel = new Channel(
                        safeGetString(jsonCategory, account.getAction() == series ? "series_id" : "stream_id"),
                        safeGetString(jsonCategory, "name"),
                        null,
                        buildXtremeStreamUrl(account, safeGetString(jsonCategory, "stream_id"), safeGetString(jsonCategory, "container_extension")),
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
                );
                channel.setCategoryId(safeGetString(jsonCategory, "category_id"));
                channel.setExtraJson(jsonCategory.toString());
                categoryList.add(channel);
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

    private static String fetchPlayerApi(Account account, String action, Map<String, String> extraParams) throws IOException {
        List<String> baseUrlCandidates = baseUrlCandidates(account);
        if (baseUrlCandidates.isEmpty()) {
            throw new IOException("Xtreme base URL is blank.");
        }

        IOException lastIoException = null;
        for (int index = 0; index < baseUrlCandidates.size(); index++) {
            String baseUrl = baseUrlCandidates.get(index);
            boolean hasMoreCandidates = index + 1 < baseUrlCandidates.size();
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("player_api.php")
                    .append("?username=").append(nullSafeEncode(account.getUsername()))
                    .append("&password=").append(nullSafeEncode(account.getPassword()))
                    .append("&action=").append(nullSafeEncode(action));

            if (extraParams != null) {
                for (Map.Entry<String, String> entry : extraParams.entrySet()) {
                    url.append('&')
                            .append(nullSafeEncode(entry.getKey()))
                            .append('=')
                            .append(nullSafeEncode(entry.getValue()));
                }
            }

            try {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(url.toString(), null, "GET");
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                if (response.statusCode() == 404 && hasMoreCandidates) {
                    continue;
                }
                throw new IOException("Xtreme API request failed with HTTP " + response.statusCode());
            } catch (Exception e) {
                if (e instanceof IOException ioException) {
                    lastIoException = ioException;
                } else {
                    lastIoException = new IOException("Failed to call Xtreme API: " + e.getMessage(), e);
                }
                if (hasMoreCandidates) {
                    continue;
                }
            }
        }
        throw lastIoException != null ? lastIoException : new IOException("Failed to call Xtreme API.");
    }

    private static String normalizedBaseUrl(Account account) {
        if (account == null) {
            return "";
        }
        String fromM3uPath = normalizeBaseUrl(account.getM3u8Path());
        if (isBlank(fromM3uPath)) {
            return normalizeBaseUrl(account.getUrl());
        }
        return fromM3uPath;
    }

    private static List<String> baseUrlCandidates(Account account) {
        Set<String> candidates = new LinkedHashSet<>();
        if (account != null) {
            String fromM3uPath = normalizeBaseUrl(account.getM3u8Path());
            if (!isBlank(fromM3uPath)) {
                candidates.add(fromM3uPath);
            }
            String fromUrl = normalizeBaseUrl(account.getUrl());
            if (!isBlank(fromUrl)) {
                candidates.add(fromUrl);
            }
        }
        return new ArrayList<>(candidates);
    }

    private static String normalizeBaseUrl(String source) {
        if (isBlank(source)) {
            return "";
        }
        String trimmed = source.trim();
        if (!trimmed.contains("://")) {
            trimmed = "http://" + trimmed;
        }
        int playerApiIndex = trimmed.toLowerCase().indexOf("player_api.php");
        if (playerApiIndex >= 0) {
            trimmed = trimmed.substring(0, playerApiIndex);
        }
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        return trimmed;
    }

    private static String buildXtremeStreamUrl(Account account, String streamId, String extension) {
        String baseUrl = normalizedBaseUrl(account);
        if (isBlank(baseUrl) || isBlank(streamId)) {
            return "";
        }
        String ext = isBlank(extension) ? "ts" : extension;
        switch (account.getAction()) {
            case vod:
                return baseUrl + "movie/" + account.getUsername() + "/" + account.getPassword() + "/" + streamId + "." + ext;
            case series:
                return baseUrl + "series/" + account.getUsername() + "/" + account.getPassword() + "/" + streamId + "." + ext;
            default:
                return baseUrl + account.getUsername() + "/" + account.getPassword() + "/" + streamId;
        }
    }
}
