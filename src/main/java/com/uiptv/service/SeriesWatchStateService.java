package com.uiptv.service;

import com.uiptv.db.SeriesWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.util.StringUtils.isBlank;

public class SeriesWatchStateService {
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b");
    private static SeriesWatchStateService instance;
    private final Set<SeriesWatchStateChangeListener> listeners = new CopyOnWriteArraySet<>();

    private SeriesWatchStateService() {
    }

    public static synchronized SeriesWatchStateService getInstance() {
        if (instance == null) {
            instance = new SeriesWatchStateService();
        }
        return instance;
    }

    public SeriesWatchState getSeriesLastWatched(String accountId, String seriesId) {
        return getSeriesLastWatched(accountId, "", seriesId);
    }

    public SeriesWatchState getSeriesLastWatched(String accountId, String categoryId, String seriesId) {
        if (isBlank(accountId) || isBlank(seriesId)) {
            return null;
        }
        String normalizedCategory = normalizeCategoryId(categoryId);
        SeriesWatchState exact = SeriesWatchStateDb.get().getBySeries(accountId, normalizedCategory, seriesId);
        if (exact != null) {
            return exact;
        }
        // Fallback for clients that may resolve category differently (db id vs portal id).
        // Prefer the latest pointer for the same account+series when exact category is absent.
        SeriesWatchState latest = null;
        for (SeriesWatchState candidate : SeriesWatchStateDb.get().getBySeries(accountId, seriesId)) {
            if (candidate == null) {
                continue;
            }
            if (latest == null || candidate.getUpdatedAt() > latest.getUpdatedAt()) {
                latest = candidate;
            }
        }
        return latest;
    }

    public Map<String, SeriesWatchState> getSeriesLastWatchedByAccount(String accountId) {
        return getSeriesLastWatchedByAccountAndCategory(accountId, "");
    }

    public Map<String, SeriesWatchState> getSeriesLastWatchedByAccountAndCategory(String accountId, String categoryId) {
        Map<String, SeriesWatchState> result = new LinkedHashMap<>();
        if (isBlank(accountId)) {
            return result;
        }
        boolean allCategories = isBlank(categoryId);
        for (SeriesWatchState state : (allCategories
                ? SeriesWatchStateDb.get().getByAccount(accountId)
                : SeriesWatchStateDb.get().getByAccount(accountId, normalizeCategoryId(categoryId)))) {
            if (state != null && !isBlank(state.getSeriesId())) {
                result.put(state.getSeriesId(), state);
            }
        }
        return result;
    }

    public void clearSeriesLastWatched(String accountId, String seriesId) {
        clearSeriesLastWatched(accountId, "", seriesId);
    }

    public void clearSeriesLastWatched(String accountId, String categoryId, String seriesId) {
        if (isBlank(accountId) || isBlank(seriesId)) {
            return;
        }
        SeriesWatchStateDb.get().clear(accountId, normalizeCategoryId(categoryId), seriesId);
        notifyListeners(accountId, seriesId);
    }

    public void markSeriesEpisodeManual(Account account, String seriesId, String episodeId, String episodeName, String season, String episodeNum) {
        markSeriesEpisodeManual(account, "", seriesId, episodeId, episodeName, season, episodeNum);
    }

    public void markSeriesEpisodeManual(Account account, String categoryId, String seriesId, String episodeId, String episodeName, String season, String episodeNum) {
        if (account == null || isBlank(account.getDbId()) || isBlank(seriesId) || isBlank(episodeId)) {
            return;
        }
        upsertState(account.getDbId(), normalizeCategoryId(categoryId), seriesId, episodeId, episodeName, season, parseEpisodeNum(episodeNum, episodeName), "MANUAL");
    }

    public void onPlaybackResolved(Account account, Channel channel, String requestedSeriesId, String parentSeriesId) {
        onPlaybackResolved(account, channel, requestedSeriesId, parentSeriesId, "");
    }

    public void onPlaybackResolved(Account account, Channel channel, String requestedSeriesId, String parentSeriesId, String categoryId) {
        if (account == null || channel == null || account.getAction() != series || isBlank(account.getDbId())) {
            return;
        }
        String resolvedCategoryId = normalizeCategoryId(categoryId);
        String seriesId = firstNonBlank(parentSeriesId);
        if (isBlank(seriesId)) {
            return;
        }
        String episodeId = channel.getChannelId();
        if (isBlank(episodeId)) {
            return;
        }

        int nextEpisodeNum = parseEpisodeNum(channel.getEpisodeNum(), channel.getName());
        int nextSeasonNum = parseSeasonNum(channel.getSeason(), channel.getName());
        SeriesWatchState existing = SeriesWatchStateDb.get().getBySeries(account.getDbId(), resolvedCategoryId, seriesId);

        if (existing == null) {
            upsertState(account.getDbId(), resolvedCategoryId, seriesId, episodeId, channel.getName(), channel.getSeason(), nextEpisodeNum, "AUTO");
            return;
        }

        int currentSeasonNum = parseSeasonNum(existing.getSeason(), existing.getEpisodeName());
        int currentEpisodeNum = existing.getEpisodeNum();
        if (nextEpisodeNum <= 0 && nextSeasonNum <= 0) {
            return;
        }
        if (shouldAdvancePointer(currentSeasonNum, currentEpisodeNum, nextSeasonNum, nextEpisodeNum)) {
            upsertState(account.getDbId(), resolvedCategoryId, seriesId, episodeId, channel.getName(), channel.getSeason(), nextEpisodeNum, "AUTO");
        }
    }

    public void addChangeListener(SeriesWatchStateChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(SeriesWatchStateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void upsertState(String accountId, String categoryId, String seriesId, String episodeId, String episodeName, String season, int episodeNum, String source) {
        String normalizedSeason = normalizeSeason(season, episodeName);
        int normalizedEpisodeNum = episodeNum > 0 ? episodeNum : parseEpisodeNum("", episodeName);
        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(accountId);
        state.setMode("series");
        state.setCategoryId(normalizeCategoryId(categoryId));
        state.setSeriesId(seriesId);
        state.setEpisodeId(episodeId);
        state.setEpisodeName(episodeName);
        state.setSeason(normalizedSeason);
        state.setEpisodeNum(normalizedEpisodeNum);
        state.setUpdatedAt(System.currentTimeMillis());
        state.setSource(source);
        SeriesWatchStateDb.get().upsert(state);
        notifyListeners(accountId, seriesId);
    }

    public boolean isMatchingEpisode(SeriesWatchState watchedState,
                                     String episodeId,
                                     String season,
                                     String episodeNum,
                                     String episodeName) {
        if (watchedState == null) {
            return false;
        }
        if (isBlank(watchedState.getEpisodeId()) || isBlank(episodeId)) {
            return false;
        }
        if (!watchedState.getEpisodeId().trim().equals(episodeId.trim())) {
            return false;
        }

        String watchedSeason = stripToDigits(watchedState.getSeason());
        String candidateSeason = stripToDigits(normalizeSeason(season, episodeName));
        if (!isBlank(watchedSeason)) {
            if (isBlank(candidateSeason) || !watchedSeason.equals(candidateSeason)) {
                return false;
            }
        }

        String watchedEpisodeNum = watchedState.getEpisodeNum() > 0
                ? String.valueOf(watchedState.getEpisodeNum())
                : "";
        String candidateEpisodeNum = stripToDigits(
                !isBlank(episodeNum) ? episodeNum : String.valueOf(parseEpisodeNum("", episodeName)));
        if (!isBlank(watchedEpisodeNum)) {
            if (isBlank(candidateEpisodeNum) || !watchedEpisodeNum.equals(candidateEpisodeNum)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeCategoryId(String categoryId) {
        return isBlank(categoryId) ? "" : categoryId.trim();
    }

    private void notifyListeners(String accountId, String seriesId) {
        for (SeriesWatchStateChangeListener listener : listeners) {
            try {
                listener.onSeriesWatchStateChanged(accountId, seriesId);
            } catch (Exception ignored) {
                // Listener errors should not break watch-state writes.
            }
        }
    }

    private int parseEpisodeNum(String explicitEpisodeNum, String fallbackTitle) {
        String onlyDigits = stripToDigits(explicitEpisodeNum);
        if (!isBlank(onlyDigits)) {
            return Integer.parseInt(onlyDigits);
        }
        String title = fallbackTitle == null ? "" : fallbackTitle;
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find() && !isBlank(sxey.group(2))) {
            return Integer.parseInt(sxey.group(2));
        }
        Matcher episodeMatcher = EPISODE_PATTERN.matcher(title);
        if (episodeMatcher.find()) {
            String parsed = !isBlank(episodeMatcher.group(1)) ? episodeMatcher.group(1) : episodeMatcher.group(2);
            if (!isBlank(parsed)) {
                return Integer.parseInt(parsed);
            }
        }
        return 0;
    }

    private int parseSeasonNum(String explicitSeason, String fallbackTitle) {
        String normalized = normalizeSeason(explicitSeason, fallbackTitle);
        if (isBlank(normalized)) {
            return 0;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean shouldAdvancePointer(int currentSeasonNum,
                                         int currentEpisodeNum,
                                         int nextSeasonNum,
                                         int nextEpisodeNum) {
        if (nextSeasonNum > 0 && currentSeasonNum > 0) {
            if (nextSeasonNum > currentSeasonNum) {
                return true;
            }
            if (nextSeasonNum < currentSeasonNum) {
                return false;
            }
            if (nextEpisodeNum <= 0) {
                return false;
            }
            return currentEpisodeNum <= 0 || nextEpisodeNum > currentEpisodeNum;
        }

        if (nextEpisodeNum <= 0) {
            return false;
        }
        return currentEpisodeNum <= 0 || nextEpisodeNum > currentEpisodeNum;
    }

    private String normalizeSeason(String explicitSeason, String fallbackTitle) {
        String fromValue = stripToDigits(explicitSeason);
        if (!isBlank(fromValue)) {
            return String.valueOf(Integer.parseInt(fromValue));
        }
        String title = fallbackTitle == null ? "" : fallbackTitle;
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find() && !isBlank(sxey.group(1))) {
            return String.valueOf(Integer.parseInt(sxey.group(1)));
        }
        Matcher seasonMatcher = SEASON_PATTERN.matcher(title);
        if (seasonMatcher.find()) {
            String parsed = !isBlank(seasonMatcher.group(1))
                    ? seasonMatcher.group(1)
                    : (!isBlank(seasonMatcher.group(2)) ? seasonMatcher.group(2) : seasonMatcher.group(3));
            if (!isBlank(parsed)) {
                return String.valueOf(Integer.parseInt(parsed));
            }
        }
        return "";
    }

    private String stripToDigits(String value) {
        if (isBlank(value)) {
            return "";
        }
        String parsed = value.replaceAll("[^0-9]", "");
        return isBlank(parsed) ? "" : parsed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
