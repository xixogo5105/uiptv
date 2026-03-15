package com.uiptv.service;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;

public class WatchingNowSeriesResolver {
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^\\d+$");

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
        if (!cacheInfo.resolvedFromCache && isAllDigits(scopedState.getSeriesId())) {
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
        String rawSeriesId = safe(state.getSeriesId());
        String defaultTitle = rawSeriesId;
        String normalizedSeriesId = normalizeSeriesIdentity(rawSeriesId);
        if (!isBlank(normalizedSeriesId) && !normalizedSeriesId.equals(rawSeriesId)) {
            defaultTitle = normalizedSeriesId;
        }
        List<String> seriesIdCandidates = buildSeriesIdCandidates(rawSeriesId);
        for (String categoryCandidate : buildSeriesCategoryCandidates(account, state)) {
            Channel match = findSeriesChannel(account, categoryCandidate, seriesIdCandidates);
            if (match != null) {
                return buildSeriesCacheInfo(match, defaultTitle, true);
            }
        }
        return new SeriesCacheInfo(firstNonBlank(defaultTitle, rawSeriesId), "", false);
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
        List<String> seriesIdCandidates = buildSeriesIdCandidates(state.getSeriesId());
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            Channel match = findSeriesChannel(account, category.getDbId(), seriesIdCandidates);
            if (match != null) {
                String defaultTitle = current == null ? state.getSeriesId() : current.seriesTitle;
                return buildSeriesCacheInfo(match, defaultTitle, true);
            }
        }
        return null;
    }

    private Channel findSeriesChannel(Account account, String categoryId, List<String> seriesIds) {
        if (isBlank(categoryId) || seriesIds == null || seriesIds.isEmpty()) {
            return null;
        }
        List<Channel> channels = SeriesChannelDb.get().getChannels(account, categoryId);
        for (String seriesId : seriesIds) {
            String target = safe(seriesId);
            if (isBlank(target)) {
                continue;
            }
            for (Channel channel : channels) {
                if (channel != null && target.equals(safe(channel.getChannelId()))) {
                    return channel;
                }
            }
        }
        return null;
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
                title = firstNonBlank(channel.getName(), title);
                poster = firstNonBlank(channel.getLogo(), poster);
            }
        } catch (Exception _) {
            // Snapshot payloads are optional; keep the original series/channel scope when parsing fails.
        }
        SnapshotData snapshotData = extractSnapshotData(state.getSeriesChannelSnapshot());
        if (snapshotData != null) {
            title = firstNonBlank(snapshotData.title, title);
            poster = firstNonBlank(snapshotData.poster, poster);
        }
        SnapshotData episodeData = extractSnapshotData(state.getSeriesEpisodeSnapshot());
        if (episodeData != null) {
            if (isBlank(title)) {
                title = firstNonBlank(episodeData.seriesTitle, title);
            }
            if (isBlank(poster)) {
                poster = firstNonBlank(episodeData.poster, poster);
            }
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
        if (areAllPartsNumeric(parts)) {
            String lastNumeric = lastNonBlank(parts);
            if (!isBlank(lastNumeric)) {
                return lastNumeric;
            }
        }
        String first = firstNonBlankPart(parts);
        return isBlank(first) ? normalized : first;
    }

    private SnapshotData extractSnapshotData(String snapshotJson) {
        String raw = safe(snapshotJson);
        if (isBlank(raw)) {
            return null;
        }
        JSONObject payload = null;
        try {
            payload = new JSONObject(raw);
        } catch (Exception _) {
            try {
                JSONArray array = new JSONArray(raw);
                if (!array.isEmpty()) {
                    Object first = array.get(0);
                    if (first instanceof JSONObject jsonObject) {
                        payload = jsonObject;
                    }
                }
            } catch (Exception _) {
                return null;
            }
        }
        if (payload == null) {
            return null;
        }
        String title = firstNonBlank(
                safe(payload.optString("seriesTitle")),
                safe(payload.optString("seriesName")),
                safe(payload.optString("name")),
                safe(payload.optString("channelName")),
                safe(payload.optString("title"))
        );
        String seriesTitle = firstNonBlank(
                safe(payload.optString("seriesTitle")),
                safe(payload.optString("seriesName"))
        );
        String poster = firstNonBlank(
                safe(payload.optString("logo")),
                safe(payload.optString("poster")),
                safe(payload.optString("cover")),
                safe(payload.optString("movieImage"))
        );
        return new SnapshotData(title, seriesTitle, poster);
    }

    private static final class SnapshotData {
        private final String title;
        private final String seriesTitle;
        private final String poster;

        private SnapshotData(String title, String seriesTitle, String poster) {
            this.title = title == null ? "" : title;
            this.seriesTitle = seriesTitle == null ? "" : seriesTitle;
            this.poster = poster == null ? "" : poster;
        }
    }

    private boolean areAllPartsNumeric(String[] parts) {
        boolean allNumeric = true;
        for (String part : parts) {
            String p = safe(part);
            if (!isBlank(p) && !isAllDigits(p)) {
                allNumeric = false;
            }
        }
        return allNumeric;
    }

    private String lastNonBlank(String[] parts) {
        String last = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = safe(parts[i]);
            if (!isBlank(p) && isBlank(last)) {
                last = p;
            }
        }
        return last;
    }

    private String firstNonBlankPart(String[] parts) {
        for (String part : parts) {
            String p = safe(part);
            if (!isBlank(p)) {
                return p;
            }
        }
        return "";
    }

    private List<String> buildSeriesIdCandidates(String seriesId) {
        String raw = safe(seriesId);
        if (isBlank(raw)) {
            return List.of();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeSeriesIdentity(raw);
        if (!isBlank(normalized)) {
            candidates.add(normalized);
        }
        if (raw.contains(":")) {
            for (String part : raw.split(":")) {
                String p = safe(part);
                if (!isBlank(p)) {
                    candidates.add(p);
                }
            }
        }
        if (!raw.contains(":") && isAllDigits(raw)) {
            candidates.add(raw + ":" + raw);
        }
        candidates.add(raw);
        return new ArrayList<>(candidates);
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

    private boolean isAllDigits(String value) {
        String normalized = safe(value);
        return !isBlank(normalized) && DIGITS_ONLY_PATTERN.matcher(normalized).matches();
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
