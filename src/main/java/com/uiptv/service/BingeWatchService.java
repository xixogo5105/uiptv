package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings("java:S6548")
public class BingeWatchService {
    private static final String DEFAULT_SEASON = "1";
    @SuppressWarnings("java:S1075")
    private static final String PLAYLIST_PATH = "/bingewatch.m3u8?token=";
    @SuppressWarnings("java:S1075")
    private static final String ENTRY_PATH = "/bingwatch?token=";
    private static final String EPISODE_ID_QUERY = "&episodeId=";
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private BingeWatchService() {
    }

    private static final class SingletonHelper {
        private static final BingeWatchService INSTANCE = new BingeWatchService();
    }

    public static BingeWatchService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public String createSession(Account account,
                                String seriesId,
                                String seriesCategoryId,
                                String season,
                                List<Channel> episodes,
                                SeriesWatchState watchState) {
        if (account == null || isBlank(account.getDbId()) || isBlank(seriesId) || isBlank(season) || episodes == null || episodes.isEmpty()) {
            return "";
        }

        List<SessionEpisode> seasonEpisodes = orderSeasonEpisodes(season, episodes, watchState);
        if (seasonEpisodes.isEmpty()) {
            return "";
        }

        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(
                account.getDbId(),
                safe(seriesId),
                safe(seriesCategoryId),
                safe(season),
                seasonEpisodes
        ));
        return token;
    }

    public String buildPlaylistUrl(String token) {
        if (isBlank(token)) {
            return "";
        }
        return ServerUrlUtil.getLocalServerUrl() + PLAYLIST_PATH + urlEncode(token);
    }

    public String buildPlaylistUrl(String token, String startEpisodeId) {
        if (isBlank(token)) {
            return "";
        }
        if (isBlank(startEpisodeId)) {
            return buildPlaylistUrl(token);
        }
        Session session = sessions.get(token);
        if (session == null) {
            return "";
        }
        int startIndex = 0;
        for (int i = 0; i < session.episodes().size(); i++) {
            if (startEpisodeId.equals(session.episodes().get(i).episodeId())) {
                startIndex = i;
                break;
            }
        }
        if (startIndex <= 0) {
            return buildPlaylistUrl(token);
        }

        String newToken = UUID.randomUUID().toString();
        sessions.put(newToken, new Session(
                session.accountId(),
                session.seriesId(),
                session.seriesCategoryId(),
                session.season(),
                new ArrayList<>(session.episodes().subList(startIndex, session.episodes().size()))
        ));
        return buildPlaylistUrl(newToken);
    }

    public List<PlaylistItem> getPlaylistItems(String token) {
        Session session = sessions.get(token);
        if (session == null || session.episodes() == null || session.episodes().isEmpty()) {
            return List.of();
        }
        List<PlaylistItem> items = new ArrayList<>(session.episodes().size());
        for (SessionEpisode episode : session.episodes()) {
            items.add(new PlaylistItem(
                    episode.episodeId(),
                    episode.episodeName(),
                    episode.season(),
                    episode.episodeNumber()
            ));
        }
        return items;
    }

    public String renderPlaylist(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return "";
        }
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (SessionEpisode episode : session.episodes()) {
            String title = isBlank(episode.episodeNumber())
                    ? episode.episodeName()
                    : I18n.formatEpisodeLabel(episode.episodeNumber()) + ": " + episode.episodeName();
            playlist.append("#EXTINF:-1,").append(title).append("\n");
            playlist.append(buildEntryUrl(token, episode.episodeId())).append("\n");
        }
        return playlist.toString();
    }

    public ResolvedEpisode resolveEpisode(String token, String episodeId) throws IOException {
        Session session = sessions.get(token);
        if (session == null || isBlank(episodeId)) {
            return null;
        }
        SessionEpisode episode = session.findEpisode(episodeId);
        if (episode == null) {
            return null;
        }

        Account account = AccountService.getInstance().getById(session.accountId());
        if (account == null) {
            return null;
        }
        account.setAction(Account.AccountAction.series);

        Channel channel = Channel.fromJson(episode.channelJson());
        if (channel == null) {
            return null;
        }

        SeriesWatchStateService.getInstance().markSeriesEpisodeManualIfNewer(
                account,
                session.seriesCategoryId(),
                session.seriesId(),
                episode.episodeId(),
                episode.episodeName(),
                episode.season(),
                episode.episodeNumber()
        );

        PlayerResponse response = PlayerService.getInstance().get(
                account,
                channel,
                episode.episodeId(),
                session.seriesId(),
                session.seriesCategoryId()
        );
        if (response == null || isBlank(response.getUrl())) {
            return null;
        }
        return new ResolvedEpisode(response.getUrl(), episode.episodeName());
    }

    List<SessionEpisode> orderSeasonEpisodes(String season, List<Channel> episodes, SeriesWatchState watchState) {
        String normalizedSeason = normalizeNumber(season);
        List<SessionEpisode> ordered = new ArrayList<>();
        if (isBlank(normalizedSeason) || episodes == null) {
            return ordered;
        }

        for (Channel episode : episodes) {
            if (episode != null) {
                String episodeSeason = normalizeNumber(firstNonBlank(episode.getSeason(), DEFAULT_SEASON));
                String episodeId = safe(episode.getChannelId());
                if (normalizedSeason.equals(episodeSeason) && !isBlank(episodeId)) {
                    ordered.add(new SessionEpisode(
                            episodeId,
                            safe(episode.getName()),
                            episodeSeason,
                            normalizeNumber(episode.getEpisodeNum()),
                            episode.toJson()
                    ));
                }
            }
        }

        ordered.sort(Comparator
                .comparingInt((SessionEpisode item) -> parseNumberOrDefault(item.season(), 1))
                .thenComparingInt(item -> parseNumberOrDefault(item.episodeNumber(), Integer.MAX_VALUE))
                .thenComparing(SessionEpisode::episodeName, String.CASE_INSENSITIVE_ORDER));

        int startIndex = resolveStartIndex(ordered, normalizedSeason, watchState);
        if (startIndex <= 0) {
            return ordered;
        }
        return new ArrayList<>(ordered.subList(startIndex, ordered.size()));
    }

    private int resolveStartIndex(List<SessionEpisode> ordered, String season, SeriesWatchState watchState) {
        if (ordered.isEmpty() || watchState == null) {
            return 0;
        }
        if (!season.equals(normalizeNumber(watchState.getSeason()))) {
            return 0;
        }

        String watchedEpisodeId = safe(watchState.getEpisodeId());
        String watchedEpisodeNumber = watchState.getEpisodeNum() > 0 ? String.valueOf(watchState.getEpisodeNum()) : "";
        for (int i = 0; i < ordered.size(); i++) {
            SessionEpisode item = ordered.get(i);
            if (!isBlank(watchedEpisodeId) && watchedEpisodeId.equals(item.episodeId())) {
                return i;
            }
            if (!isBlank(watchedEpisodeNumber) && watchedEpisodeNumber.equals(item.episodeNumber())) {
                return i;
            }
        }
        return 0;
    }

    private String buildEntryUrl(String token, String episodeId) {
        return ServerUrlUtil.getLocalServerUrl()
                + ENTRY_PATH + urlEncode(token)
                + EPISODE_ID_QUERY + urlEncode(episodeId);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private static String normalizeNumber(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        return String.valueOf(Integer.parseInt(digits));
    }

    private static int parseNumberOrDefault(String raw, int fallback) {
        String normalized = normalizeNumber(raw);
        if (normalized.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return isBlank(first) ? safe(fallback) : first.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record Session(String accountId,
                           String seriesId,
                           String seriesCategoryId,
                           String season,
                           List<SessionEpisode> episodes) {
        private SessionEpisode findEpisode(String episodeId) {
            for (SessionEpisode episode : episodes) {
                if (episode.episodeId().equals(episodeId)) {
                    return episode;
                }
            }
            return null;
        }
    }

    record SessionEpisode(String episodeId, String episodeName, String season, String episodeNumber, String channelJson) {
    }

    public record PlaylistItem(String episodeId, String episodeName, String season, String episodeNumber) {
    }

    public record ResolvedEpisode(String url, String episodeName) {
    }
}
