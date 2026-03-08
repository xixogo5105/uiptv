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
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.util.AccountType;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpChannelJsonServer implements HttpHandler {
    private static final String ALL_CATEGORY = "All";
    private static final String PARAM_CHANNEL_ID = "channelId";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, "[]");
            return;
        }
        applyMode(account, getParam(ex, "mode"));
        String categoryId = getParam(ex, "categoryId");
        String movieId = getParam(ex, "movieId");
        String response = resolveResponse(account, categoryId, movieId);

        if (account.getAction() == Account.AccountAction.series && !isNotBlank(movieId)) {
            String categoryApiId = ALL_CATEGORY.equalsIgnoreCase(categoryId) ? "" : resolveCategoryApiId(account, categoryId);
            response = enrichSeriesRowsWatched(account, categoryApiId, response);
        }
        generateJsonResponse(ex, dedupeJsonResponse(response));
    }

    private String resolveResponse(Account account, String categoryId, String movieId) throws IOException {
        if (shouldServeSeriesEpisodes(account, categoryId, movieId)) {
            return resolveSeriesEpisodesResponse(account, categoryId, movieId);
        }
        if (ALL_CATEGORY.equalsIgnoreCase(categoryId)) {
            return readAllCategoryChannels(account);
        }
        Category category = resolveCategoryByDbId(account, categoryId);
        return StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account);
    }

    private boolean shouldServeSeriesEpisodes(Account account, String categoryId, String movieId) {
        return account.getAction() == Account.AccountAction.series
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(movieId)
                && !ALL_CATEGORY.equalsIgnoreCase(categoryId);
    }

    private String resolveSeriesEpisodesResponse(Account account, String categoryId, String movieId) {
        String categoryApiId = resolveCategoryApiId(account, categoryId);
        String cachedResponse = readFreshSeriesEpisodes(account, categoryApiId, movieId);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        List<Channel> episodes = fetchAndCacheSeriesEpisodes(account, categoryId, movieId);
        String response = StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(episodes);
        return enrichSeriesEpisodesWatched(account, resolveCategoryApiId(account, categoryId), movieId, response);
    }

    private String readFreshSeriesEpisodes(Account account, String categoryApiId, String movieId) {
        if (!SeriesEpisodeDb.get().isFresh(account, categoryApiId, movieId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return null;
        }
        List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(account, categoryApiId, movieId);
        if (cached.isEmpty()) {
            return null;
        }
        String cachedJson = com.uiptv.util.ServerUtils.objectToJson(cached);
        return enrichSeriesEpisodesWatched(account, categoryApiId, movieId, cachedJson);
    }

    private List<Channel> fetchAndCacheSeriesEpisodes(Account account, String categoryId, String movieId) {
        String categoryApiId = resolveCategoryApiId(account, categoryId);
        List<Channel> episodes = ChannelService.getInstance().getSeries(categoryApiId, movieId, account, null, null);
        if (!episodes.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryApiId, movieId, episodes);
        }
        return episodes;
    }

    private String readAllCategoryChannels(Account account) throws IOException {
        JSONArray allChannels = new JSONArray();
        for (Category category : resolveRequestedCategories(resolveCategoriesForAccount(account))) {
            appendChannels(allChannels, ChannelService.getInstance().readToJson(category, account));
        }
        return allChannels.toString();
    }

    private List<Category> resolveRequestedCategories(List<Category> categories) {
        List<Category> nonAllCategories = categories.stream()
                .filter(cat -> !ALL_CATEGORY.equalsIgnoreCase(cat.getTitle()))
                .toList();
        if (!nonAllCategories.isEmpty()) {
            return nonAllCategories;
        }
        return categories.stream()
                .filter(cat -> ALL_CATEGORY.equalsIgnoreCase(cat.getTitle()))
                .findFirst()
                .map(List::of)
                .orElse(List.of());
    }

    private void appendChannels(JSONArray target, String channelsJson) {
        if (channelsJson == null || channelsJson.isEmpty()) {
            return;
        }
        JSONArray channelsArray = new JSONArray(channelsJson);
        for (int i = 0; i < channelsArray.length(); i++) {
            target.put(channelsArray.getJSONObject(i));
        }
    }

    private void applyMode(Account account, String mode) {
        if (account == null || !isNotBlank(mode)) {
            return;
        }
        try {
            account.setAction(Account.AccountAction.valueOf(mode.toLowerCase()));
        } catch (Exception _) {
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
                String key = item.optString(PARAM_CHANNEL_ID, "").trim() + "|"
                        + item.optString("cmd", "").trim() + "|"
                        + item.optString("name", "").trim().toLowerCase();
                if (seen.add(key)) {
                    deduped.put(item);
                }
            }
            return deduped.toString();
        } catch (Exception _) {
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
                String seriesId = item.optString(PARAM_CHANNEL_ID, "");
                String rowCategoryId = item.optString("categoryId", "");
                if (StringUtils.isBlank(rowCategoryId)) {
                    rowCategoryId = fallbackCategoryId;
                }
                rowCategoryId = normalizeSeriesCategoryId(rowCategoryId);
                item.put("watched",
                        SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), rowCategoryId, seriesId) != null);
            }
            return rows.toString();
        } catch (Exception _) {
            return response;
        }
    }

    private String enrichSeriesEpisodesWatched(Account account, String categoryId, String seriesId, String response) {
        try {
            JSONArray rows = new JSONArray(response);
            if (rows.isEmpty()) {
                return response;
            }
            String scopedCategoryId = normalizeSeriesCategoryId(categoryId);
            SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), scopedCategoryId, seriesId);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;
                item.put("watched", SeriesWatchStateService.getInstance().isMatchingEpisode(
                        state,
                        item.optString("channelId", ""),
                        item.optString("season", ""),
                        item.optString("episodeNum", ""),
                        item.optString("name", "")
                ));
            }
            return rows.toString();
        } catch (Exception _) {
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

}
