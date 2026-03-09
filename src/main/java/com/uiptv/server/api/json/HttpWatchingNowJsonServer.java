package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpWatchingNowJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        generateJsonResponse(ex, toJson(buildRows()));
    }

    private String toJson(List<PanelRow> rows) {
        JSONArray payload = new JSONArray();
        for (PanelRow row : rows) {
            payload.put(row.toJson());
        }
        return payload.toString();
    }

    private List<PanelRow> buildRows() {
        List<PanelRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            addRowsForAccount(rows, account);
        }
        sortRows(rows);
        return rows;
    }

    private void sortRows(List<PanelRow> rows) {
        rows.sort(
                Comparator.comparingLong((PanelRow row) -> row.updatedAt).reversed()
                        .thenComparing(row -> safe(row.seriesTitle), String.CASE_INSENSITIVE_ORDER)
        );
    }

    private void addRowsForAccount(List<PanelRow> rows, Account account) {
        if (account == null || StringUtils.isBlank(account.getDbId())) {
            return;
        }
        Map<String, SeriesWatchState> dedupedStates = dedupeLatestStates(account.getDbId());
        if (dedupedStates.isEmpty()) {
            return;
        }
        AccountSeriesIndex seriesIndex = buildSeriesIndex(account, new ArrayList<>(dedupedStates.values()));
        for (SeriesWatchState state : dedupedStates.values()) {
            PanelRow row = buildRow(account, state, seriesIndex);
            if (row != null) {
                rows.add(row);
            }
        }
    }

    private Map<String, SeriesWatchState> dedupeLatestStates(String accountDbId) {
        Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
        for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(accountDbId)) {
            if (state == null || StringUtils.isBlank(state.getSeriesId())) {
                continue;
            }
            String key = safe(state.getSeriesId());
            SeriesWatchState existing = deduped.get(key);
            if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                deduped.put(key, state);
            }
        }
        return deduped;
    }

    private PanelRow buildRow(Account account, SeriesWatchState state, AccountSeriesIndex index) {
        SeriesCacheInfo cacheInfo = resolveSeriesInfoFromCache(state, index);
        if (!cacheInfo.resolvedFromCache && safe(state.getSeriesId()).matches("^\\d+$")) {
            return null;
        }
        return new PanelRow(
                account.getDbId(),
                safe(account.getAccountName()),
                safe(account.getType() != null ? account.getType().name() : ""),
                safe(state.getCategoryId()),
                cacheInfo.categoryDbId,
                safe(state.getSeriesId()),
                safe(state.getEpisodeId()),
                safe(state.getEpisodeName()),
                safe(state.getSeason()),
                state.getEpisodeNum(),
                firstNonBlank(cacheInfo.seriesTitle, safe(state.getSeriesId())),
                cacheInfo.seriesPoster,
                state.getUpdatedAt()
        );
    }

    private AccountSeriesIndex buildSeriesIndex(Account account, List<SeriesWatchState> states) {
        AccountSeriesIndex index = new AccountSeriesIndex();
        if (account == null || StringUtils.isBlank(account.getDbId())) {
            return index;
        }

        indexCategories(account, index);
        List<String> watchedSeriesIds = watchedSeriesIds(states);
        if (watchedSeriesIds.isEmpty()) {
            return index;
        }

        indexSeriesChannels(account, watchedSeriesIds, index);
        return index;
    }

    private void indexCategories(Account account, AccountSeriesIndex index) {
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            if (category == null || StringUtils.isBlank(category.getDbId())) {
                continue;
            }
            String categoryDbId = safe(category.getDbId());
            index.categoryDbIds.add(categoryDbId);
            String categoryApiId = safe(category.getCategoryId());
            if (!StringUtils.isBlank(categoryApiId)) {
                index.categoryApiToDb.putIfAbsent(categoryApiId, categoryDbId);
            }
        }
    }

    private List<String> watchedSeriesIds(List<SeriesWatchState> states) {
        return (states == null ? List.<SeriesWatchState>of() : states).stream()
                .map(SeriesWatchState::getSeriesId)
                .map(this::safe)
                .filter(id -> !StringUtils.isBlank(id))
                .distinct()
                .toList();
    }

    private void indexSeriesChannels(Account account, List<String> watchedSeriesIds, AccountSeriesIndex index) {
        List<Channel> channels = SeriesChannelDb.get().getChannelsBySeriesIds(account, watchedSeriesIds);
        for (Channel channel : channels) {
            if (channel == null || StringUtils.isBlank(channel.getChannelId())) {
                continue;
            }
            String seriesId = safe(channel.getChannelId());
            index.bySeriesId.putIfAbsent(seriesId, channel);
            String categoryDbId = safe(channel.getCategoryId());
            if (!StringUtils.isBlank(categoryDbId)) {
                index.byCategoryAndSeries.putIfAbsent(categoryDbId + "|" + seriesId, channel);
            }
        }
    }

    private SeriesCacheInfo resolveSeriesInfoFromCache(SeriesWatchState state, AccountSeriesIndex index) {
        String title = safe(state.getSeriesId());
        String poster = "";
        String categoryDbId = resolveCategoryDbId(state.getCategoryId(), index);
        boolean resolved = false;

        if (!StringUtils.isBlank(categoryDbId)) {
            String key = categoryDbId + "|" + safe(state.getSeriesId());
            Channel match = index.byCategoryAndSeries.get(key);
            if (match != null) {
                title = firstNonBlank(match.getName(), title);
                poster = firstNonBlank(match.getLogo(), poster);
                resolved = true;
            }
        }

        if (title.equals(safe(state.getSeriesId()))) {
            Channel match = index.bySeriesId.get(safe(state.getSeriesId()));
            if (match != null) {
                title = firstNonBlank(match.getName(), title);
                poster = firstNonBlank(match.getLogo(), poster);
                resolved = true;
            }
        }

        return new SeriesCacheInfo(title, poster, categoryDbId, resolved);
    }

    private String resolveCategoryDbId(String rawCategoryId, AccountSeriesIndex index) {
        String value = safe(rawCategoryId);
        if (StringUtils.isBlank(value) || index == null) {
            return "";
        }
        String fromApi = index.categoryApiToDb.get(value);
        if (!StringUtils.isBlank(fromApi)) {
            return fromApi;
        }
        return index.categoryDbIds.contains(value) ? value : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!StringUtils.isBlank(value)) return value.trim();
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class SeriesCacheInfo {
        private final String seriesTitle;
        private final String seriesPoster;
        private final String categoryDbId;
        private final boolean resolvedFromCache;

        private SeriesCacheInfo(String seriesTitle, String seriesPoster, String categoryDbId, boolean resolvedFromCache) {
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.categoryDbId = categoryDbId;
            this.resolvedFromCache = resolvedFromCache;
        }
    }

    private static class AccountSeriesIndex {
        private final Map<String, String> categoryApiToDb = new HashMap<>();
        private final Set<String> categoryDbIds = new HashSet<>();
        private final Map<String, Channel> bySeriesId = new HashMap<>();
        private final Map<String, Channel> byCategoryAndSeries = new HashMap<>();
    }

    private static class PanelRow {
        private final String accountId;
        private final String accountName;
        private final String accountType;
        private final String categoryId;
        private final String categoryDbId;
        private final String seriesId;
        private final String episodeId;
        private final String episodeName;
        private final String season;
        private final int episodeNum;
        private final String seriesTitle;
        private final String seriesPoster;
        private final long updatedAt;

        @SuppressWarnings("java:S107")
        private PanelRow(String accountId,
                         String accountName,
                         String accountType,
                         String categoryId,
                         String categoryDbId,
                         String seriesId,
                         String episodeId,
                         String episodeName,
                         String season,
                         int episodeNum,
                         String seriesTitle,
                         String seriesPoster,
                         long updatedAt) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.accountType = accountType;
            this.categoryId = categoryId;
            this.categoryDbId = categoryDbId;
            this.seriesId = seriesId;
            this.episodeId = episodeId;
            this.episodeName = episodeName;
            this.season = season;
            this.episodeNum = episodeNum;
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.updatedAt = updatedAt;
        }

        private JSONObject toJson() {
            JSONObject row = new JSONObject();
            row.put("key", accountId + "|" + seriesId);
            row.put("accountId", accountId);
            row.put("accountName", accountName);
            row.put("accountType", accountType);
            row.put("categoryId", categoryId);
            row.put("categoryDbId", categoryDbId);
            row.put("seriesId", seriesId);
            row.put("episodeId", episodeId);
            row.put("episodeName", episodeName);
            row.put("season", season);
            row.put("episodeNum", episodeNum);
            row.put("seriesTitle", seriesTitle);
            row.put("seriesPoster", seriesPoster);
            row.put("updatedAt", updatedAt);
            return row;
        }
    }
}
