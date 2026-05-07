package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.WatchingNowSeriesResolver;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpWatchingNowSeriesEpisodesJsonServer implements HttpHandler {
    private final WatchingNowSeriesResolver resolver = new WatchingNowSeriesResolver();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String emptyJson = "[]";
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, emptyJson);
            return;
        }
        String seriesId = getParam(ex, "seriesId");
        String categoryId = getParam(ex, "categoryId");
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, emptyJson);
            return;
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
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels));
    }

    private SeriesMetadata resolveMetadata(Account account, String categoryId, String seriesId) {
        for (WatchingNowSeriesResolver.SeriesRow row : resolver.resolveForAccount(account)) {
            if (!safe(seriesId).equals(safe(row.getState().getSeriesId()))) {
                continue;
            }
            if (!StringUtils.isBlank(categoryId) && !safe(categoryId).equals(safe(row.getState().getCategoryId()))) {
                continue;
            }
            return new SeriesMetadata(row.getCategoryDbId(), row.getSeriesTitle(), row.getSeriesPoster());
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
        com.uiptv.model.SeriesWatchState state = SeriesWatchStateService.getInstance()
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

    private static final class SeriesMetadata {
        private final String categoryDbId;
        private final String seriesTitle;
        private final String seriesPoster;

        private SeriesMetadata(String categoryDbId, String seriesTitle, String seriesPoster) {
            this.categoryDbId = categoryDbId == null ? "" : categoryDbId;
            this.seriesTitle = seriesTitle == null ? "" : seriesTitle;
            this.seriesPoster = seriesPoster == null ? "" : seriesPoster;
        }
    }
}
