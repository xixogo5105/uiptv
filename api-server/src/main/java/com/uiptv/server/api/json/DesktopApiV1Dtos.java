package com.uiptv.server.api.json;

import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.application.WatchingNowVodRow;
import com.uiptv.service.AccountResolver;
import org.json.JSONObject;

final class DesktopApiV1Dtos {
    private DesktopApiV1Dtos() {
    }

    static JSONObject category(Category category) {
        return new JSONObject()
                .put("dbId", safe(category.getDbId()))
                .put("title", safe(category.getTitle()))
                .put("alias", safe(category.getAlias()))
                .put("activeSub", category.isActiveSub());
    }

    static JSONObject channel(Channel channel) {
        return new JSONObject()
                .put("dbId", safe(channel.getDbId()))
                .put("name", safe(channel.getName()))
                .put("number", safe(channel.getNumber()))
                .put("logo", safe(channel.getLogo()))
                .put("description", safe(channel.getDescription()))
                .put("watched", channel.isWatched())
                .put("hd", channel.getHd())
                .put("status", channel.getStatus());
    }

    static JSONObject channel(Channel channel, String playbackId) {
        JSONObject json = channel(channel);
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject bookmark(Bookmark bookmark) {
        return new JSONObject()
                .put("dbId", safe(bookmark.getDbId()))
                .put("categoryId", safe(bookmark.getCategoryId()))
                .put("channelName", safe(bookmark.getChannelName()))
                .put("categoryTitle", safe(bookmark.getCategoryTitle()))
                .put("accountName", safe(bookmark.getAccountName()))
                .put("logo", safe(bookmark.getLogo()));
    }

    static JSONObject account(AccountResolver.AccountRow account) {
        return new JSONObject()
                .put("dbId", safe(account.getDbId()))
                .put("accountName", safe(account.getAccountName()))
                .put("type", safe(account.getType()))
                .put("pinToTop", account.isPinToTop())
                .put("cacheEnabled", account.isCacheEnabled())
                .put("webPlaybackProxy", account.isWebPlaybackProxy());
    }

    static JSONObject bookmarkCategory(BookmarkCategory category) {
        return new JSONObject()
                .put("id", safe(category.getId()))
                .put("name", safe(category.getName()));
    }

    static JSONObject watchingNowSeries(WatchingNowSeriesRow row) {
        return watchingNowSeries(row, "");
    }

    static JSONObject watchingNowSeries(WatchingNowSeriesRow row, String playbackId) {
        String title = safe(row.seriesTitle());
        if (title.equals(safe(row.seriesId()))) {
            title = "";
        }
        JSONObject json = new JSONObject()
                .put("accountName", safe(row.accountName()))
                .put("seriesTitle", title)
                .put("seriesPoster", safe(row.seriesPoster()))
                .put("episodeName", safe(row.episodeName()))
                .put("season", safe(row.season()))
                .put("episodeNum", row.episodeNum())
                .put("updatedAt", row.updatedAt());
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject watchingNowVod(WatchingNowVodRow row) {
        return watchingNowVod(row, "");
    }

    static JSONObject watchingNowVod(WatchingNowVodRow row, String playbackId) {
        String title = safe(row.vodName());
        if (title.equals(safe(row.vodId()))) {
            title = "";
        }
        JSONObject json = new JSONObject()
                .put("accountName", safe(row.accountName()))
                .put("vodName", title)
                .put("vodLogo", safe(row.vodLogo()))
                .put("plot", safe(row.plot()))
                .put("releaseDate", safe(row.releaseDate()))
                .put("rating", safe(row.rating()))
                .put("duration", safe(row.duration()))
                .put("updatedAt", row.updatedAt());
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject watchingNowEpisode(Channel episode, String playbackId) {
        return new JSONObject()
                .put("title", safe(episode.getName()))
                .put("season", safe(episode.getSeason()))
                .put("episodeNum", safe(episode.getEpisodeNum()))
                .put("image", safe(episode.getLogo()))
                .put("playbackId", safe(playbackId));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
