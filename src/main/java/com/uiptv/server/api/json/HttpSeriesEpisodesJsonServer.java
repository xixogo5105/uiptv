package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;

public class HttpSeriesEpisodesJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String emptyJson = "[]";
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, emptyJson);
            return;
        }

        String seriesId = getParam(ex, "seriesId");
        String rawCategoryId = getParam(ex, "categoryId");
        String categoryId = resolveSeriesCategoryId(rawCategoryId);
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, emptyJson);
            return;
        }

        List<Channel> cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        if (cachedEpisodes.isEmpty() && account.getType() == AccountType.XTREME_API) {
            cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId);
        }
        if (isCachedFresh(account, categoryId, seriesId, cachedEpisodes)) {
            applyWatchedFlag(cachedEpisodes, account, categoryId, seriesId);
            generateJsonResponse(ex, ServerUtils.objectToJson(cachedEpisodes));
            return;
        }

        List<Channel> episodesAsChannels = loadEpisodes(account, seriesId);

        if (!episodesAsChannels.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, episodesAsChannels);
        } else if (account.getType() == AccountType.XTREME_API) {
            episodesAsChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId);
        }
        applyWatchedFlag(episodesAsChannels, account, categoryId, seriesId);
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels));
    }

    private boolean isCachedFresh(Account account, String categoryId, String seriesId, List<Channel> cachedEpisodes) {
        return !cachedEpisodes.isEmpty() && (
                SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())
                        || (account.getType() == AccountType.XTREME_API
                        && SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, ConfigurationService.getInstance().getCacheExpiryMs()))
        );
    }

    private List<Channel> loadEpisodes(Account account, String seriesId) {
        if (account.getType() == AccountType.XTREME_API && StringUtils.isNotBlank(seriesId)) {
            return toChannels(XtremeParser.parseEpisodes(seriesId, account));
        }
        return new ArrayList<>();
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
            if (StringUtils.isBlank(channel.getSeason())) {
                channel.setSeason(episode.getInfo().getSeason());
            }
        }
        return channel;
    }

    private void applyWatchedFlag(List<Channel> episodes, Account account, String categoryId, String seriesId) {
        if (episodes == null || episodes.isEmpty() || account == null) {
            return;
        }
        SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), categoryId, seriesId);
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

    private String resolveSeriesCategoryId(String rawCategoryId) {
        if (StringUtils.isBlank(rawCategoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(rawCategoryId);
        if (category != null && StringUtils.isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return rawCategoryId;
    }
}
