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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpWatchingNowJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        JSONArray rows = new JSONArray();
        for (PanelRow row : buildRows()) {
            rows.put(row.toJson());
        }
        generateJsonResponse(ex, rows.toString());
    }

    private List<PanelRow> buildRows() {
        List<PanelRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            if (account == null || StringUtils.isBlank(account.getDbId())) {
                continue;
            }

            Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
            for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(account.getDbId())) {
                if (state == null || StringUtils.isBlank(state.getSeriesId())) {
                    continue;
                }
                String key = safe(state.getSeriesId());
                SeriesWatchState existing = deduped.get(key);
                if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                    deduped.put(key, state);
                }
            }
            if (deduped.isEmpty()) {
                continue;
            }
            AccountSeriesIndex seriesIndex = buildSeriesIndex(account, new ArrayList<>(deduped.values()));

            for (SeriesWatchState state : deduped.values()) {
                PanelRow row = buildRow(account, state, seriesIndex);
                if (row != null) {
                    rows.add(row);
                }
            }
        }

        rows.sort(
                Comparator.comparingLong((PanelRow row) -> row.updatedAt).reversed()
                        .thenComparing(row -> safe(row.seriesTitle), String.CASE_INSENSITIVE_ORDER)
        );
        return rows;
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

        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            if (category == null || StringUtils.isBlank(category.getDbId())) {
                continue;
            }
            String categoryDbId = safe(category.getDbId());
            String categoryApiId = safe(category.getCategoryId());
            index.categoryDbIds.add(categoryDbId);
            if (!StringUtils.isBlank(categoryApiId)) {
                index.categoryApiToDb.putIfAbsent(categoryApiId, categoryDbId);
            }
        }

        List<String> watchedSeriesIds = (states == null ? List.<SeriesWatchState>of() : states).stream()
                .map(SeriesWatchState::getSeriesId)
                .map(this::safe)
                .filter(id -> !StringUtils.isBlank(id))
                .distinct()
                .collect(Collectors.toList());

        if (watchedSeriesIds.isEmpty()) {
            return index;
        }

        List<Channel> channels = SeriesChannelDb.get().getChannelsBySeriesIds(account, watchedSeriesIds);
        for (Channel channel : channels) {
            if (channel == null || StringUtils.isBlank(channel.getChannelId())) {
                continue;
            }
            String seriesId = safe(channel.getChannelId());
            String categoryDbId = safe(channel.getCategoryId());
            index.bySeriesId.putIfAbsent(seriesId, channel);
            if (!StringUtils.isBlank(categoryDbId)) {
                index.byCategoryAndSeries.putIfAbsent(categoryDbId + "|" + seriesId, channel);
            }
        }
        return index;
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
        private final String seriesTitle;
        private final String seriesPoster;
        private final long updatedAt;

        private PanelRow(String accountId,
                         String accountName,
                         String accountType,
                         String categoryId,
                         String categoryDbId,
                         String seriesId,
                         String seriesTitle,
                         String seriesPoster,
                         long updatedAt) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.accountType = accountType;
            this.categoryId = categoryId;
            this.categoryDbId = categoryDbId;
            this.seriesId = seriesId;
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
            row.put("seriesTitle", seriesTitle);
            row.put("seriesPoster", seriesPoster);
            row.put("updatedAt", updatedAt);
            return row;
        }
    }
}
