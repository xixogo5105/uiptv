package com.uiptv.server.api.json;

import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.application.WatchingNowVodRow;
import com.uiptv.model.Channel;

import java.util.Objects;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class DesktopWatchingNowPlaybackStore {
    private static final int MAX_SELECTIONS = 2_000;
    private static final long TTL_MILLIS = 15 * 60 * 1_000L;
    private static final DesktopWatchingNowPlaybackStore INSTANCE = new DesktopWatchingNowPlaybackStore();

    private final LinkedHashMap<String, Selection> selections = new LinkedHashMap<>();

    static DesktopWatchingNowPlaybackStore getInstance() {
        return INSTANCE;
    }

    synchronized String register(WatchingNowVodRow row) {
        if (row == null || row.playItem() == null) {
            return "";
        }
        return register(new Selection(
                row.accountId(), row.categoryId(), "", "", "", "", 0,
                row.playItem(), "vod", System.currentTimeMillis() + TTL_MILLIS
        ));
    }

    synchronized String register(WatchingNowSeriesRow row) {
        if (row == null || row.episodeId() == null || row.episodeId().isBlank()) {
            return "";
        }
        return register(new Selection(
                row.accountId(), row.categoryId(), row.categoryDbId(), row.seriesId(),
                row.episodeId(), row.season(), row.episodeNum(), null, "series",
                System.currentTimeMillis() + TTL_MILLIS
        ));
    }

    synchronized Selection resolve(String id) {
        purgeExpired();
        return selections.get(id);
    }

    synchronized String registerEpisode(Selection series, Channel episode) {
        if (series == null || !"series".equals(series.mode())
                || episode == null || episode.getChannelId() == null || episode.getChannelId().isBlank()) {
            return "";
        }
        int episodeNum;
        try {
            episodeNum = Integer.parseInt(episode.getEpisodeNum());
        } catch (NumberFormatException _) {
            episodeNum = 0;
        }
        return register(new Selection(
                series.accountId(), series.categoryId(), series.categoryDbId(), series.seriesId(),
                episode.getChannelId(), safe(episode.getSeason()), episodeNum, episode, "series",
                System.currentTimeMillis() + TTL_MILLIS
        ));
    }

    private String register(Selection selection) {
        purgeExpired();
        while (selections.size() >= MAX_SELECTIONS) {
            Iterator<Map.Entry<String, Selection>> oldest = selections.entrySet().iterator();
            oldest.next();
            oldest.remove();
        }
        String id = UUID.randomUUID().toString();
        selections.put(id, selection);
        return id;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        selections.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    record Selection(
            String accountId,
            String categoryId,
            String categoryDbId,
            String seriesId,
            String episodeId,
            String season,
            int episodeNum,
            Channel channel,
            String mode,
            long expiresAt
    ) {
    }
}
