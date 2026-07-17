package com.uiptv.application;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.service.WatchingNowSeriesResolver;
import com.uiptv.service.WatchingNowVodResolver;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("java:S6548")
public class WatchingNowApplicationService {
    private final WatchingNowSeriesResolver seriesResolver = new WatchingNowSeriesResolver();
    private final WatchingNowVodResolver vodResolver = new WatchingNowVodResolver();

    private WatchingNowApplicationService() {
    }

    public static WatchingNowApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public List<WatchingNowSeriesRow> listSeriesRows() {
        List<WatchingNowSeriesRow> rows = new ArrayList<>();
        for (WatchingNowSeriesResolver.SeriesRow row : seriesResolver.resolveAll()) {
            SeriesWatchState state = row.getState();
            Account account = row.getAccount();
            rows.add(new WatchingNowSeriesRow(
                    safe(account.getDbId()),
                    safe(account.getAccountName()),
                    safe(account.getType() != null ? account.getType().name() : ""),
                    safe(state.getCategoryId()),
                    safe(row.getCategoryDbId()),
                    safe(state.getSeriesId()),
                    safe(state.getEpisodeId()),
                    safe(state.getEpisodeName()),
                    safe(state.getSeason()),
                    state.getEpisodeNum(),
                    safe(row.getSeriesTitle()),
                    safe(row.getSeriesPoster()),
                    state.getUpdatedAt()
            ));
        }
        rows.sort(
                Comparator.comparingLong(WatchingNowSeriesRow::updatedAt).reversed()
                        .thenComparing(row -> safe(row.seriesTitle()), String.CASE_INSENSITIVE_ORDER)
        );
        return rows;
    }

    public List<Channel> listSeriesEpisodes(String accountId, String categoryId, String seriesId) {
        Account account = AccountService.getInstance().getById(accountId);
        if (account == null || StringUtils.isBlank(seriesId)) {
            return List.of();
        }

        List<Channel> cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        if (cachedEpisodes.isEmpty()) {
            cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId);
        }

        List<Channel> episodesAsChannels = !cachedEpisodes.isEmpty()
                ? cachedEpisodes
                : toChannels(SeriesEpisodeService.getInstance().getEpisodesForWatchingNow(account, categoryId, seriesId, () -> false));

        applyWatchedFlag(episodesAsChannels, account, categoryId, seriesId);
        SeriesMetadata metadata = resolveMetadata(account, categoryId, seriesId);
        SeriesWatchingNowSnapshotService.getInstance().saveChannels(
                account,
                categoryId,
                seriesId,
                metadata.categoryDbId,
                metadata.seriesTitle,
                metadata.seriesPoster,
                episodesAsChannels
        );
        return episodesAsChannels;
    }

    public Account getAccount(String accountId) {
        return AccountService.getInstance().getById(accountId);
    }

    public void saveSeriesEpisode(WatchingNowSeriesActionRequest request, Account account) {
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                request.categoryId(),
                request.seriesId(),
                request.episodeId(),
                request.episodeName(),
                request.season(),
                request.episodeNum()
        );
        SeriesWatchingNowSnapshotService.getInstance().saveChannels(
                account,
                request.categoryId(),
                request.seriesId(),
                request.categoryDbId(),
                request.seriesTitle(),
                request.seriesPoster(),
                request.episodes() == null ? List.of() : request.episodes()
        );
    }

    public void removeSeries(String accountId, String categoryId, String seriesId) {
        SeriesWatchStateService.getInstance().clearSeriesLastWatched(accountId, categoryId, seriesId);
    }

    public void saveVod(WatchingNowVodActionRequest request, Account account) {
        Channel channel = new Channel();
        channel.setChannelId(request.vodId());
        channel.setCategoryId(request.categoryId());
        channel.setName(request.vodName());
        channel.setCmd(request.vodCmd());
        channel.setLogo(request.vodLogo());
        VodWatchStateService.getInstance().save(account, request.categoryId(), channel);
    }

    public void removeVod(String accountId, String categoryId, String vodId) {
        VodWatchStateService.getInstance().remove(accountId, categoryId, vodId);
    }

    public List<WatchingNowVodRow> listVodRows() {
        List<WatchingNowVodRow> rows = new ArrayList<>();
        for (WatchingNowVodResolver.VodRow row : vodResolver.resolveAll()) {
            rows.add(new WatchingNowVodRow(
                    safe(row.getAccount().getDbId()),
                    safe(row.getAccount().getAccountName()),
                    safe(row.getAccount().getType() != null ? row.getAccount().getType().name() : ""),
                    safe(row.getState().getCategoryId()),
                    safe(row.getState().getVodId()),
                    safe(row.getDisplayTitle()),
                    safe(row.getMetadata().getLogo()),
                    safe(row.getMetadata().getPlot()),
                    safe(row.getMetadata().getReleaseDate()),
                    safe(row.getMetadata().getRating()),
                    safe(row.getMetadata().getDuration()),
                    row.getState().getUpdatedAt(),
                    row.getPlaybackChannel()
            ));
        }
        rows.sort(Comparator.comparingLong(WatchingNowVodRow::updatedAt).reversed()
                .thenComparing(WatchingNowVodRow::vodName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private SeriesMetadata resolveMetadata(Account account, String categoryId, String seriesId) {
        for (WatchingNowSeriesResolver.SeriesRow row : seriesResolver.resolveForAccount(account)) {
            boolean matchingSeries = safe(seriesId).equals(safe(row.getState().getSeriesId()));
            boolean matchingCategory = StringUtils.isBlank(categoryId)
                    || safe(categoryId).equals(safe(row.getState().getCategoryId()));
            if (matchingSeries && matchingCategory) {
                return new SeriesMetadata(row.getCategoryDbId(), row.getSeriesTitle(), row.getSeriesPoster());
            }
        }
        return new SeriesMetadata("", "", "");
    }

    private List<Channel> toChannels(EpisodeList episodes) {
        List<Channel> channels = new ArrayList<>();
        if (episodes == null || episodes.getEpisodes() == null) {
            return channels;
        }
        for (Episode episode : episodes.getEpisodes()) {
            Channel channel = toChannel(episode);
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private Channel toChannel(Episode episode) {
        if (episode == null) {
            return null;
        }
        Channel channel = new Channel();
        channel.setChannelId(episode.getId());
        channel.setName(episode.getTitle());
        channel.setCmd(episode.getCmd());
        channel.setExtraJson(episode.toJson());
        channel.setSeason(episode.getSeason());
        channel.setEpisodeNum(episode.getEpisodeNum());
        if (episode.getInfo() != null) {
            channel.setLogo(episode.getInfo().getMovieImage());
            channel.setDescription(episode.getInfo().getPlot());
            channel.setReleaseDate(episode.getInfo().getReleaseDate());
            channel.setRating(episode.getInfo().getRating());
            channel.setDuration(episode.getInfo().getDuration());
        }
        return channel;
    }

    private void applyWatchedFlag(List<Channel> episodes, Account account, String categoryId, String seriesId) {
        if (episodes == null || episodes.isEmpty() || account == null) {
            return;
        }
        SeriesWatchState state = SeriesWatchStateService.getInstance()
                .getSeriesLastWatched(account.getDbId(), categoryId, seriesId);
        for (Channel channel : episodes) {
            if (channel == null) {
                continue;
            }
            channel.setWatched(SeriesWatchStateService.getInstance().isMatchingEpisode(
                    state,
                    channel.getChannelId(),
                    channel.getSeason(),
                    channel.getEpisodeNum(),
                    channel.getName()
            ));
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record SeriesMetadata(String categoryDbId, String seriesTitle, String seriesPoster) {}

    private static class SingletonHelper {
        private static final WatchingNowApplicationService INSTANCE = new WatchingNowApplicationService();
    }
}
