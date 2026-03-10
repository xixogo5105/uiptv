package com.uiptv.service;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;

public class WatchingNowSeriesResolver {

    public List<SeriesRow> resolveAll() {
        List<SeriesRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            rows.addAll(resolveForAccount(account));
        }
        return rows;
    }

    public List<SeriesRow> resolveForAccount(Account account) {
        List<SeriesRow> rows = new ArrayList<>();
        if (account == null || isBlank(account.getDbId())) {
            return rows;
        }
        Map<String, SeriesWatchState> deduped = dedupeSeriesStates(account.getDbId());
        for (SeriesWatchState state : deduped.values()) {
            SeriesRow row = buildRow(account, state);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, SeriesWatchState> dedupeSeriesStates(String accountId) {
        Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
        for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(accountId)) {
            if (state == null || isBlank(state.getSeriesId())) {
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

    private SeriesRow buildRow(Account account, SeriesWatchState state) {
        SnapshotScope scope = resolveSnapshotScope(state);
        SeriesWatchState scopedState = copyStateWithScope(state, scope.categoryId, scope.parentChannelId);
        SeriesCacheInfo cacheInfo = resolveSeriesInfoFromCache(account, scopedState);
        if (!isBlank(scope.seriesTitle)) {
            cacheInfo = new SeriesCacheInfo(scope.seriesTitle, firstNonBlank(scope.seriesPoster, cacheInfo.seriesPoster), true);
        } else if (!isBlank(scope.seriesPoster)) {
            cacheInfo = new SeriesCacheInfo(cacheInfo.seriesTitle, scope.seriesPoster, cacheInfo.resolvedFromCache);
        }
        if (!cacheInfo.resolvedFromCache && scopedState.getSeriesId().matches("^\\d+$")) {
            return null;
        }
        String categoryDbId = resolveSeriesCategoryDbId(account, scopedState.getCategoryId());
        if (isBlank(categoryDbId)) {
            categoryDbId = safe(scopedState.getCategoryId());
        }
        return new SeriesRow(account, scopedState, cacheInfo.seriesTitle, cacheInfo.seriesPoster, categoryDbId, cacheInfo.resolvedFromCache);
    }

    private SeriesCacheInfo resolveSeriesInfoFromCache(Account account, SeriesWatchState state) {
        SeriesCacheInfo directMatch = resolveSeriesInfoFromCandidateCategories(account, state);
        if (!needsSeriesCacheFallback(directMatch, state)) {
            return directMatch;
        }
        SeriesCacheInfo fallbackMatch = resolveSeriesInfoFromAllCategories(account, state, directMatch);
        return fallbackMatch != null ? fallbackMatch : directMatch;
    }

    private SeriesCacheInfo resolveSeriesInfoFromCandidateCategories(Account account, SeriesWatchState state) {
        String defaultTitle = state.getSeriesId();
        for (String categoryCandidate : buildSeriesCategoryCandidates(account, state)) {
            Channel match = findSeriesChannel(account, categoryCandidate, state.getSeriesId());
            if (match != null) {
                return buildSeriesCacheInfo(match, defaultTitle, true);
            }
        }
        return new SeriesCacheInfo(firstNonBlank(defaultTitle, state.getSeriesId()), "", false);
    }

    private List<String> buildSeriesCategoryCandidates(Account account, SeriesWatchState state) {
        String categoryDbId = resolveSeriesCategoryDbId(account, state.getCategoryId());
        List<String> categoryCandidates = new ArrayList<>();
        categoryCandidates.add(safe(state.getCategoryId()));
        if (!isBlank(categoryDbId)) {
            categoryCandidates.add(categoryDbId);
        }
        return categoryCandidates;
    }

    private boolean needsSeriesCacheFallback(SeriesCacheInfo cacheInfo, SeriesWatchState state) {
        return cacheInfo == null
                || isBlank(cacheInfo.seriesTitle)
                || cacheInfo.seriesTitle.equals(state.getSeriesId());
    }

    private SeriesCacheInfo resolveSeriesInfoFromAllCategories(Account account, SeriesWatchState state, SeriesCacheInfo current) {
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            Channel match = findSeriesChannel(account, category.getDbId(), state.getSeriesId());
            if (match != null) {
                String defaultTitle = current == null ? state.getSeriesId() : current.seriesTitle;
                return buildSeriesCacheInfo(match, defaultTitle, true);
            }
        }
        return null;
    }

    private Channel findSeriesChannel(Account account, String categoryId, String seriesId) {
        if (isBlank(categoryId)) {
            return null;
        }
        List<Channel> channels = SeriesChannelDb.get().getChannels(account, categoryId);
        return channels.stream()
                .filter(c -> safe(c.getChannelId()).equals(safe(seriesId)))
                .findFirst()
                .orElse(null);
    }

    private SeriesCacheInfo buildSeriesCacheInfo(Channel match, String defaultTitle, boolean resolved) {
        String title = firstNonBlank(match.getName(), defaultTitle);
        String poster = firstNonBlank(match.getLogo(), "");
        return new SeriesCacheInfo(firstNonBlank(title, defaultTitle), poster, resolved);
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
            // Snapshot payloads are optional; keep the original category id when parsing fails.
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
            // Snapshot payloads are optional; keep the original series/channel scope when parsing fails.
        }
        return new SnapshotScope(categoryId, parentChannelId, title, poster);
    }

    private SeriesWatchState copyStateWithScope(SeriesWatchState source, String categoryId, String parentChannelId) {
        SeriesWatchState scoped = new SeriesWatchState();
        if (source == null) {
            return scoped;
        }
        scoped.setDbId(source.getDbId());
        scoped.setAccountId(source.getAccountId());
        scoped.setMode(source.getMode());
        scoped.setCategoryId(firstNonBlank(categoryId, source.getCategoryId()));
        scoped.setSeriesId(firstNonBlank(parentChannelId, source.getSeriesId()));
        scoped.setEpisodeId(source.getEpisodeId());
        scoped.setEpisodeName(source.getEpisodeName());
        scoped.setSeason(source.getSeason());
        scoped.setEpisodeNum(source.getEpisodeNum());
        scoped.setUpdatedAt(source.getUpdatedAt());
        scoped.setSource(source.getSource());
        scoped.setSeriesCategorySnapshot(source.getSeriesCategorySnapshot());
        scoped.setSeriesChannelSnapshot(source.getSeriesChannelSnapshot());
        scoped.setSeriesEpisodeSnapshot(source.getSeriesEpisodeSnapshot());
        return scoped;
    }

    private String resolveSeriesCategoryDbId(Account account, String apiCategoryId) {
        if (account == null || isBlank(account.getDbId()) || isBlank(apiCategoryId)) {
            return "";
        }
        String target = safe(apiCategoryId);
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            if (target.equals(safe(category.getCategoryId())) || target.equals(safe(category.getDbId()))) {
                return safe(category.getDbId());
            }
        }
        return "";
    }

    private String normalizeSeriesIdentity(String seriesId) {
        String normalized = safe(seriesId);
        if (isBlank(normalized)) {
            return "";
        }
        if (!normalized.contains(":")) {
            return normalized;
        }
        String[] parts = normalized.split(":");
        for (String part : parts) {
            String p = safe(part);
            if (!isBlank(p)) {
                return p;
            }
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class SeriesRow {
        private final Account account;
        private final SeriesWatchState state;
        private final String seriesTitle;
        private final String seriesPoster;
        private final String categoryDbId;
        private final boolean resolvedFromCache;

        private SeriesRow(Account account, SeriesWatchState state, String seriesTitle, String seriesPoster, String categoryDbId, boolean resolvedFromCache) {
            this.account = account;
            this.state = state;
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.categoryDbId = categoryDbId;
            this.resolvedFromCache = resolvedFromCache;
        }

        public Account getAccount() {
            return account;
        }

        public SeriesWatchState getState() {
            return state;
        }

        public String getSeriesTitle() {
            return seriesTitle;
        }

        public String getSeriesPoster() {
            return seriesPoster;
        }

        public String getCategoryDbId() {
            return categoryDbId;
        }

        public boolean isResolvedFromCache() {
            return resolvedFromCache;
        }
    }

    private static final class SeriesCacheInfo {
        private final String seriesTitle;
        private final String seriesPoster;
        private final boolean resolvedFromCache;

        private SeriesCacheInfo(String seriesTitle, String seriesPoster, boolean resolvedFromCache) {
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.resolvedFromCache = resolvedFromCache;
        }
    }

    private static final class SnapshotScope {
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
}
