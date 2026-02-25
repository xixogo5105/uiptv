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
import com.uiptv.service.HandshakeService;
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
    private static final long EPISODE_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, "[]");
            return;
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        String seriesId = getParam(ex, "seriesId");
        String rawCategoryId = getParam(ex, "categoryId");
        String categoryId = resolveSeriesCategoryId(rawCategoryId);
        List<Channel> episodesAsChannels = new ArrayList<>();
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, "[]");
            return;
        }

        List<Channel> cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, seriesId);
        if (!cachedEpisodes.isEmpty() && SeriesEpisodeDb.get().isFresh(account, seriesId, EPISODE_CACHE_TTL_MS)) {
            applyWatchedFlag(cachedEpisodes, account, categoryId, seriesId);
            generateJsonResponse(ex, ServerUtils.objectToJson(cachedEpisodes));
            return;
        }

        if (account.getType() == AccountType.XTREME_API && StringUtils.isNotBlank(seriesId)) {
            EpisodeList episodes = XtremeParser.parseEpisodes(seriesId, account);
            if (episodes != null && episodes.episodes != null) {
                for (Episode episode : episodes.episodes) {
                    if (episode == null) {
                        continue;
                    }
                    Channel channel = new Channel();
                    channel.setChannelId(episode.getId());
                    channel.setName(episode.getTitle());
                    channel.setCmd(episode.getCmd());
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
                    episodesAsChannels.add(channel);
                }
            }
        }

        if (!episodesAsChannels.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, seriesId, episodesAsChannels);
        }
        applyWatchedFlag(episodesAsChannels, account, categoryId, seriesId);
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels));
    }

    private void applyWatchedFlag(List<Channel> episodes, Account account, String categoryId, String seriesId) {
        if (episodes == null || episodes.isEmpty() || account == null) {
            return;
        }
        SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), categoryId, seriesId);
        String watchedEpisodeId = state == null ? "" : state.getEpisodeId();
        String watchedSeason = state == null ? "" : state.getSeason();
        String watchedEpisodeNum = state == null || state.getEpisodeNum() <= 0 ? "" : String.valueOf(state.getEpisodeNum());
        for (Channel channel : episodes) {
            if (channel == null) {
                continue;
            }
            channel.setWatched(isMatchingWatchedEpisode(
                    watchedEpisodeId,
                    watchedSeason,
                    watchedEpisodeNum,
                    channel.getChannelId(),
                    channel.getSeason(),
                    channel.getEpisodeNum()
            ));
        }
    }

    private boolean isMatchingWatchedEpisode(String watchedEpisodeId,
                                             String watchedSeason,
                                             String watchedEpisodeNum,
                                             String episodeId,
                                             String season,
                                             String episodeNum) {
        if (StringUtils.isBlank(watchedEpisodeId) || StringUtils.isBlank(episodeId)) {
            return false;
        }
        if (!watchedEpisodeId.equals(episodeId)) {
            return false;
        }
        String ws = digitsOnly(watchedSeason);
        String s = digitsOnly(season);
        if (StringUtils.isNotBlank(ws) && StringUtils.isNotBlank(s) && !ws.equals(s)) {
            return false;
        }
        String we = digitsOnly(watchedEpisodeNum);
        String e = digitsOnly(episodeNum);
        return StringUtils.isBlank(we) || StringUtils.isBlank(e) || we.equals(e);
    }

    private String digitsOnly(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
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
