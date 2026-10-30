package com.uiptv.service;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogMode;
import com.uiptv.application.CatalogSeriesEpisodesQuery;
import com.uiptv.application.CatalogChannelsQuery;
import com.uiptv.db.AccountDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.util.I18n;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AccountCatalogCachingTest extends DbBackedTest {

    @Test
    void accountCachingDefaultsOnAndPersistsTheDisabledSetting() {
        Account enabledAccount = newAccount("cache-setting-default", AccountType.M3U8_LOCAL, "");
        AccountService.getInstance().save(enabledAccount);
        Account enabled = AccountDb.get().getAccountById(enabledAccount.getDbId());
        assertTrue(enabled.isCacheEnabled());

        Account disabledAccount = newAccount("cache-setting-disabled", AccountType.M3U8_LOCAL, "");
        disabledAccount.setCacheEnabled(false);
        AccountService.getInstance().save(disabledAccount);

        assertFalse(AccountDb.get().getAccountById(disabledAccount.getDbId()).isCacheEnabled());
    }

    @Test
    void cacheReloadSkipsDisabledAccountAndReportsFriendlySummary() throws Exception {
        Account account = newAccount("cache-refresh-skipped", AccountType.M3U8_LOCAL, "missing-playlist.m3u");
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);
        List<String> messages = new ArrayList<>();

        new CacheServiceImpl().reloadCacheWithPhases(account, (message, phase) -> messages.add(message));

        assertEquals(List.of(I18n.tr("reloadCacheDisabledSkipping")), messages);
        assertEquals(0, cacheRowCount("Category", account.getDbId()));
    }

    @Test
    void disablingCachingClearsAllAccountCatalogTablesAsynchronously() throws Exception {
        Account account = newAccount("cache-clear-transition", AccountType.M3U8_LOCAL, "");
        account.setServerPortalUrl("http://portal.example/server_portal");
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getById(account.getDbId());

        account.setAction(Account.AccountAction.itv);
        Category live = category("live-cat", "Live");
        CategoryDb.get().saveAll(List.of(live), account);
        Category storedLive = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("live-channel", "Old live title", "cmd://old-live")), storedLive.getDbId(), account);

        account.setAction(Account.AccountAction.vod);
        VodCategoryDb.get().saveAll(List.of(category("vod-cat", "Movies")), account);
        VodChannelDb.get().saveAll(List.of(channel("vod-item", "Old movie", "cmd://old-vod")), "vod-cat", account);

        account.setAction(Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(List.of(category("series-cat", "Shows")), account);
        SeriesChannelDb.get().saveAll(List.of(channel("series-item", "Old show", "cmd://old-series")), "series-cat", account);
        SeriesEpisodeDb.get().saveAll(account, "series-cat", "show-1", List.of(channel("old-episode", "Episode 1", "cmd://old-episode")));

        assertTrue(cacheRowCount("Category", account.getDbId()) > 0);
        assertTrue(cacheRowCount("Channel", null) > 0);
        assertTrue(cacheRowCount("VodCategory", account.getDbId()) > 0);
        assertTrue(cacheRowCount("VodChannel", account.getDbId()) > 0);
        assertTrue(cacheRowCount("SeriesCategory", account.getDbId()) > 0);
        assertTrue(cacheRowCount("SeriesChannel", account.getDbId()) > 0);
        assertTrue(cacheRowCount("SeriesEpisode", account.getDbId()) > 0);

        String accountId = account.getDbId();
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);

        assertFalse(AccountDb.get().getAccountById(accountId).isCacheEnabled());
        assertTrue(await(() -> {
            try {
                return cacheTablesAreEmpty(accountId);
            } catch (Exception _) {
                return false;
            }
        }, Duration.ofSeconds(5)),
                "account catalog cache should be removed on a background thread after save");
        assertEquals("http://portal.example/server_portal", AccountDb.get().getAccountById(accountId).getServerPortalUrl());
    }

    @Test
    void disabledM3uCatalogReadsCurrentCategoriesAndStreamsChannelsWithoutWritingCache() throws Exception {
        Path playlist = tempDir.resolve("current-live.m3u");
        Files.writeString(playlist, """
                #EXTM3U
                #EXTINF:-1 tvg-id="match-1" group-title="Sports",Eng vs SL live
                http://stream.example/current.m3u8
                #EXTINF:-1 tvg-id="news-1" group-title="News",World News
                http://stream.example/news.m3u8
                """);
        Account account = newAccount("cache-disabled-m3u-live", AccountType.M3U8_LOCAL, playlist.toString());
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getById(account.getDbId());
        account.setAction(Account.AccountAction.itv);

        CategoryDb.get().saveAll(List.of(category("stale-sports", "Old category")), account);
        Category oldStoredCategory = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("match-1", "Yesterday's match", "http://stream.example/old.m3u8")),
                oldStoredCategory.getDbId(), account);

        List<Category> liveCategories = CategoryService.getInstance().get(account, false);
        Category allCategory = liveCategories.stream()
                .filter(c -> CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle()))
                .findFirst().orElseThrow();
        Category sportsCategory = liveCategories.stream()
                .filter(c -> "Sports".equals(c.getTitle()))
                .findFirst().orElseThrow();
        List<List<Channel>> streamedBatches = new ArrayList<>();
        List<Channel> currentChannels = ChannelService.getInstance().get(allCategory.getTitle(), account, allCategory.getDbId(), null,
                streamedBatches::add, () -> false, progress -> { });
        List<Channel> currentSportsChannels = CatalogApplicationService.getInstance().listChannels(
                new CatalogChannelsQuery(account.getDbId(), CatalogMode.ITV, sportsCategory.getDbId(), ""));
        Channel playbackChannel = ChannelService.getInstance().getLiveChannelByChannelId(
                allCategory.getDbId(), "match-1", account);

        assertNotEquals(oldStoredCategory.getDbId(), allCategory.getDbId(), "live categories should not depend on cached category row IDs");
        assertTrue(sportsCategory.getDbId().endsWith(":Sports"), "M3U live category keys must carry the group title used for lookup");
        assertTrue(liveCategories.stream().noneMatch(c -> "Old category".equals(c.getTitle())));
        assertEquals("Eng vs SL live", currentChannels.getFirst().getName());
        assertEquals("Eng vs SL live", currentSportsChannels.getFirst().getName());
        assertEquals("Eng vs SL live", streamedBatches.getFirst().getFirst().getName());
        assertEquals("Eng vs SL live", playbackChannel.getName());
        assertEquals(1, CategoryDb.get().getCategories(account).size());
        assertEquals("Old category", CategoryDb.get().getCategories(account).getFirst().getTitle());
        assertEquals(1, cacheRowCount("Channel", null));
        assertTrue(CategoryService.getInstance().getCached(account).isEmpty());
    }

    @Test
    void disabledXtremeCatalogAndEpisodesUseProviderDataWithoutPersistingIt() throws Exception {
        Account account = newAccount("cache-disabled-xtreme", AccountType.XTREME_API, "http://provider.example/player_api.php");
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getById(account.getDbId());

        Category staleVodCategory = category("vod-cat", "Yesterday's movies");
        account.setAction(Account.AccountAction.vod);
        VodCategoryDb.get().saveAll(List.of(staleVodCategory), account);
        VodChannelDb.get().saveAll(List.of(channel("movie-1", "Yesterday's title", "cmd://old-movie")), "vod-cat", account);

        Category providerVodCategory = category("vod-cat", "Movies");
        Channel providerMovie = channel("movie-1", "Current movie", "cmd://current-movie");
        List<List<Channel>> streamedBatches = new ArrayList<>();
        try (MockedStatic<XtremeApiParser> parser = mockStatic(XtremeApiParser.class)) {
            parser.when(() -> XtremeApiParser.parseCategories(any(Account.class))).thenReturn(List.of(providerVodCategory));
            parser.when(() -> XtremeApiParser.parseChannels(eq("vod-cat"), any(Account.class), any(), any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Consumer<List<Channel>> callback = invocation.getArgument(2);
                        callback.accept(List.of(providerMovie));
                        return List.of(providerMovie);
                    });

            List<Category> vodCategories = CategoryService.getInstance().get(account, false);
            List<Channel> movies = ChannelService.getInstance().get("vod-cat", account, vodCategories.getFirst().getDbId(),
                    null, streamedBatches::add, () -> false, progress -> { });

            assertEquals("Movies", vodCategories.getFirst().getTitle());
            assertNotEquals("Yesterday's movies", vodCategories.getFirst().getTitle());
            assertEquals("Current movie", movies.getFirst().getName());
            assertEquals("Current movie", streamedBatches.getFirst().getFirst().getName());
            parser.verify(() -> XtremeApiParser.parseChannels(eq("vod-cat"), any(Account.class), any(), any()), times(1));
        }
        assertEquals("Yesterday's movies", VodCategoryDb.get().getCategories(account).getFirst().getTitle());
        assertEquals("Yesterday's title", VodChannelDb.get().getChannels(account, "vod-cat", 10, 0).getFirst().getName());

        account.setAction(Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(List.of(category("series-cat", "Old shows")), account);
        SeriesEpisodeDb.get().saveAll(account, "series-cat", "show-1", List.of(channel("old-episode", "Old episode", "cmd://old-episode")));
        Episode freshEpisode = new Episode();
        freshEpisode.setId("fresh-episode");
        freshEpisode.setTitle("Current episode");
        freshEpisode.setCmd("cmd://fresh-episode");
        EpisodeList freshEpisodes = new EpisodeList();
        freshEpisodes.setEpisodes(List.of(freshEpisode));

        try (MockedStatic<XtremeApiParser> parser = mockStatic(XtremeApiParser.class)) {
            parser.when(() -> XtremeApiParser.parseCategories(any(Account.class))).thenReturn(List.of(category("series-cat", "Shows")));
            parser.when(() -> XtremeApiParser.parseEpisodes(eq("show-1"), any(Account.class))).thenReturn(freshEpisodes);

            List<Channel> episodes = CatalogApplicationService.getInstance().listSeriesEpisodes(
                    new CatalogSeriesEpisodesQuery(account.getDbId(), "series-cat", "show-1"));

            assertEquals("Current episode", episodes.getFirst().getName());
            assertEquals("Shows", CategoryService.getInstance().get(account, false).getFirst().getTitle());
            parser.verify(() -> XtremeApiParser.parseEpisodes(eq("show-1"), any(Account.class)), times(1));
        }
        assertEquals("Old episode", SeriesEpisodeDb.get().getEpisodes(account, "series-cat", "show-1").getFirst().getName());
        assertEquals("Old shows", SeriesCategoryDb.get().getCategories(account).getFirst().getTitle());
    }

    private boolean cacheTablesAreEmpty(String accountId) throws Exception {
        return cacheRowCount("Category", accountId) == 0
                && cacheRowCount("Channel", null) == 0
                && cacheRowCount("VodCategory", accountId) == 0
                && cacheRowCount("VodChannel", accountId) == 0
                && cacheRowCount("SeriesCategory", accountId) == 0
                && cacheRowCount("SeriesChannel", accountId) == 0
                && cacheRowCount("SeriesEpisode", accountId) == 0;
    }

    private boolean await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return condition.getAsBoolean();
    }

    private int cacheRowCount(String table, String accountId) throws Exception {
        String sql = accountId == null ? "SELECT COUNT(*) FROM " + table : "SELECT COUNT(*) FROM " + table + " WHERE accountId=?";
        try (Connection connection = com.uiptv.db.SQLConnection.connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (accountId != null) statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private Account newAccount(String name, AccountType type, String source) {
        String url = type == AccountType.M3U8_LOCAL ? "http://unused" : source;
        return new Account(name, "user", "pass", url, "00:11:22:33:44:55", null, null, null, null, null,
                type, null, type == AccountType.M3U8_LOCAL ? source : url, false);
    }

    private Category category(String id, String title) {
        return new Category(id, title, title, false, 0);
    }

    private Channel channel(String id, String name, String command) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd(command);
        return channel;
    }
}
