package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.CategoryDb;
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
import com.uiptv.shared.Pagination;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isNotBlank;

public class HttpWebChannelJsonServer implements HttpHandler {
    private static final int DEFAULT_PAGE_SIZE = 120;
    private static final int MAX_PAGE_SIZE = 240;
    private static final int DEFAULT_PREFETCH = 3;
    private static final int MAX_PREFETCH = 5;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, "{\"items\":[],\"nextPage\":0,\"hasMore\":false,\"apiOffset\":0}");
            return;
        }
        applyMode(account, getParam(ex, "mode"));
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        String categoryId = getParam(ex, "categoryId");
        String movieId = getParam(ex, "movieId");
        int page = parseInt(getParam(ex, "page"), 0, 0, Integer.MAX_VALUE);
        int pageSize = parseInt(getParam(ex, "pageSize"), DEFAULT_PAGE_SIZE, 20, MAX_PAGE_SIZE);
        int prefetchPages = parseInt(getParam(ex, "prefetchPages"), DEFAULT_PREFETCH, 1, MAX_PREFETCH);
        int apiOffset = parseInt(getParam(ex, "apiOffset"), 0, 0, 1);

        if (account.getType() == AccountType.STALKER_PORTAL && !"All".equalsIgnoreCase(categoryId)) {
            generateJsonResponse(ex, buildStalkerPagedResponse(account, categoryId, movieId, page, pageSize, prefetchPages, apiOffset));
            return;
        }

        // Fallback path for non-Stalker providers: slice already-resolved full list for web paging.
        String fullJson = resolveFullJson(account, categoryId, movieId);
        generateJsonResponse(ex, sliceJson(fullJson, page, pageSize, prefetchPages));
    }

    private String buildStalkerPagedResponse(Account account, String categoryId, String movieId, int page, int pageSize, int prefetchPages, int requestedApiOffset) {
        String categoryApiId = resolveCategoryApiId(account, categoryId);
        int resolvedApiOffset = requestedApiOffset;

        List<Channel> merged = new ArrayList<>();
        int currentPage = page;
        boolean hasMore = false;

        for (int i = 0; i < prefetchPages; i++) {
            int apiPage = currentPage + resolvedApiOffset;
            StalkerPageResult result = fetchStalkerPage(account, categoryApiId, movieId, apiPage, pageSize);

            if (currentPage == 0 && i == 0 && resolvedApiOffset == 0 && result.items.isEmpty()) {
                // Some portals start from page 1 instead of 0.
                StalkerPageResult pageOne = fetchStalkerPage(account, categoryApiId, movieId, 1, pageSize);
                if (!pageOne.items.isEmpty()) {
                    resolvedApiOffset = 1;
                    result = pageOne;
                    apiPage = 1;
                }
            }

            if (result.items.isEmpty()) {
                hasMore = false;
                break;
            }

            merged.addAll(result.items);
            hasMore = estimateHasMore(result.pagination, apiPage, resolvedApiOffset, result.items.size(), pageSize);
            currentPage++;

            if (!hasMore) {
                break;
            }
        }

        if (account.getAction() == Account.AccountAction.series) {
            if (isNotBlank(movieId)) {
                applySeriesEpisodesWatched(account, categoryApiId, movieId, merged);
            } else {
                applySeriesRowsWatched(account, categoryApiId, merged);
            }
        }
        List<Channel> deduped = dedupeChannels(merged);
        JSONObject response = new JSONObject();
        response.put("items", new JSONArray(deduped));
        response.put("nextPage", currentPage);
        response.put("hasMore", hasMore);
        response.put("apiOffset", resolvedApiOffset);
        return response.toString();
    }

    private String resolveFullJson(Account account, String categoryId, String movieId) throws IOException {
        if (account.getAction() == Account.AccountAction.series
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(movieId)
                && !"All".equalsIgnoreCase(categoryId)) {
            String categoryApiId = resolveCategoryApiId(account, categoryId);
            List<Channel> episodes = ChannelService.getInstance().getSeries(categoryApiId, movieId, account, null, null);
            applySeriesEpisodesWatched(account, categoryApiId, movieId, episodes);
            return StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(
                    episodes
            );
        }
        if ("All".equalsIgnoreCase(categoryId)) {
            JSONArray allChannels = new JSONArray();
            List<Category> categories = resolveCategoriesForAccount(account);
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
                    if (channelsJson == null || channelsJson.isEmpty()) continue;
                    JSONArray channelsArray = new JSONArray(channelsJson);
                    for (int i = 0; i < channelsArray.length(); i++) {
                        allChannels.put(channelsArray.getJSONObject(i));
                    }
                }
            }
            String result = allChannels.toString();
            if (account.getAction() == Account.AccountAction.series) {
                result = enrichSeriesRowsWatchedJson(account, "", result);
            }
            return result;
        }
        Category category = resolveCategoryByDbId(account, categoryId);
        String result = StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account);
        if (account.getAction() == Account.AccountAction.series) {
            String categoryApiId = resolveCategoryApiId(account, categoryId);
            result = enrichSeriesRowsWatchedJson(account, categoryApiId, result);
        }
        return result;
    }

    private String sliceJson(String json, int page, int pageSize, int prefetchPages) {
        JSONArray all = new JSONArray(json);
        int start = page * pageSize;
        int end = Math.min(all.length(), start + (pageSize * prefetchPages));
        JSONArray items = new JSONArray();
        for (int i = start; i < end; i++) {
            items.put(all.get(i));
        }

        JSONObject response = new JSONObject();
        response.put("items", items);
        response.put("nextPage", page + Math.max(prefetchPages, 1));
        response.put("hasMore", end < all.length());
        response.put("apiOffset", 0);
        return response.toString();
    }

    private StalkerPageResult fetchStalkerPage(Account account, String categoryApiId, String movieId, int pageNumber, int pageSize) {
        Map<String, String> params = ChannelService.getChannelOrSeriesParams(categoryApiId, pageNumber, account.getAction(), movieId, "0");
        params.put("per_page", String.valueOf(pageSize));

        String json = FetchAPI.fetch(params, account);
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        List<Channel> parsed = account.getAction() == itv
                ? ChannelService.getInstance().parseItvChannels(json, true)
                : ChannelService.getInstance().parseVodChannels(account, json, true);
        return new StalkerPageResult(parsed == null ? List.of() : parsed, pagination);
    }

    private static List<Channel> dedupeChannels(List<Channel> channels) {
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        for (Channel c : channels) {
            if (c == null) continue;
            String key = String.join("|",
                    StringUtils.isBlank(c.getChannelId()) ? "" : c.getChannelId().trim(),
                    StringUtils.isBlank(c.getCmd()) ? "" : c.getCmd().trim(),
                    StringUtils.isBlank(c.getName()) ? "" : c.getName().trim().toLowerCase());
            unique.putIfAbsent(key, c);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean estimateHasMore(Pagination pagination, int apiPage, int apiOffset, int currentSize, int pageSize) {
        if (pagination != null && pagination.getMaxPageItems() > 0 && pagination.getPaginationLimit() > 0) {
            int servedPages = Math.max(1, apiPage - apiOffset + 1);
            int servedItemsEstimate = servedPages * pagination.getPaginationLimit();
            return servedItemsEstimate < pagination.getMaxPageItems();
        }
        return currentSize >= pageSize;
    }

    private String resolveCategoryApiId(Account account, String categoryId) {
        Category category = resolveCategoryByDbId(account, categoryId);
        return category != null ? category.getCategoryId() : categoryId;
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

    private List<Category> resolveCategoriesForAccount(Account account) {
        if (account.getAction() == Account.AccountAction.vod) {
            return VodCategoryDb.get().getCategories(account);
        }
        if (account.getAction() == Account.AccountAction.series) {
            return SeriesCategoryDb.get().getCategories(account);
        }
        return CategoryDb.get().getCategories(account);
    }

    private String enrichSeriesRowsWatchedJson(Account account, String fallbackCategoryId, String response) {
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

    private void applySeriesRowsWatched(Account account, String fallbackCategoryId, List<Channel> rows) {
        if (rows == null || rows.isEmpty() || account == null) {
            return;
        }
        for (Channel row : rows) {
            if (row == null) continue;
            String rowCategoryId = StringUtils.isBlank(row.getCategoryId()) ? fallbackCategoryId : row.getCategoryId();
            rowCategoryId = normalizeSeriesCategoryId(rowCategoryId);
            row.setWatched(SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), rowCategoryId, row.getChannelId()) != null);
        }
    }

    private void applySeriesEpisodesWatched(Account account, String categoryId, String seriesId, List<Channel> episodes) {
        if (episodes == null || episodes.isEmpty() || account == null) {
            return;
        }
        String scopedCategoryId = normalizeSeriesCategoryId(categoryId);
        SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), scopedCategoryId, seriesId);
        for (Channel episode : episodes) {
            if (episode == null) continue;
            episode.setWatched(SeriesWatchStateService.getInstance().isMatchingEpisode(
                    state,
                    episode.getChannelId(),
                    episode.getSeason(),
                    episode.getEpisodeNum(),
                    episode.getName()
            ));
        }
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

    private int parseInt(String value, int defaultValue, int minValue, int maxValue) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minValue) return minValue;
            return Math.min(parsed, maxValue);
        } catch (Exception ignored) {
            return defaultValue;
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

    private static class StalkerPageResult {
        final List<Channel> items;
        final Pagination pagination;

        StalkerPageResult(List<Channel> items, Pagination pagination) {
            this.items = items;
            this.pagination = pagination;
        }
    }
}
