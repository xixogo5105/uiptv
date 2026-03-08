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

@SuppressWarnings("java:S6548")
public class SeriesEpisodeService {
    private static final Pattern SEASON_WORD_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b");
    private static final Pattern SEASON_SHORT_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})(?=\\b|E\\d+)");
    private static final Pattern SEASON_X_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_WORD_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b");
    private static final Pattern EPISODE_SHORT_PATTERN = Pattern.compile("(?i)\\bE(\\d{1,3})\\b");
    private static final Pattern EPISODE_X_PATTERN = Pattern.compile("(?i)\\b\\d{1,2}x(\\d{1,3})\\b");
    private SeriesEpisodeService() {
    }

    private static class SingletonHelper {
        private static final SeriesEpisodeService INSTANCE = new SeriesEpisodeService();
    }

    public static SeriesEpisodeService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public EpisodeList getEpisodes(Account account, String categoryId, String seriesId, Supplier<Boolean> isCancelled) {
        if (account == null || isBlank(seriesId)) {
            return new EpisodeList();
        }

        EpisodeList cached = loadFromDbCache(account, categoryId, seriesId);
        if (hasEpisodes(cached)) {
            return cached;
        }

        EpisodeList fetched = fetchEpisodesFromPortal(account, categoryId, seriesId, isCancelled);
        if (hasEpisodes(fetched)) {
            return fetched;
        }
        return new EpisodeList();
    }

    public EpisodeList reloadEpisodesFromPortal(Account account, String categoryId, String seriesId, Supplier<Boolean> isCancelled) {
        if (account == null || isBlank(seriesId)) {
            return new EpisodeList();
        }
        EpisodeList fetched = fetchEpisodesFromPortal(account, categoryId, seriesId, isCancelled);
        if (hasEpisodes(fetched)) {
            return fetched;
        }
        EpisodeList fallback = loadFromDbAnyAge(account, categoryId, seriesId);
        if (hasEpisodes(fallback)) {
            return fallback;
        }
        return new EpisodeList();
    }

    @SuppressWarnings("java:S4276")
    private EpisodeList fetchEpisodesFromPortal(Account account, String categoryId, String seriesId, Supplier<Boolean> isCancelled) {
        if (account.getType() == XTREME_API) {
            try {
                EpisodeList episodes = XtremeParser.parseEpisodes(seriesId, account);
                if (hasEpisodes(episodes)) {
                    saveEpisodesToDbCache(account, categoryId, seriesId, episodes);
                    return episodes;
                }
            } catch (RuntimeException _) {
                // Fall through to local cache fallback.
            }
            EpisodeList fallback = loadFromAnyCategoryCache(account, seriesId);
            return fallback != null ? fallback : new EpisodeList();
        }

        if (account.getType() == STALKER_PORTAL) {
            Supplier<Boolean> cancellationCheck = isCancelled != null ? isCancelled : () -> false;
            List<Channel> seriesChannels = ChannelService.getInstance().getSeries(categoryId, seriesId, account, null, cancellationCheck);
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, seriesChannels);
            return toEpisodeList(seriesChannels);
        }

        return new EpisodeList();
    }

    private EpisodeList loadFromDbCache(Account account, String categoryId, String seriesId) {
        List<Channel> cachedChannels = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        if (cachedChannels == null || cachedChannels.isEmpty()) {
            return loadFromAnyCategoryCache(account, seriesId);
        }
        if (!SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return loadFromAnyCategoryCache(account, seriesId);
        }
        return toEpisodeList(cachedChannels);
    }

    private EpisodeList loadFromAnyCategoryCache(Account account, String seriesId) {
        if (account == null || isBlank(seriesId) || account.getType() != XTREME_API) {
            return null;
        }
        List<Channel> cachedChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId);
        if (cachedChannels == null || cachedChannels.isEmpty()) {
            return null;
        }
        if (!SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return null;
        }
        return toEpisodeList(cachedChannels);
    }

    private EpisodeList loadFromDbAnyAge(Account account, String categoryId, String seriesId) {
        List<Channel> cachedChannels = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        if (cachedChannels != null && !cachedChannels.isEmpty()) {
            return toEpisodeList(cachedChannels);
        }
        if (account != null && account.getType() == XTREME_API) {
            List<Channel> fallback = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId);
            if (fallback != null && !fallback.isEmpty()) {
                return toEpisodeList(fallback);
            }
        }
        return null;
    }

    private void saveEpisodesToDbCache(Account account, String categoryId, String seriesId, EpisodeList episodes) {
        if (account == null || isBlank(seriesId) || !hasEpisodes(episodes)) {
            return;
        }
        List<Channel> channels = new ArrayList<>();
        for (Episode episode : episodes.getEpisodes()) {
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
            list.getEpisodes().add(toEpisode(channel));
        }
        return list;
    }

    private Episode toEpisode(Channel channel) {
        Episode episode = restoreEpisode(channel);
        populateEpisodeFallbacks(episode, channel);
        mergeEpisodeInfo(episode, buildEpisodeInfo(channel));
        return episode;
    }

    private boolean hasEpisodes(EpisodeList episodes) {
        return episodes != null && episodes.getEpisodes() != null && !episodes.getEpisodes().isEmpty();
    }

    private Episode restoreEpisode(Channel channel) {
        Episode parsed = Episode.fromJson(channel.getExtraJson());
        if (isParsedEpisodeCompatible(parsed, channel)) {
            return parsed;
        }
        Episode episode = new Episode();
        episode.setId(channel.getChannelId());
        episode.setTitle(channel.getName());
        episode.setCmd(channel.getCmd());
        episode.setSeason(resolveEpisodeSeason(channel));
        episode.setEpisodeNum(resolveEpisodeNumber(channel));
        return episode;
    }

    private void populateEpisodeFallbacks(Episode episode, Channel channel) {
        if (isBlank(episode.getId())) {
            episode.setId(channel.getChannelId());
        }
        if (isBlank(episode.getTitle())) {
            episode.setTitle(channel.getName());
        }
        if (isBlank(episode.getCmd())) {
            episode.setCmd(channel.getCmd());
        }
        if (isBlank(episode.getSeason())) {
            episode.setSeason(resolveEpisodeSeason(channel));
        }
        if (isBlank(episode.getEpisodeNum())) {
            episode.setEpisodeNum(resolveEpisodeNumber(channel));
        }
    }

    private String resolveEpisodeSeason(Channel channel) {
        return isBlank(channel.getSeason()) ? extractSeason(channel.getName()) : channel.getSeason();
    }

    private String resolveEpisodeNumber(Channel channel) {
        return isBlank(channel.getEpisodeNum()) ? extractEpisode(channel.getName()) : channel.getEpisodeNum();
    }

    private EpisodeInfo buildEpisodeInfo(Channel channel) {
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage(channel.getLogo());
        info.setPlot(channel.getDescription());
        info.setReleaseDate(channel.getReleaseDate());
        info.setRating(channel.getRating());
        info.setDuration(channel.getDuration());
        return info;
    }

    private void mergeEpisodeInfo(Episode episode, EpisodeInfo info) {
        if (episode.getInfo() == null) {
            episode.setInfo(info);
            return;
        }
        if (isBlank(episode.getInfo().getMovieImage())) {
            episode.getInfo().setMovieImage(info.getMovieImage());
        }
        if (isBlank(episode.getInfo().getPlot())) {
            episode.getInfo().setPlot(info.getPlot());
        }
        if (isBlank(episode.getInfo().getReleaseDate())) {
            episode.getInfo().setReleaseDate(info.getReleaseDate());
        }
        if (isBlank(episode.getInfo().getRating())) {
            episode.getInfo().setRating(info.getRating());
        }
        if (isBlank(episode.getInfo().getDuration())) {
            episode.getInfo().setDuration(info.getDuration());
        }
    }

    private boolean isParsedEpisodeCompatible(Episode parsed, Channel channel) {
        if (parsed == null || channel == null) {
            return false;
        }
        String parsedId = safe(parsed.getId());
        String cachedId = safe(channel.getChannelId());
        if (!isBlank(parsedId) && !isBlank(cachedId)) {
            return parsedId.equals(cachedId);
        }
        String parsedCmd = safe(parsed.getCmd());
        String cachedCmd = safe(channel.getCmd());
        return !isBlank(parsedCmd) && !isBlank(cachedCmd) && parsedCmd.equals(cachedCmd);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractSeason(String title) {
        if (isBlank(title)) return "1";
        String matched = firstMatch(title, SEASON_WORD_PATTERN, SEASON_SHORT_PATTERN, SEASON_X_PATTERN);
        if (!isBlank(matched)) return matched;
        return "1";
    }

    private String extractEpisode(String title) {
        if (isBlank(title)) return "";
        String matched = firstMatch(title, EPISODE_WORD_PATTERN, EPISODE_SHORT_PATTERN, EPISODE_X_PATTERN);
        if (!isBlank(matched)) return matched;
        return "";
    }

    private String firstMatch(String title, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                String value = matcher.group(1);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }
}
