package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpChannelJsonServer implements HttpHandler {
    private static final long EPISODE_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        applyMode(account, getParam(ex, "mode"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }
        String categoryId = getParam(ex, "categoryId");
        String movieId = getParam(ex, "movieId");
        String response;

        if (account.getAction() == Account.AccountAction.series
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(movieId)
                && !"All".equalsIgnoreCase(categoryId)) {
            if (SeriesEpisodeDb.get().isFresh(account, movieId, EPISODE_CACHE_TTL_MS)) {
                List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(account, movieId);
                if (!cached.isEmpty()) {
                    String cachedJson = com.uiptv.util.ServerUtils.objectToJson(cached);
                    String categoryApiId = resolveCategoryApiId(account, categoryId);
                    cachedJson = enrichSeriesEpisodesWatched(account, categoryApiId, movieId, cachedJson);
                    generateJsonResponse(ex, dedupeJsonResponse(cachedJson));
                    return;
                }
            }
            Category category = resolveCategoryByDbId(account, categoryId);
            String categoryApiId = category != null ? category.getCategoryId() : categoryId;
            List<Channel> episodes = ChannelService.getInstance().getSeries(categoryApiId, movieId, account, null, null);
            if (!episodes.isEmpty()) {
                SeriesEpisodeDb.get().saveAll(account, movieId, episodes);
            }
            response = StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(episodes);
            response = enrichSeriesEpisodesWatched(account, categoryApiId, movieId, response);
        } else if ("All".equalsIgnoreCase(categoryId)) {
            List<Category> categories = resolveCategoriesForAccount(account);
            JSONArray allChannels = new JSONArray();
            List<Category> nonAllCategories = categories.stream()
                    .filter(cat -> !"All".equalsIgnoreCase(cat.getTitle()))
                    .toList();
            if (nonAllCategories.isEmpty()) {
                Category allCategory = categories.stream()
                        .filter(cat -> "All".equalsIgnoreCase(cat.getTitle()))
                        .findFirst()
                        .orElse(null);
                if (allCategory != null) {
                    String channelsJson = ChannelService.getInstance().readToJson(allCategory, account);
                    if (channelsJson != null && !channelsJson.isEmpty()) {
                        JSONArray channelsArray = new JSONArray(channelsJson);
                        for (int i = 0; i < channelsArray.length(); i++) {
                            allChannels.put(channelsArray.getJSONObject(i));
                        }
                    }
                }
            } else {
                for (Category cat : nonAllCategories) {
                    String channelsJson = ChannelService.getInstance().readToJson(cat, account);
                    if (channelsJson != null && !channelsJson.isEmpty()) {
                        JSONArray channelsArray = new JSONArray(channelsJson);
                        for (int i = 0; i < channelsArray.length(); i++) {
                            allChannels.put(channelsArray.getJSONObject(i));
                        }
                    }
                }
            }
            response = allChannels.toString();
        } else {
            Category category = resolveCategoryByDbId(account, categoryId);
            response = StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account);
        }

        if (account.getAction() == Account.AccountAction.series && !isNotBlank(movieId)) {
            String categoryApiId = "All".equalsIgnoreCase(categoryId) ? "" : resolveCategoryApiId(account, categoryId);
            response = enrichSeriesRowsWatched(account, categoryApiId, response);
        }
        generateJsonResponse(ex, dedupeJsonResponse(response));
    }

    private void applyMode(Account account, String mode) {
        if (account == null || !isNotBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception ignored) {
            account.setAction(Account.AccountAction.itv);
        }
    }

    private String dedupeJsonResponse(String response) {
        try {
            JSONArray array = new JSONArray(response);
            JSONArray deduped = new JSONArray();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("channelId", "").trim() + "|"
                        + item.optString("cmd", "").trim() + "|"
                        + item.optString("name", "").trim().toLowerCase();
                if (seen.add(key)) {
                    deduped.put(item);
                }
            }
            return deduped.toString();
        } catch (Exception ignored) {
            return response;
        }
    }

    private Category resolveCategoryByDbId(Account account, String categoryId) {
        if (account.getAction() == Account.AccountAction.vod) {
            return VodCategoryDb.get().getById(categoryId);
        }
        if (account.getAction() == Account.AccountAction.series) {
            return SeriesCategoryDb.get().getById(categoryId);
        }
        return CategoryDb.get().getCategoryByDbId(categoryId, account);
    }

    private String resolveCategoryApiId(Account account, String categoryId) {
        Category category = resolveCategoryByDbId(account, categoryId);
        return category != null ? category.getCategoryId() : categoryId;
    }

    private List<Category> resolveCategoriesForAccount(Account account) {
        if (account.getAction() == Account.AccountAction.vod) {
            return VodCategoryDb.get().getCategories(account);
        }
        if (account.getAction() == Account.AccountAction.series) {
            return SeriesCategoryDb.get().getCategories(account);
        }
        return CategoryDb.get().getCategories(account);
    }

    private String enrichSeriesRowsWatched(Account account, String fallbackCategoryId, String response) {
        try {
            JSONArray rows = new JSONArray(response);
            if (rows.isEmpty()) {
                return response;
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;
                String seriesId = item.optString("channelId", "");
                String rowCategoryId = item.optString("categoryId", "");
                if (StringUtils.isBlank(rowCategoryId)) {
                    rowCategoryId = fallbackCategoryId;
                }
                rowCategoryId = normalizeSeriesCategoryId(rowCategoryId);
                item.put("watched",
                        SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), rowCategoryId, seriesId) != null);
            }
            return rows.toString();
        } catch (Exception ignored) {
            return response;
        }
    }

    private String enrichSeriesEpisodesWatched(Account account, String categoryId, String seriesId, String response) {
        try {
            JSONArray rows = new JSONArray(response);
            if (rows.isEmpty()) {
                return response;
            }
            String watchedEpisodeId = "";
            String watchedSeason = "";
            String watchedEpisodeNum = "";
            String scopedCategoryId = normalizeSeriesCategoryId(categoryId);
            SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), scopedCategoryId, seriesId);
            if (state != null && state.getEpisodeId() != null) {
                watchedEpisodeId = state.getEpisodeId();
                watchedSeason = state.getSeason();
                if (state.getEpisodeNum() > 0) {
                    watchedEpisodeNum = String.valueOf(state.getEpisodeNum());
                }
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;
                item.put("watched", isMatchingWatchedEpisode(
                        watchedEpisodeId,
                        watchedSeason,
                        watchedEpisodeNum,
                        item.optString("channelId", ""),
                        item.optString("season", ""),
                        item.optString("episodeNum", "")
                ));
            }
            return rows.toString();
        } catch (Exception ignored) {
            return response;
        }
    }

    private String normalizeSeriesCategoryId(String categoryId) {
        if (StringUtils.isBlank(categoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(categoryId);
        if (category != null && isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return categoryId;
    }

    private boolean isMatchingWatchedEpisode(String watchedEpisodeId,
                                             String watchedSeason,
                                             String watchedEpisodeNum,
                                             String episodeId,
                                             String season,
                                             String episodeNum) {
        if (StringUtils.isBlank(watchedEpisodeId) || StringUtils.isBlank(episodeId)) {
            return false;
        }
        if (!watchedEpisodeId.equals(episodeId)) {
            return false;
        }
        String ws = digitsOnly(watchedSeason);
        String s = digitsOnly(season);
        if (StringUtils.isNotBlank(ws) && StringUtils.isNotBlank(s) && !ws.equals(s)) {
            return false;
        }
        String we = digitsOnly(watchedEpisodeNum);
        String e = digitsOnly(episodeNum);
        return StringUtils.isBlank(we) || StringUtils.isBlank(e) || we.equals(e);
    }

    private String digitsOnly(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }
}
