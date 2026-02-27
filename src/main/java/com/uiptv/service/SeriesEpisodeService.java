package com.uiptv.service;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;

public class SeriesEpisodeService {
    private static final Pattern SERIES_SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern SERIES_EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    private static SeriesEpisodeService instance;

    private SeriesEpisodeService() {
    }

    public static synchronized SeriesEpisodeService getInstance() {
        if (instance == null) {
            instance = new SeriesEpisodeService();
        }
        return instance;
    }

    public EpisodeList getEpisodes(Account account, String categoryId, String seriesId, Supplier<Boolean> isCancelled) {
        if (account == null || isBlank(seriesId)) {
            return new EpisodeList();
        }

        EpisodeList cached = loadFromDbCache(account, categoryId, seriesId);
        if (cached != null && cached.episodes != null && !cached.episodes.isEmpty()) {
            return cached;
        }

        if (account.getType() == XTREME_API) {
            EpisodeList episodes = XtremeParser.parseEpisodes(seriesId, account);
            saveEpisodesToDbCache(account, categoryId, seriesId, episodes);
            return episodes == null ? new EpisodeList() : episodes;
        }

        if (account.getType() == STALKER_PORTAL) {
            List<Channel> seriesChannels = ChannelService.getInstance().getSeries(categoryId, seriesId, account, null, isCancelled);
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, seriesChannels);
            return toEpisodeList(seriesChannels);
        }

        return new EpisodeList();
    }

    private EpisodeList loadFromDbCache(Account account, String categoryId, String seriesId) {
        List<Channel> cachedChannels = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        if (cachedChannels == null || cachedChannels.isEmpty()) {
            return null;
        }
        if (!SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return null;
        }
        return toEpisodeList(cachedChannels);
    }

    private void saveEpisodesToDbCache(Account account, String categoryId, String seriesId, EpisodeList episodes) {
        if (account == null || isBlank(seriesId) || episodes == null || episodes.episodes == null || episodes.episodes.isEmpty()) {
            return;
        }
        List<Channel> channels = new ArrayList<>();
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
            channel.setExtraJson(episode.toJson());
            if (episode.getInfo() != null) {
                channel.setLogo(episode.getInfo().getMovieImage());
                channel.setDescription(episode.getInfo().getPlot());
                channel.setReleaseDate(episode.getInfo().getReleaseDate());
                channel.setRating(episode.getInfo().getRating());
                channel.setDuration(episode.getInfo().getDuration());
            }
            channels.add(channel);
        }
        if (!channels.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, channels);
        }
    }

    private EpisodeList toEpisodeList(List<Channel> channels) {
        EpisodeList list = new EpisodeList();
        if (channels == null || channels.isEmpty()) {
            return list;
        }
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            Episode parsed = Episode.fromJson(channel.getExtraJson());
            Episode episode = new Episode();
            if (isParsedEpisodeCompatible(parsed, channel)) {
                episode = parsed;
            } else {
                episode.setId(channel.getChannelId());
                episode.setTitle(channel.getName());
                episode.setCmd(channel.getCmd());
                episode.setSeason(isBlank(channel.getSeason()) ? extractSeason(channel.getName()) : channel.getSeason());
                episode.setEpisodeNum(isBlank(channel.getEpisodeNum()) ? extractEpisode(channel.getName()) : channel.getEpisodeNum());
            }
            if (isBlank(episode.getId())) episode.setId(channel.getChannelId());
            if (isBlank(episode.getTitle())) episode.setTitle(channel.getName());
            if (isBlank(episode.getCmd())) episode.setCmd(channel.getCmd());
            if (isBlank(episode.getSeason())) episode.setSeason(isBlank(channel.getSeason()) ? extractSeason(channel.getName()) : channel.getSeason());
            if (isBlank(episode.getEpisodeNum())) episode.setEpisodeNum(isBlank(channel.getEpisodeNum()) ? extractEpisode(channel.getName()) : channel.getEpisodeNum());
            EpisodeInfo info = new EpisodeInfo();
            info.setMovieImage(channel.getLogo());
            info.setPlot(channel.getDescription());
            info.setReleaseDate(channel.getReleaseDate());
            info.setRating(channel.getRating());
            info.setDuration(channel.getDuration());
            if (episode.getInfo() == null) {
                episode.setInfo(info);
            } else {
                if (isBlank(episode.getInfo().getMovieImage())) episode.getInfo().setMovieImage(info.getMovieImage());
                if (isBlank(episode.getInfo().getPlot())) episode.getInfo().setPlot(info.getPlot());
                if (isBlank(episode.getInfo().getReleaseDate())) episode.getInfo().setReleaseDate(info.getReleaseDate());
                if (isBlank(episode.getInfo().getRating())) episode.getInfo().setRating(info.getRating());
                if (isBlank(episode.getInfo().getDuration())) episode.getInfo().setDuration(info.getDuration());
            }
            list.episodes.add(episode);
        }
        return list;
    }

    private boolean isParsedEpisodeCompatible(Episode parsed, Channel channel) {
        if (parsed == null || channel == null) {
            return false;
        }
        String parsedId = safe(parsed.getId());
        String cachedId = safe(channel.getChannelId());
        if (!isBlank(parsedId) && !isBlank(cachedId)) {
            if (!parsedId.equals(cachedId)) {
                return false;
            }
            return true;
        }
        String parsedCmd = safe(parsed.getCmd());
        String cachedCmd = safe(channel.getCmd());
        if (!isBlank(parsedCmd) && !isBlank(cachedCmd) && parsedCmd.equals(cachedCmd)) {
            return true;
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractSeason(String title) {
        if (isBlank(title)) return "1";
        Matcher matcher = SERIES_SEASON_PATTERN.matcher(title);
        if (matcher.find()) {
            String v1 = matcher.group(1);
            if (!isBlank(v1)) return v1;
            String v2 = matcher.group(2);
            if (!isBlank(v2)) return v2;
            String v3 = matcher.group(3);
            if (!isBlank(v3)) return v3;
        }
        return "1";
    }

    private String extractEpisode(String title) {
        if (isBlank(title)) return "";
        Matcher matcher = SERIES_EPISODE_PATTERN.matcher(title);
        if (matcher.find()) {
            String v1 = matcher.group(1);
            if (!isBlank(v1)) return v1;
            String v2 = matcher.group(2);
            if (!isBlank(v2)) return v2;
            String v3 = matcher.group(3);
            if (!isBlank(v3)) return v3;
        }
        return "";
    }
}
