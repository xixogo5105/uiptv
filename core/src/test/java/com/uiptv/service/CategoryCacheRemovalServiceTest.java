package com.uiptv.service;

import com.uiptv.db.AccountDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SQLConnection;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryCacheRemovalServiceTest extends DbBackedTest {

    @Test
    void removeCachedCategories_removesLiveCategoriesAndChannelsOnly() throws Exception {
        Account account = persistAccount("remove-live", itv);
        CategoryDb.get().saveAll(List.of(
                new Category("live-news", "News", "news", false, 0),
                new Category("live-sports", "Sports", "sports", false, 0)
        ), account);
        List<Category> categories = CategoryDb.get().getCategories(account);
        Category news = categories.get(0);
        Category sports = categories.get(1);
        ChannelDb.get().saveAll(List.of(channel("news-1", "News 1"), channel("news-2", "News 2")), news.getDbId(), account);
        ChannelDb.get().saveAll(List.of(channel("sports-1", "Sports 1")), sports.getDbId(), account);
        insertBookmark(account, "live-news", "news-1", itv);

        CategoryCacheRemovalResult result = CategoryCacheRemovalService.getInstance()
                .removeCachedCategories(account, List.of(news.getDbId(), "all", news.getDbId()));

        assertEquals(1, result.requestedCategoryCount());
        assertEquals(1, result.removedCategoryCount());
        assertEquals(2, result.removedItemCount());
        assertEquals(1, countRows("Category", "accountId=? AND accountType=?", account.getDbId(), itv.name()));
        assertEquals(1, countRows("Channel", "categoryId=?", sports.getDbId()));
        assertEquals(0, countRows("Channel", "categoryId=?", news.getDbId()));
        assertEquals(1, countRows("Bookmark", "accountName=?", account.getAccountName()));
    }

    @Test
    void removeCachedCategories_removesVodCacheButKeepsWatchingNowState() throws Exception {
        Account account = persistAccount("remove-vod", vod);
        VodCategoryDb.get().saveAll(List.of(
                new Category("vod-action", "Action", "action", false, 0),
                new Category("vod-drama", "Drama", "drama", false, 0)
        ), account);
        Category action = VodCategoryDb.get().getCategories(account).get(0);
        VodChannelDb.get().saveAll(List.of(channel("vod-1", "Movie 1"), channel("vod-2", "Movie 2")), "vod-action", account);
        VodChannelDb.get().saveAll(List.of(channel("vod-3", "Movie 3")), "vod-drama", account);
        insertVodWatchState(account, "vod-action", "vod-1");

        CategoryCacheRemovalResult result = CategoryCacheRemovalService.getInstance()
                .removeCachedCategories(account, List.of(action.getDbId()));

        assertEquals(1, result.requestedCategoryCount());
        assertEquals(1, result.removedCategoryCount());
        assertEquals(2, result.removedItemCount());
        assertEquals(1, countRows("VodCategory", "accountId=?", account.getDbId()));
        assertEquals(0, countRows("VodChannel", "accountId=? AND categoryId=?", account.getDbId(), "vod-action"));
        assertEquals(1, countRows("VodChannel", "accountId=? AND categoryId=?", account.getDbId(), "vod-drama"));
        assertEquals(1, countRows("VodWatchState", "accountId=? AND categoryId=?", account.getDbId(), "vod-action"));
    }

    @Test
    void removeCachedCategories_removesSeriesCacheAndEpisodesButKeepsWatchingNowState() throws Exception {
        Account account = persistAccount("remove-series", series);
        SeriesCategoryDb.get().saveAll(List.of(
                new Category("series-comedy", "Comedy", "comedy", false, 0),
                new Category("series-crime", "Crime", "crime", false, 0)
        ), account);
        Category comedy = SeriesCategoryDb.get().getCategories(account).get(0);
        SeriesChannelDb.get().saveAll(List.of(channel("series-1", "Series 1")), "series-comedy", account);
        SeriesChannelDb.get().saveAll(List.of(channel("series-2", "Series 2")), "series-crime", account);
        SeriesEpisodeDb.get().saveAll(account, "series-comedy", "series-1", List.of(episode("episode-1"), episode("episode-2")));
        SeriesEpisodeDb.get().saveAll(account, "series-crime", "series-2", List.of(episode("episode-3")));
        insertSeriesWatchState(account, "series-comedy", "series-1");
        insertSeriesWatchingNowSnapshot(account, "series-comedy", "series-1");

        CategoryCacheRemovalResult result = CategoryCacheRemovalService.getInstance()
                .removeCachedCategories(account, List.of(comedy.getDbId()));

        assertEquals(1, result.requestedCategoryCount());
        assertEquals(1, result.removedCategoryCount());
        assertEquals(3, result.removedItemCount());
        assertEquals(1, countRows("SeriesCategory", "accountId=?", account.getDbId()));
        assertEquals(0, countRows("SeriesChannel", "accountId=? AND categoryId=?", account.getDbId(), "series-comedy"));
        assertEquals(1, countRows("SeriesChannel", "accountId=? AND categoryId=?", account.getDbId(), "series-crime"));
        assertEquals(0, countRows("SeriesEpisode", "accountId=? AND categoryId=?", account.getDbId(), "series-comedy"));
        assertEquals(1, countRows("SeriesEpisode", "accountId=? AND categoryId=?", account.getDbId(), "series-crime"));
        assertEquals(1, countRows("SeriesWatchState", "accountId=? AND categoryId=?", account.getDbId(), "series-comedy"));
        assertEquals(1, countRows("SeriesWatchingNowSnapshot", "accountId=? AND categoryId=?", account.getDbId(), "series-comedy"));
    }

    private Account persistAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.XTREME_API, null, null, false);
        account.setAction(action);
        AccountDb.get().save(account);
        Account saved = AccountDb.get().getAccountByName(name);
        saved.setAction(action);
        return saved;
    }

    private Channel channel(String id, String name) {
        return new Channel(id, name, "", "cmd-" + id, null, null, null, "", 0, 1, 0,
                null, null, null, null, null);
    }

    private Channel episode(String id) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName("Episode " + id);
        channel.setCmd("cmd-" + id);
        return channel;
    }

    private void insertBookmark(Account account, String categoryId, String channelId, Account.AccountAction action) throws Exception {
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement("""
                     INSERT INTO Bookmark (accountName, categoryTitle, channelId, channelName, cmd, categoryId, accountAction)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, account.getAccountName());
            statement.setString(2, "Bookmark Category");
            statement.setString(3, channelId);
            statement.setString(4, "Bookmark Channel");
            statement.setString(5, "cmd");
            statement.setString(6, categoryId);
            statement.setString(7, action.name());
            statement.executeUpdate();
        }
    }

    private void insertVodWatchState(Account account, String categoryId, String vodId) throws Exception {
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement("""
                     INSERT INTO VodWatchState (accountId, categoryId, vodId, vodName, vodCmd, vodLogo, updatedAt)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, categoryId);
            statement.setString(3, vodId);
            statement.setString(4, "Movie");
            statement.setString(5, "cmd");
            statement.setString(6, "");
            statement.setLong(7, 1L);
            statement.executeUpdate();
        }
    }

    private void insertSeriesWatchState(Account account, String categoryId, String seriesId) throws Exception {
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement("""
                     INSERT INTO SeriesWatchState (accountId, mode, categoryId, seriesId, episodeId, episodeName, season, episodeNum, updatedAt, source)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, "series");
            statement.setString(3, categoryId);
            statement.setString(4, seriesId);
            statement.setString(5, "episode-1");
            statement.setString(6, "Episode 1");
            statement.setString(7, "1");
            statement.setInt(8, 1);
            statement.setLong(9, 1L);
            statement.setString(10, "test");
            statement.executeUpdate();
        }
    }

    private void insertSeriesWatchingNowSnapshot(Account account, String categoryId, String seriesId) throws Exception {
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement("""
                     INSERT INTO SeriesWatchingNowSnapshot (accountId, categoryId, seriesId, categoryDbId, seriesTitle, seriesPoster, episodesJson, updatedAt)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, categoryId);
            statement.setString(3, seriesId);
            statement.setString(4, "1");
            statement.setString(5, "Series");
            statement.setString(6, "");
            statement.setString(7, "[]");
            statement.setLong(8, 1L);
            statement.executeUpdate();
        }
    }

    private int countRows(String table, String where, String... args) throws Exception {
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            for (int i = 0; i < args.length; i++) {
                statement.setString(i + 1, args[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
