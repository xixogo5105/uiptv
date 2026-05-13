package com.uiptv.service;

import com.uiptv.db.SeriesWatchingNowSnapshotDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchingNowSnapshot;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings("java:S6548")
public class SeriesWatchingNowSnapshotService {
    private SeriesWatchingNowSnapshotService() {
    }

    private static class SingletonHelper {
        private static final SeriesWatchingNowSnapshotService INSTANCE = new SeriesWatchingNowSnapshotService();
    }

    public static SeriesWatchingNowSnapshotService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public SeriesWatchingNowSnapshot getSnapshot(String accountId, String categoryId, String seriesId) {
        if (isBlank(accountId) || isBlank(seriesId)) {
            return null;
        }
        String normalizedCategory = normalize(categoryId);
        List<String> candidateIds = buildSeriesIdCandidates(seriesId);
        SeriesWatchingNowSnapshot exact = findExactSnapshot(accountId, normalizedCategory, candidateIds);
        return exact != null ? exact : findLatestSnapshot(accountId, candidateIds);
    }

    public EpisodeList loadEpisodeList(String accountId, String categoryId, String seriesId) {
        SeriesWatchingNowSnapshot snapshot = getSnapshot(accountId, categoryId, seriesId);
        if (snapshot == null || isBlank(snapshot.getEpisodesJson())) {
            return new EpisodeList();
        }
        JSONArray payload;
        try {
            payload = new JSONArray(snapshot.getEpisodesJson());
        } catch (Exception _) {
            return new EpisodeList();
        }
        EpisodeList list = new EpisodeList();
        for (int i = 0; i < payload.length(); i++) {
            Channel channel = readChannel(payload, i);
            if (channel != null) {
                list.getEpisodes().add(toEpisode(channel));
            }
        }
        return list;
    }

    public List<Channel> loadChannels(String accountId, String categoryId, String seriesId) {
        SeriesWatchingNowSnapshot snapshot = getSnapshot(accountId, categoryId, seriesId);
        if (snapshot == null || isBlank(snapshot.getEpisodesJson())) {
            return List.of();
        }
        JSONArray payload;
        try {
            payload = new JSONArray(snapshot.getEpisodesJson());
        } catch (Exception _) {
            return List.of();
        }
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < payload.length(); i++) {
            Channel channel = readChannel(payload, i);
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    public void save(Account account,
                     String categoryId,
                     String seriesId,
                     String categoryDbId,
                     String seriesTitle,
                     String seriesPoster,
                     EpisodeList episodeList) {
        if (account == null || isBlank(account.getDbId()) || isBlank(seriesId) || episodeList == null
                || episodeList.getEpisodes() == null || episodeList.getEpisodes().isEmpty()) {
            return;
        }
        JSONArray episodesPayload = new JSONArray();
        for (Episode episode : episodeList.getEpisodes()) {
            Channel channel = toChannel(episode);
            if (channel != null) {
                episodesPayload.put(channel.toJson());
            }
        }
        if (episodesPayload.isEmpty()) {
            return;
        }
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(account.getDbId());
        snapshot.setCategoryId(normalize(categoryId));
        snapshot.setSeriesId(canonicalizeSeriesId(seriesId));
        snapshot.setCategoryDbId(normalize(categoryDbId));
        snapshot.setSeriesTitle(normalize(seriesTitle));
        snapshot.setSeriesPoster(normalize(seriesPoster));
        snapshot.setEpisodesJson(episodesPayload.toString());
        snapshot.setUpdatedAt(System.currentTimeMillis());
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot);
    }

    public void saveChannels(Account account,
                             String categoryId,
                             String seriesId,
                             String categoryDbId,
                             String seriesTitle,
                             String seriesPoster,
                             List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        EpisodeList list = new EpisodeList();
        for (Channel channel : channels) {
            if (channel != null) {
                list.getEpisodes().add(toEpisode(channel));
            }
        }
        save(account, categoryId, seriesId, categoryDbId, seriesTitle, seriesPoster, list);
    }

    public void clear(String accountId, String categoryId, String seriesId) {
        if (isBlank(accountId) || isBlank(seriesId)) {
            return;
        }
        String canonicalSeriesId = canonicalizeSeriesId(seriesId);
        String normalizedCategory = normalize(categoryId);
        SeriesWatchingNowSnapshotDb.get().clear(accountId, normalizedCategory, canonicalSeriesId);
        for (String candidateId : buildSeriesIdCandidates(seriesId)) {
            for (SeriesWatchingNowSnapshot candidate : SeriesWatchingNowSnapshotDb.get().getBySeries(accountId, candidateId)) {
                if (candidate != null) {
                    SeriesWatchingNowSnapshotDb.get().clear(accountId, normalize(candidate.getCategoryId()), normalize(candidate.getSeriesId()));
                }
            }
        }
    }

    public void clearAll() {
        SeriesWatchingNowSnapshotDb.get().clearAll();
    }

    private Channel toChannel(Episode episode) {
        if (episode == null || isBlank(episode.getId())) {
            return null;
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
        return channel;
    }

    private Episode toEpisode(Channel channel) {
        Episode parsed = Episode.fromJson(channel.getExtraJson());
        if (parsed != null && !isBlank(parsed.getId())) {
            hydrateParsedEpisode(parsed, channel);
            return parsed;
        }
        Episode episode = new Episode();
        episode.setId(channel.getChannelId());
        episode.setTitle(channel.getName());
        episode.setCmd(channel.getCmd());
        episode.setSeason(channel.getSeason());
        episode.setEpisodeNum(channel.getEpisodeNum());
        com.uiptv.shared.EpisodeInfo info = new com.uiptv.shared.EpisodeInfo();
        info.setMovieImage(channel.getLogo());
        info.setPlot(channel.getDescription());
        info.setReleaseDate(channel.getReleaseDate());
        info.setRating(channel.getRating());
        info.setDuration(channel.getDuration());
        episode.setInfo(info);
        return episode;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private SeriesWatchingNowSnapshot findExactSnapshot(String accountId, String normalizedCategory, List<String> candidateIds) {
        for (String candidateId : candidateIds) {
            SeriesWatchingNowSnapshot match = SeriesWatchingNowSnapshotDb.get().getBySeries(accountId, normalizedCategory, candidateId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private SeriesWatchingNowSnapshot findLatestSnapshot(String accountId, List<String> candidateIds) {
        SeriesWatchingNowSnapshot latest = null;
        for (String candidateId : candidateIds) {
            latest = newerSnapshot(latest, SeriesWatchingNowSnapshotDb.get().getBySeries(accountId, candidateId));
        }
        return latest;
    }

    private SeriesWatchingNowSnapshot newerSnapshot(SeriesWatchingNowSnapshot current, List<SeriesWatchingNowSnapshot> candidates) {
        SeriesWatchingNowSnapshot latest = current;
        for (SeriesWatchingNowSnapshot candidate : candidates) {
            if (candidate != null && (latest == null || candidate.getUpdatedAt() > latest.getUpdatedAt())) {
                latest = candidate;
            }
        }
        return latest;
    }

    private Channel readChannel(JSONArray payload, int index) {
        Object value = payload.opt(index);
        return value == null ? null : Channel.fromJson(String.valueOf(value));
    }

    private void hydrateParsedEpisode(Episode parsed, Channel channel) {
        if (isBlank(parsed.getSeason())) {
            parsed.setSeason(channel.getSeason());
        }
        if (isBlank(parsed.getEpisodeNum())) {
            parsed.setEpisodeNum(channel.getEpisodeNum());
        }
        if (parsed.getInfo() == null) {
            parsed.setInfo(new com.uiptv.shared.EpisodeInfo());
        }
        mergeEpisodeInfo(parsed.getInfo(), channel);
    }

    private void mergeEpisodeInfo(com.uiptv.shared.EpisodeInfo info, Channel channel) {
        if (isBlank(info.getMovieImage())) {
            info.setMovieImage(channel.getLogo());
        }
        if (isBlank(info.getPlot())) {
            info.setPlot(channel.getDescription());
        }
        if (isBlank(info.getReleaseDate())) {
            info.setReleaseDate(channel.getReleaseDate());
        }
        if (isBlank(info.getRating())) {
            info.setRating(channel.getRating());
        }
        if (isBlank(info.getDuration())) {
            info.setDuration(channel.getDuration());
        }
    }

    private String canonicalizeSeriesId(String seriesId) {
        String raw = normalize(seriesId);
        if (isBlank(raw) || !raw.contains(":")) {
            return raw;
        }
        String[] parts = raw.split(":");
        String last = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = normalize(parts[i]);
            if (!isBlank(part)) {
                last = part;
                break;
            }
        }
        return isBlank(last) ? raw : last;
    }

    private List<String> buildSeriesIdCandidates(String seriesId) {
        String raw = normalize(seriesId);
        if (isBlank(raw)) {
            return List.of();
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(canonicalizeSeriesId(raw));
        if (raw.contains(":")) {
            for (String part : raw.split(":")) {
                String normalized = normalize(part);
                if (!isBlank(normalized)) {
                    candidates.add(normalized);
                }
            }
        } else {
            candidates.add(raw + ":" + raw);
        }
        candidates.add(raw);
        return new ArrayList<>(candidates);
    }
}
