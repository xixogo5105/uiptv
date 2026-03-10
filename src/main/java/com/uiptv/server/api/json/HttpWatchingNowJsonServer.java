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
        for (SeriesWatchState state : dedupedStates.values()) {
            PanelRow row = buildRow(account, state);
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
            String key = normalizeSeriesIdentity(state.getSeriesId());
            SeriesWatchState existing = deduped.get(key);
            if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                deduped.put(key, state);
            }
        }
        return deduped;
    }

    private PanelRow buildRow(Account account, SeriesWatchState state) {
        SnapshotScope scope = resolveSnapshotScope(state);
        String rawSeriesId = safe(state.getSeriesId());
        String scopedCategoryId = firstNonBlank(scope.categoryId, safe(state.getCategoryId()));
        String scopedSeriesId = firstNonBlank(scope.parentChannelId, rawSeriesId);
        SeriesCacheInfo cacheInfo = resolveSeriesInfoFromCache(account, scopedCategoryId, scopedSeriesId);
        if (!StringUtils.isBlank(scope.seriesTitle)) {
            cacheInfo = new SeriesCacheInfo(scope.seriesTitle, firstNonBlank(scope.seriesPoster, cacheInfo.seriesPoster), true, cacheInfo.categoryDbId);
        } else if (!StringUtils.isBlank(scope.seriesPoster)) {
            cacheInfo = new SeriesCacheInfo(cacheInfo.seriesTitle, scope.seriesPoster, cacheInfo.resolvedFromCache, cacheInfo.categoryDbId);
        }
        if (!cacheInfo.resolvedFromCache && safe(state.getSeriesId()).matches("^\\d+$")) {
            return null;
        }
        return new PanelRow(
                account.getDbId(),
                safe(account.getAccountName()),
                safe(account.getType() != null ? account.getType().name() : ""),
                safe(state.getCategoryId()),
                cacheInfo.categoryDbId,
                rawSeriesId,
                safe(state.getEpisodeId()),
                safe(state.getEpisodeName()),
                safe(state.getSeason()),
                state.getEpisodeNum(),
                firstNonBlank(cacheInfo.seriesTitle, rawSeriesId),
                cacheInfo.seriesPoster,
                state.getUpdatedAt()
        );
    }

    private SeriesCacheInfo resolveSeriesInfoFromCache(Account account, String categoryId, String seriesId) {
        SeriesCacheInfo directMatch = resolveSeriesInfoFromCandidateCategories(account, categoryId, seriesId);
        if (!needsSeriesCacheFallback(directMatch, seriesId)) {
            return directMatch;
        }
        SeriesCacheInfo fallback = resolveSeriesInfoFromAllCategories(account, seriesId, directMatch);
        return fallback != null ? fallback : directMatch;
    }

    private SeriesCacheInfo resolveSeriesInfoFromCandidateCategories(Account account, String categoryId, String seriesId) {
        String defaultTitle = safe(seriesId);
        for (String candidate : buildSeriesCategoryCandidates(account, categoryId)) {
            Channel match = findSeriesChannel(account, candidate, seriesId);
            if (match != null) {
                return buildSeriesCacheInfo(match, defaultTitle, true, resolveSeriesCategoryDbId(account, categoryId));
            }
        }
        return new SeriesCacheInfo(firstNonBlank(defaultTitle, safe(seriesId)), "", false, resolveSeriesCategoryDbId(account, categoryId));
    }

    private boolean needsSeriesCacheFallback(SeriesCacheInfo cacheInfo, String seriesId) {
        return cacheInfo == null
                || StringUtils.isBlank(cacheInfo.seriesTitle)
                || cacheInfo.seriesTitle.equals(safe(seriesId));
    }

    private SeriesCacheInfo resolveSeriesInfoFromAllCategories(Account account, String seriesId, SeriesCacheInfo current) {
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            Channel match = findSeriesChannel(account, category.getDbId(), seriesId);
            if (match != null) {
                String defaultTitle = current == null ? safe(seriesId) : current.seriesTitle;
                return buildSeriesCacheInfo(match, defaultTitle, true, safe(category.getDbId()));
            }
        }
        return null;
    }

    private List<String> buildSeriesCategoryCandidates(Account account, String apiCategoryId) {
        String categoryDbId = resolveSeriesCategoryDbId(account, apiCategoryId);
        List<String> candidates = new ArrayList<>();
        candidates.add(safe(apiCategoryId));
        if (!StringUtils.isBlank(categoryDbId)) {
            candidates.add(categoryDbId);
        }
        return candidates;
    }

    private Channel findSeriesChannel(Account account, String categoryId, String seriesId) {
        if (account == null || StringUtils.isBlank(categoryId) || StringUtils.isBlank(seriesId)) {
            return null;
        }
        List<Channel> channels = SeriesChannelDb.get().getChannels(account, categoryId);
        String normalizedSeriesId = normalizeSeriesIdentity(seriesId);
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            String channelId = safe(channel.getChannelId());
            if (channelId.equals(safe(seriesId)) || (!StringUtils.isBlank(normalizedSeriesId) && channelId.equals(normalizedSeriesId))) {
                return channel;
            }
        }
        return null;
    }

    private SeriesCacheInfo buildSeriesCacheInfo(Channel match, String defaultTitle, boolean resolved, String categoryDbId) {
        String title = firstNonBlank(match.getName(), defaultTitle);
        String poster = firstNonBlank(match.getLogo(), "");
        return new SeriesCacheInfo(firstNonBlank(title, defaultTitle), poster, resolved, categoryDbId);
    }

    private String resolveSeriesCategoryDbId(Account account, String apiCategoryId) {
        if (account == null || StringUtils.isBlank(account.getDbId()) || StringUtils.isBlank(apiCategoryId)) {
            return "";
        }
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            if (safe(category.getCategoryId()).equals(safe(apiCategoryId))) {
                return safe(category.getDbId());
            }
        }
        return "";
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

    private String normalizeSeriesIdentity(String seriesId) {
        String normalized = safe(seriesId);
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if (!normalized.contains(":")) {
            return normalized;
        }
        String[] parts = normalized.split(":");
        for (String part : parts) {
            String p = safe(part);
            if (!StringUtils.isBlank(p)) {
                return p;
            }
        }
        return normalized;
    }

    private SnapshotScope resolveSnapshotScope(SeriesWatchState state) {
        String categoryId = safe(state == null ? "" : state.getCategoryId());
        String parentChannelId = safe(state == null ? "" : state.getSeriesId());
        String title = "";
        String poster = "";
        if (state == null) {
            return new SnapshotScope(categoryId, parentChannelId, title, poster);
        }
        try {
            Category category = Category.fromJson(state.getSeriesCategorySnapshot());
            if (category != null) {
                categoryId = firstNonBlank(category.getCategoryId(), categoryId);
            }
        } catch (Exception _) {
            // Snapshot payloads are optional; keep original category id.
        }
        try {
            Channel channel = Channel.fromJson(state.getSeriesChannelSnapshot());
            if (channel != null) {
                parentChannelId = firstNonBlank(channel.getChannelId(), parentChannelId);
                categoryId = firstNonBlank(channel.getCategoryId(), categoryId);
                title = firstNonBlank(channel.getName(), title);
                poster = firstNonBlank(channel.getLogo(), poster);
            }
        } catch (Exception _) {
            // Snapshot payloads are optional; keep original series/channel scope.
        }
        return new SnapshotScope(categoryId, parentChannelId, title, poster);
    }

    private static class SeriesCacheInfo {
        private final String seriesTitle;
        private final String seriesPoster;
        private final boolean resolvedFromCache;
        private final String categoryDbId;

        private SeriesCacheInfo(String seriesTitle, String seriesPoster, boolean resolvedFromCache, String categoryDbId) {
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.resolvedFromCache = resolvedFromCache;
            this.categoryDbId = categoryDbId;
        }
    }

    private static class SnapshotScope {
        private final String categoryId;
        private final String parentChannelId;
        private final String seriesTitle;
        private final String seriesPoster;

        private SnapshotScope(String categoryId, String parentChannelId, String seriesTitle, String seriesPoster) {
            this.categoryId = categoryId == null ? "" : categoryId;
            this.parentChannelId = parentChannelId == null ? "" : parentChannelId;
            this.seriesTitle = seriesTitle == null ? "" : seriesTitle;
            this.seriesPoster = seriesPoster == null ? "" : seriesPoster;
        }
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
