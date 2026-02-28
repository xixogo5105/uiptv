package com.uiptv.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uiptv.db.*;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.LogUtil;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.util.TextParserService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.*;

class EndToEndIntegrationFlowTest extends DbBackedTest {

    private HttpServer mockServer;
    private String baseUrl;
    private String stalkerPortalUrl;
    private String xtremeBaseUrl;
    private String rssFeedUrl;

    @BeforeEach
    void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext("/portal.php", this::handleStalker);
        mockServer.createContext("/xtreme/player_api.php", this::handleXtreme);
        mockServer.createContext("/rss/feed.xml", this::handleRss);
        mockServer.createContext("/m3u", this::handleM3uPlaylist);
        mockServer.start();

        int port = mockServer.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        stalkerPortalUrl = baseUrl + "/portal.php";
        xtremeBaseUrl = baseUrl + "/xtreme/";
        rssFeedUrl = baseUrl + "/rss/feed.xml";
    }

    @AfterEach
    void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    /*
     * End-to-end integration flow covered by this test:
     *
     * 1) Configuration service lifecycle
     *    - Create configuration with explicit values.
     *    - Update existing configuration values.
     *    - Assert persisted fields after update.
     *
     * 2) Account creation baseline (one of each type)
     *    - Create 1 Stalker account.
     *    - Create 1 Xtreme account.
     *    - Create 1 M3U account.
     *    - Create 1 RSS account.
     *    - Assert counts == 1 for each account type bucket.
     *
     * 3) Parser/import expansion to full sets
     *    - Import Stalker accounts through TextParserService MODE_STALKER.
     *    - Import Xtreme accounts through TextParserService MODE_XTREME.
     *    - Import M3U accounts through TextParserService MODE_M3U.
     *    - Add RSS accounts to reach same final cardinality.
     *    - Assert counts == 3 per type bucket.
     *
     * 4) Account update assertions
     *    - Update timezone/http method and provider URLs as applicable.
     *    - Re-save all accounts and re-read representative accounts.
     *    - Assert updates are persisted.
     *
     * 5) Cache population across all account sets
     *    Live (all account types):
     *    - Reload live cache and assert categories/channels persisted in DB.
     *    - Assert at least 2 channels for live flows where expected.
     *
     *    Stalker fallback scenario (critical cache refresh branch):
     *    - For one specific Stalker account (MAC 00:11:22:33:44:53), mock
     *      get_all_channels returns empty payload.
     *    - Service must fallback to category-by-category ordered-list fetch.
     *    - Assert fallback log markers are present:
     *      "Trying last-resort category-by-category fetch"
     *      "Last-resort fetch succeeded"
     *    - Assert fallback still caches live categories/channels in DB.
     *
     *    VOD/Series (Stalker + Xtreme):
     *    - Fetch VOD categories and channels for at least two categories.
     *    - Fetch Series categories and series channels for at least two categories.
     *    - For Stalker series payloads, assert episode-expanded naming includes Episode 5.
     *    - For Xtreme, call parseEpisodes on a cached series and assert 5 episodes.
     *    - Assert VOD/Series DB tables contain expected cached rows.
     *
     *    DRM checks (M3U):
     *    - Use M3U playlist containing DRM metadata.
     *    - Assert at least one cached channel is DRM-protected.
     *    - Assert persisted DRM attributes (type/keys presence).
     *
     * 6) Play service scenarios
     *    - M3U DRM channel playback response path + DRM browser launch URL.
     *    - Stalker live create_link path where provider returns missing query values:
     *      assert merged stream/play_token in resulting playback URL.
     *    - Xtreme predefined URL playback path assertion.
     *    - Bookmark flow including play-from-bookmark assertion.
     *
     * 6.2) DB synchronize flow
     *    - Create two isolated sqlite databases.
     *    - Seed syncable tables with different rows in each database.
     *    - Execute RootApplication.syncDatabases (same logic as CLI sync mode).
     *    - Assert both databases converge for all syncable tables.
     *
     * 7) Account-level cache clear and partial deletion
     *    - Select one account from each type bucket.
     *    - clearCache(account) for each selected account.
     *    - Assert all cache tables cleared for that account:
     *      Category/Channel/VodCategory/VodChannel/SeriesCategory/SeriesChannel.
     *    - Delete selected accounts and assert they are removed.
     *    - Assert remaining account counts == 2 per type bucket.
     *
     * 8) Global cache clear and full deletion
     *    - clearAllCache().
     *    - Assert all cache tables are globally empty.
     *    - Delete all remaining accounts.
     *    - Assert account store is empty and cache tables remain empty.
     *
     * Test infrastructure notes:
     * - Uses an embedded mock HttpServer to emulate:
     *   Stalker portal endpoints, Xtreme player_api.php endpoints, RSS feed, M3U files.
     * - Uses DbBackedTest temp SQLite path for isolated DB per test run.
     * - Mocks LogUtil.httpLog to avoid JavaFX LogDisplayUI initialization in headless CI.
     */
    void endToEnd_allAccountTypes_configurationParseCachePlayAndClear() throws Exception {
        ConfigurationService configurationService = ConfigurationService.getInstance();
        AccountService accountService = AccountService.getInstance();
        CacheService cacheService = new CacheServiceImpl();
        BookmarkService bookmarkService = BookmarkService.getInstance();

        try (MockedStatic<LogUtil> logUtilMock = Mockito.mockStatic(LogUtil.class)) {
            logUtilMock.when(() -> LogUtil.httpLog(Mockito.anyString(), Mockito.any(), Mockito.anyMap())).thenAnswer(invocation -> null);
            assertConfigurationLifecycle(configurationService);
            seedAndImportAccounts();
            updateAccountsAndAssert(accountService);
            cacheAllAccountTypes(cacheService);
            assertPlayAndBookmarkScenarios(accountService, bookmarkService);
            runDbSyncFlow();
            clearCacheAndDeleteOnePerType(cacheService, accountService);
            clearAllCacheAndDeleteRemaining(cacheService, accountService);
        }
    }

    private void assertConfigurationLifecycle(ConfigurationService configurationService) {
        Configuration firstConfig = new Configuration(
                "player-a",
                "player-b",
                "player-c",
                "player-a",
                "skip-me",
                "skip-channel",
                false,
                "JetBrains Mono",
                "13",
                "Medium",
                false,
                "9999",
                false,
                false
        );
        configurationService.save(firstConfig);

        Configuration updated = configurationService.read();
        updated.setServerPort("10001");
        updated.setDarkTheme(true);
        updated.setEnableFfmpegTranscoding(true);
        configurationService.save(updated);

        Configuration persisted = configurationService.read();
        assertEquals("10001", persisted.getServerPort());
        assertTrue(persisted.isDarkTheme());
        assertTrue(persisted.isEnableFfmpegTranscoding());
    }

    private void seedAndImportAccounts() throws Exception {
        String drmLocalPlaylist = writeM3uPlaylist("m3u-drm-local.m3u", true);
        saveStalker("stalker-seed-1", "00:11:22:33:44:51");
        saveXtreme("xtreme-seed-1", "xtuser1", "xtpass1");
        saveM3uLocal("m3u-seed-1", drmLocalPlaylist);
        saveRss("rss-seed-1", rssFeedUrl);
        assertTypeCounts(1, 1, 1, 1);

        TextParserService.saveBulkAccounts(stalkerBulkText(), TextParserService.MODE_STALKER, false, false);
        TextParserService.saveBulkAccounts(xtremeBulkText(), TextParserService.MODE_XTREME, false, false);
        TextParserService.saveBulkAccounts(m3uBulkText(), TextParserService.MODE_M3U, false, false);
        saveRss("rss-seed-2", rssFeedUrl + "?r=2");
        saveRss("rss-seed-3", rssFeedUrl + "?r=3");
        assertTypeCounts(3, 3, 3, 3);
    }

    private void updateAccountsAndAssert(AccountService accountService) {
        List<Account> allAccounts = new ArrayList<>(accountService.getAll().values());
        for (Account account : allAccounts) {
            account.setTimezone("America/New_York");
            account.setHttpMethod("GET");
            if (account.getType() == AccountType.STALKER_PORTAL) {
                account.setServerPortalUrl(stalkerPortalUrl);
                account.setUrl(stalkerPortalUrl);
            }
            if (account.getType() == AccountType.XTREME_API) {
                account.setM3u8Path(xtremeBaseUrl);
                account.setUrl(xtremeBaseUrl);
            }
            if (account.getType() == AccountType.RSS_FEED) {
                account.setM3u8Path(rssFeedUrl);
                account.setUrl(rssFeedUrl);
            }
            accountService.save(account);
        }

        Account updatedStalker = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        Account updatedXtreme = getAccountsByType(AccountType.XTREME_API).get(0);
        Account updatedM3u = getM3uAccounts().get(0);
        Account updatedRss = getAccountsByType(AccountType.RSS_FEED).get(0);
        assertEquals("America/New_York", updatedStalker.getTimezone());
        assertEquals(stalkerPortalUrl, updatedStalker.getServerPortalUrl());
        assertTrue(updatedXtreme.getM3u8Path().startsWith(xtremeBaseUrl));
        assertNotNull(updatedM3u.getM3u8Path());
        assertTrue(updatedRss.getM3u8Path().contains("/rss/feed.xml"));
    }

    private void cacheAllAccountTypes(CacheService cacheService) throws IOException {
        for (Account stalker : getAccountsByType(AccountType.STALKER_PORTAL)) {
            if ("00:11:22:33:44:53".equalsIgnoreCase(stalker.getMacAddress())) {
                cacheLiveWithFallbackAssertion(stalker, cacheService);
            } else {
                cacheLive(stalker, cacheService);
            }
            cacheVod(stalker);
            cacheSeries(stalker);
        }

        for (Account xtreme : getAccountsByType(AccountType.XTREME_API)) {
            cacheLive(xtreme, cacheService);
            cacheVod(xtreme);
            cacheSeries(xtreme);
            assertXtremeEpisodes(xtreme);
        }

        for (Account m3u : getM3uAccounts()) {
            cacheLive(m3u, cacheService);
        }
        assertM3uUncategorizedAllCategoryCoverage();

        for (Account rss : getAccountsByType(AccountType.RSS_FEED)) {
            cacheLive(rss, cacheService);
        }

        assertCategoryAndChannelCacheHitPaths();
        assertStalkerPageIndexFallback();
        assertChannelFetchCancellationFlow();
    }

    private void assertXtremeEpisodes(Account xtreme) throws IOException {
        xtreme.setAction(series);
        List<Category> seriesCategories = SeriesCategoryDb.get().getCategories(xtreme);
        assertFalse(seriesCategories.isEmpty());
        List<Channel> seriesChannels = SeriesChannelDb.get().getChannels(xtreme, seriesCategories.get(0).getDbId());
        assertFalse(seriesChannels.isEmpty());
        EpisodeList episodes = XtremeParser.parseEpisodes(seriesChannels.get(0).getChannelId(), xtreme);
        assertEquals(5, episodes.getEpisodes().size());
    }

    private void assertPlayAndBookmarkScenarios(AccountService accountService, BookmarkService bookmarkService) throws Exception {
        Account drmAccount = accountService.getByName("m3u-seed-1");
        drmAccount.setAction(itv);
        List<Channel> m3uChannels = allLiveChannels(drmAccount);
        Channel drmChannel = m3uChannels.stream().filter(ch -> PlayerService.getInstance().isDrmProtected(ch)).findFirst().orElse(null);
        assertNotNull(drmChannel, "Expected at least one DRM-protected M3U channel");
        assertEquals("org.w3.clearkey", drmChannel.getDrmType());
        assertNotNull(drmChannel.getClearKeysJson());

        PlayerResponse drmPlay = PlayerService.getInstance().get(drmAccount, drmChannel);
        assertNotNull(drmPlay.getUrl());
        assertTrue(PlayerService.getInstance().buildDrmBrowserPlaybackUrl(drmAccount, drmChannel, "cat", "itv").contains("drmLaunch="));

        Account stalkerPlayAccount = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        stalkerPlayAccount.setAction(itv);
        Channel stalkerLiveChannel = allLiveChannels(stalkerPlayAccount).get(0);
        PlayerResponse stalkerPlay = PlayerService.getInstance().get(stalkerPlayAccount, stalkerLiveChannel);
        assertTrue(stalkerPlay.getUrl().contains("stream=1001") || stalkerPlay.getUrl().contains("stream=2001"));
        assertTrue(stalkerPlay.getUrl().contains("play_token="));

        Account xtremePlayAccount = getAccountsByType(AccountType.XTREME_API).get(0);
        xtremePlayAccount.setAction(itv);
        Channel xtremeChannel = allLiveChannels(xtremePlayAccount).get(0);
        PlayerResponse xtremePlay = PlayerService.getInstance().get(xtremePlayAccount, xtremeChannel);
        assertTrue(xtremePlay.getUrl().contains("/xtreme/"));

        bookmarkService.addCategory(new BookmarkCategory(null, "E2E Favorites"));
        BookmarkCategory bookmarkCategory = bookmarkService.getAllCategories().stream()
                .filter(c -> "E2E Favorites".equals(c.getName()))
                .findFirst()
                .orElseThrow();

        Bookmark playBookmark = new Bookmark(
                stalkerPlayAccount.getAccountName(),
                "Sports",
                stalkerLiveChannel.getChannelId(),
                stalkerLiveChannel.getName(),
                stalkerLiveChannel.getCmd(),
                stalkerPlayAccount.getServerPortalUrl(),
                "cat-e2e"
        );
        playBookmark.setAccountAction(itv);
        playBookmark.setFromChannel(stalkerLiveChannel);
        playBookmark.setChannelJson(stalkerLiveChannel.toJson());
        bookmarkService.save(playBookmark);
        assertTrue(bookmarkService.isChannelBookmarked(playBookmark));
        assertTrue(bookmarkService.readToJson().contains(stalkerLiveChannel.getName()));

        assertBookmarkLogoEnrichment(stalkerPlayAccount, stalkerLiveChannel);

        Bookmark persistedBookmark = bookmarkService.getBookmark(playBookmark);
        assertNotNull(persistedBookmark);
        PlayerResponse bookmarkPlay = PlayerService.getInstance().get(
                stalkerPlayAccount,
                Channel.fromJson(persistedBookmark.getChannelJson()),
                persistedBookmark.getChannelId()
        );
        assertNotNull(bookmarkPlay.getUrl());
        assertTrue(bookmarkPlay.getUrl().contains("play_token="));

        assertSeriesWatchPointerProgressionThroughPlayerCallback(accountService);
        assertSeriesEpisodeBookmarkPlayFlow();

        Bookmark secondBookmark = new Bookmark(
                xtremePlayAccount.getAccountName(),
                "X Live",
                xtremeChannel.getChannelId(),
                xtremeChannel.getName(),
                xtremeChannel.getCmd(),
                xtremePlayAccount.getServerPortalUrl(),
                "cat-e2e"
        );
        secondBookmark.setAccountAction(itv);
        bookmarkService.save(secondBookmark);
        List<Bookmark> beforeOrder = bookmarkService.getBookmarksByCategory("cat-e2e");
        assertEquals(2, beforeOrder.size());
        bookmarkService.saveBookmarkOrder("cat-e2e", List.of(beforeOrder.get(1).getDbId(), beforeOrder.get(0).getDbId()));
        List<Bookmark> afterOrder = bookmarkService.getBookmarksByCategory("cat-e2e");
        assertEquals(2, afterOrder.size());
        bookmarkService.toggleBookmark(secondBookmark);
        assertFalse(bookmarkService.isChannelBookmarked(secondBookmark));
        bookmarkService.removeCategory(bookmarkCategory);
    }

    private void clearCacheAndDeleteOnePerType(CacheService cacheService, AccountService accountService) {
        List<Account> removeSet = List.of(
                getAccountsByType(AccountType.STALKER_PORTAL).get(0),
                getAccountsByType(AccountType.XTREME_API).get(0),
                getM3uAccounts().get(0),
                getAccountsByType(AccountType.RSS_FEED).get(0)
        );

        for (Account account : removeSet) {
            cacheService.clearCache(account);
            assertAccountCacheTablesCleared(account.getDbId());
            accountService.delete(account.getDbId());
            assertNull(accountService.getById(account.getDbId()));
        }

        assertTypeCounts(2, 2, 2, 2);
    }

    private void clearAllCacheAndDeleteRemaining(CacheService cacheService, AccountService accountService) {
        cacheService.clearAllCache();
        assertAllCacheTablesEmpty();

        for (Account account : new ArrayList<>(accountService.getAll().values())) {
            accountService.delete(account.getDbId());
        }
        assertEquals(0, accountService.getAll().size());
        assertAllCacheTablesEmpty();
    }

    private void cacheLive(Account account, CacheService cacheService) throws IOException {
        account.setAction(itv);
        AccountService.getInstance().save(account);
        cacheService.reloadCache(account, m -> {
        });

        List<Category> categories = CategoryDb.get().getCategories(account);
        assertTrue(categories.size() >= 1, "Expected live categories for " + account.getAccountName());

        List<Channel> channels = allLiveChannels(account);
        assertTrue(channels.size() >= 2, "Expected at least 2 live channels for " + account.getAccountName());
        assertTrue(countLiveChannelRowsByAccount(account.getDbId()) >= 2);
    }

    private void cacheLiveWithFallbackAssertion(Account account, CacheService cacheService) throws IOException {
        account.setAction(itv);
        AccountService.getInstance().save(account);
        List<String> logs = new ArrayList<>();
        cacheService.reloadCache(account, logs::add);

        List<Category> categories = CategoryDb.get().getCategories(account);
        assertTrue(categories.size() >= 2, "Expected live categories for fallback account");

        List<Channel> channels = allLiveChannels(account);
        assertTrue(channels.size() >= 2, "Expected fallback channels to be cached");
        assertTrue(countLiveChannelRowsByAccount(account.getDbId()) >= 2);

        assertTrue(logs.stream().anyMatch(m -> m.contains("Trying last-resort category-by-category fetch")),
                "Expected get_all_channels fallback log");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Last-resort fetch succeeded")),
                "Expected fallback success log");
    }

    private void cacheVod(Account account) throws IOException {
        if (account.getType() != AccountType.STALKER_PORTAL && account.getType() != AccountType.XTREME_API) {
            return;
        }
        account.setAction(vod);
        AccountService.getInstance().save(account);

        List<Category> vodCategories = CategoryService.getInstance().get(account, false);
        assertTrue(vodCategories.size() >= 2, "Expected at least 2 VOD categories for " + account.getAccountName());

        int totalVodChannels = 0;
        for (Category category : vodCategories) {
            List<Channel> channels = ChannelService.getInstance().get(category.getCategoryId(), account, category.getDbId(), null, null, null);
            assertTrue(channels.size() >= 2, "Expected at least 2 VOD channels in category " + category.getTitle());
            totalVodChannels += channels.size();
        }
        assertTrue(totalVodChannels >= 4);
        assertEquals(vodCategories.size(), VodCategoryDb.get().getCategories(account).size());
        assertTrue(countRowsByAccount(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE, account.getDbId()) >= 4);
    }

    private void cacheSeries(Account account) throws IOException {
        if (account.getType() != AccountType.STALKER_PORTAL && account.getType() != AccountType.XTREME_API) {
            return;
        }
        account.setAction(series);
        AccountService.getInstance().save(account);

        List<Category> seriesCategories = CategoryService.getInstance().get(account, false);
        assertTrue(seriesCategories.size() >= 2, "Expected at least 2 series categories for " + account.getAccountName());

        int totalSeriesChannels = 0;
        for (Category category : seriesCategories) {
            List<Channel> channels = ChannelService.getInstance().get(category.getCategoryId(), account, category.getDbId(), null, null, null);
            assertTrue(channels.size() >= 1, "Expected series entries in category " + category.getTitle());
            totalSeriesChannels += channels.size();

            if (account.getType() == AccountType.STALKER_PORTAL) {
                assertTrue(channels.stream().anyMatch(c -> c.getName().contains("Episode 5")), "Expected Episode 5 in stalker series expansion");
            }
        }

        assertTrue(totalSeriesChannels >= 2);
        assertEquals(seriesCategories.size(), SeriesCategoryDb.get().getCategories(account).size());
        assertTrue(countRowsByAccount(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE, account.getDbId()) >= 2);
    }

    private void assertCategoryAndChannelCacheHitPaths() throws IOException {
        Account stalker = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        stalker.setAction(vod);
        AccountService.getInstance().save(stalker);

        List<String> categoryLogs = new ArrayList<>();
        List<Category> vodCategories = CategoryService.getInstance().get(stalker, false, categoryLogs::add);
        assertFalse(vodCategories.isEmpty());

        List<String> categoryLogsSecondCall = new ArrayList<>();
        List<Category> cachedVodCategories = CategoryService.getInstance().get(stalker, false, categoryLogsSecondCall::add);
        assertEquals(vodCategories.size(), cachedVodCategories.size());
        assertTrue(categoryLogsSecondCall.stream().anyMatch(m -> m.contains("Loaded categories from local cache.")),
                "Expected VOD category cache-hit log on second call");

        Category firstVodCategory = cachedVodCategories.get(0);
        List<String> channelLogsSecondCall = new ArrayList<>();
        List<Channel> cachedVodChannels = ChannelService.getInstance().get(
                firstVodCategory.getCategoryId(),
                stalker,
                firstVodCategory.getDbId(),
                channelLogsSecondCall::add
        );
        assertTrue(cachedVodChannels.size() >= 2);
        assertTrue(channelLogsSecondCall.stream().anyMatch(m -> m.contains("Loaded channels from local cache")),
                "Expected VOD channel cache-hit log on second call");
    }

    private void assertStalkerPageIndexFallback() {
        Account stalker = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        stalker.setAction(itv);
        AccountService.getInstance().save(stalker);
        List<String> logs = new ArrayList<>();
        List<Channel> channels = ChannelService.getInstance().getStalkerPortalChOrSeries(
                "99",
                stalker,
                null,
                "0",
                null,
                null,
                true,
                logs::add
        );
        assertFalse(channels.isEmpty(), "Expected stalker fallback from page 0 to page 1 to return channels");
        assertTrue(logs.stream().anyMatch(m -> m.contains("No channels on page 0. Retrying from page 1")),
                "Expected page index fallback log");
    }

    private void assertChannelFetchCancellationFlow() throws IOException {
        Account stalker = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        stalker.setAction(vod);
        AccountService.getInstance().save(stalker);

        AtomicInteger callbackHits = new AtomicInteger();
        List<String> logs = new ArrayList<>();
        List<Channel> channels = ChannelService.getInstance().get(
                "777",
                stalker,
                "cancel-test-category",
                logs::add,
                list -> callbackHits.incrementAndGet(),
                () -> true
        );

        assertEquals(1, channels.size(), "Cancellation flow should keep first page only");
        assertEquals(1, callbackHits.get(), "Expected callback only once before cancellation");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Portal fetch cancelled at page 1")),
                "Expected cancellation log message");
    }

    private void assertBookmarkLogoEnrichment(Account account, Channel channel) {
        Bookmark logoBookmark = new Bookmark(
                account.getAccountName(),
                "LogoCategory",
                channel.getChannelId(),
                channel.getName(),
                channel.getCmd(),
                account.getServerPortalUrl(),
                "logo-cat"
        );
        Channel logoMissingChannelJson = Channel.fromJson(channel.toJson());
        logoMissingChannelJson.setLogo("");
        logoBookmark.setChannelJson(logoMissingChannelJson.toJson());
        BookmarkService.getInstance().save(logoBookmark);

        JSONArray bookmarks = new JSONArray(BookmarkService.getInstance().readToJson());
        JSONObject enriched = null;
        for (int i = 0; i < bookmarks.length(); i++) {
            JSONObject row = bookmarks.getJSONObject(i);
            if (channel.getChannelId().equals(row.optString("channelId"))
                    && account.getAccountName().equals(row.optString("accountName"))) {
                enriched = row;
                break;
            }
        }
        assertNotNull(enriched, "Expected enriched bookmark entry");
        assertFalse(enriched.optString("logo", "").isBlank(), "Expected non-empty logo after bookmark JSON enrichment");
    }

    private void assertM3uUncategorizedAllCategoryCoverage() throws IOException {
        Account uncategorizedOnlyM3u = getM3uAccounts().stream()
                .filter(a -> a.getM3u8Path() != null && a.getM3u8Path().contains("/m3u/account-2.m3u"))
                .findFirst()
                .orElseThrow();

        uncategorizedOnlyM3u.setAction(itv);
        AccountService.getInstance().save(uncategorizedOnlyM3u);

        List<Category> categories = CategoryDb.get().getCategories(uncategorizedOnlyM3u);
        assertEquals(1, categories.size(), "Uncategorized-only M3U should expose only All category");
        Category allCategory = categories.get(0);
        assertEquals("All", allCategory.getTitle());

        List<Channel> channels = ChannelService.getInstance().get("All", uncategorizedOnlyM3u, allCategory.getDbId(), null, null, null);
        assertEquals(3, channels.size(), "All category should preserve all channels from uncategorized-only playlist");
        assertEquals(Set.of("Uncat One", "Uncat Two", "Uncat Three"),
                channels.stream().map(Channel::getName).collect(Collectors.toSet()));
    }

    private void assertSeriesEpisodeBookmarkPlayFlow() throws Exception {
        Account stalker = getAccountsByType(AccountType.STALKER_PORTAL).get(0);
        stalker.setAction(series);
        AccountService.getInstance().save(stalker);
        List<Category> seriesCategories = SeriesCategoryDb.get().getCategories(stalker);
        assertFalse(seriesCategories.isEmpty());
        List<Channel> seriesChannels = SeriesChannelDb.get().getChannels(stalker, seriesCategories.get(0).getDbId());
        Channel episodeChannel = seriesChannels.stream()
                .filter(c -> c.getName().contains("Episode 5"))
                .findFirst()
                .orElse(seriesChannels.get(0));

        com.uiptv.shared.Episode episode = new com.uiptv.shared.Episode();
        episode.setId(episodeChannel.getChannelId());
        episode.setCmd(episodeChannel.getCmd());
        episode.setTitle(episodeChannel.getName());

        Bookmark episodeBookmark = new Bookmark(
                stalker.getAccountName(),
                seriesCategories.get(0).getTitle(),
                episode.getId(),
                episode.getTitle(),
                episode.getCmd(),
                stalker.getServerPortalUrl(),
                seriesCategories.get(0).getCategoryId()
        );
        episodeBookmark.setAccountAction(series);
        episodeBookmark.setSeriesJson(episode.toJson());
        BookmarkService.getInstance().save(episodeBookmark);

        Bookmark persisted = BookmarkService.getInstance().getBookmark(episodeBookmark);
        assertNotNull(persisted);
        com.uiptv.shared.Episode restored = com.uiptv.shared.Episode.fromJson(persisted.getSeriesJson());
        Channel playbackChannel = new Channel();
        playbackChannel.setChannelId(restored.getId());
        playbackChannel.setCmd(restored.getCmd());
        playbackChannel.setName(restored.getTitle());

        PlayerResponse response = PlayerService.getInstance().get(stalker, playbackChannel, persisted.getChannelId());
        assertNotNull(response.getUrl());
        assertTrue(response.getUrl().contains("stream="));
    }

    private void assertSeriesWatchPointerProgressionThroughPlayerCallback(AccountService accountService) throws Exception {
        Account localPlaybackAccount = accountService.getByName("m3u-seed-1");
        localPlaybackAccount.setAction(series);
        accountService.save(localPlaybackAccount);
        localPlaybackAccount = accountService.getByName("m3u-seed-1");
        localPlaybackAccount.setAction(series);

        String seriesId = "series-e2e-pointer";
        SeriesWatchStateService.getInstance().clearSeriesLastWatched(localPlaybackAccount.getDbId(), seriesId);

        PlayerService.getInstance().get(localPlaybackAccount, buildPlayableEpisode("ep-2", "Episode 2", "1", "2"), "ep-2", seriesId);
        SeriesWatchState afterEpisode2 = SeriesWatchStateService.getInstance().getSeriesLastWatched(localPlaybackAccount.getDbId(), seriesId);
        assertNotNull(afterEpisode2);
        assertEquals("ep-2", afterEpisode2.getEpisodeId());
        assertEquals(2, afterEpisode2.getEpisodeNum());

        PlayerService.getInstance().get(localPlaybackAccount, buildPlayableEpisode("ep-1", "Episode 1", "1", "1"), "ep-1", seriesId);
        SeriesWatchState afterEpisode1 = SeriesWatchStateService.getInstance().getSeriesLastWatched(localPlaybackAccount.getDbId(), seriesId);
        assertNotNull(afterEpisode1);
        assertEquals("ep-2", afterEpisode1.getEpisodeId());
        assertEquals(2, afterEpisode1.getEpisodeNum());

        PlayerService.getInstance().get(localPlaybackAccount, buildPlayableEpisode("ep-3", "Episode 3", "1", "3"), "ep-3", seriesId);
        SeriesWatchState afterEpisode3 = SeriesWatchStateService.getInstance().getSeriesLastWatched(localPlaybackAccount.getDbId(), seriesId);
        assertNotNull(afterEpisode3);
        assertEquals("ep-3", afterEpisode3.getEpisodeId());
        assertEquals(3, afterEpisode3.getEpisodeNum());
    }

    private Channel buildPlayableEpisode(String episodeId, String title, String season, String episodeNum) {
        Channel episode = new Channel();
        episode.setChannelId(episodeId);
        episode.setName(title);
        episode.setCmd("http://example.com/stream/" + episodeId + ".m3u8");
        episode.setSeason(season);
        episode.setEpisodeNum(episodeNum);
        return episode;
    }

    private List<Channel> allLiveChannels(Account account) {
        account.setAction(itv);
        List<Channel> channels = new ArrayList<>();
        for (Category category : CategoryDb.get().getCategories(account)) {
            channels.addAll(ChannelDb.get().getChannels(category.getDbId()));
        }
        return channels;
    }

    private List<Account> getAccountsByType(AccountType type) {
        return AccountService.getInstance().getAll().values().stream()
                .filter(a -> a.getType() == type)
                .sorted(Comparator.comparing(Account::getDbId))
                .collect(Collectors.toList());
    }

    private void assertTypeCounts(int stalker, int xtreme, int m3u, int rss) {
        assertEquals(stalker, getAccountsByType(AccountType.STALKER_PORTAL).size());
        assertEquals(xtreme, getAccountsByType(AccountType.XTREME_API).size());
        assertEquals(m3u, getM3uAccounts().size());
        assertEquals(rss, getAccountsByType(AccountType.RSS_FEED).size());
    }

    private List<Account> getM3uAccounts() {
        return AccountService.getInstance().getAll().values().stream()
                .filter(a -> a.getType() == AccountType.M3U8_LOCAL || a.getType() == AccountType.M3U8_URL)
                .sorted(Comparator.comparing(Account::getDbId))
                .collect(Collectors.toList());
    }

    private void assertAccountCacheTablesCleared(String accountId) {
        assertEquals(0, countRowsByAccount(DatabaseUtils.DbTable.CATEGORY_TABLE, accountId));
        assertEquals(0, countLiveChannelRowsByAccount(accountId));
        assertEquals(0, countRowsByAccount(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE, accountId));
        assertEquals(0, countRowsByAccount(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE, accountId));
        assertEquals(0, countRowsByAccount(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE, accountId));
        assertEquals(0, countRowsByAccount(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE, accountId));
    }

    private void assertAllCacheTablesEmpty() {
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.CHANNEL_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE));
    }

    private void runDbSyncFlow() throws Exception {
        Path firstPath = tempDir.resolve("sync-first.db");
        Path secondPath = tempDir.resolve("sync-second.db");
        createSyncSchema(firstPath.toString());
        createSyncSchema(secondPath.toString());

        seedSyncDb(firstPath.toString(), "100", "sync-a", "101", "Sync One");
        seedSyncDb(secondPath.toString(), "200", "sync-b", "202", "Sync Two");

        com.uiptv.ui.RootApplication.syncDatabases(firstPath.toString(), secondPath.toString());

        for (DatabaseUtils.DbTable table : DatabaseUtils.Syncable) {
            int c1 = countRowsInDatabase(firstPath.toString(), table.getTableName());
            int c2 = countRowsInDatabase(secondPath.toString(), table.getTableName());
            assertEquals(c1, c2, "Synced table row count mismatch for " + table.getTableName());
            assertTrue(c1 >= 2 || table == DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE,
                    "Expected merged rows in " + table.getTableName());
        }
    }

    private void createSyncSchema(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                statement.execute(DatabaseUtils.createTableSql(table));
            }
        }
    }

    private void seedSyncDb(String dbPath, String accountId, String accountName, String bookmarkId, String bookmarkName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO Account (id, accountName, username, password, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, epg, m3u8Path, type, serverPortalUrl, pinToTop, httpMethod, timezone) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, accountId);
                ps.setString(2, accountName);
                ps.setString(3, "u");
                ps.setString(4, "p");
                ps.setString(5, "http://sync.example");
                ps.setString(6, null);
                ps.setString(7, null);
                ps.setString(8, null);
                ps.setString(9, null);
                ps.setString(10, null);
                ps.setString(11, null);
                ps.setString(12, null);
                ps.setString(13, null);
                ps.setString(14, AccountType.M3U8_URL.name());
                ps.setString(15, "");
                ps.setString(16, "0");
                ps.setString(17, "GET");
                ps.setString(18, "UTC");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO BookmarkCategory (id, name) VALUES (?,?)")) {
                ps.setString(1, accountId);
                ps.setString(2, "sync-category-" + accountId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO Bookmark (id, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl, categoryId, accountAction, drmType, drmLicenseUrl, clearKeysJson, inputstreamaddon, manifestType, categoryJson, channelJson, vodJson, seriesJson) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, bookmarkId);
                ps.setString(2, accountName);
                ps.setString(3, "Sync");
                ps.setString(4, "ch-" + bookmarkId);
                ps.setString(5, bookmarkName);
                ps.setString(6, "ffmpeg http://sync/" + bookmarkId + ".ts");
                ps.setString(7, "http://sync.example");
                ps.setString(8, "cat-sync");
                ps.setString(9, "itv");
                ps.setString(10, null);
                ps.setString(11, null);
                ps.setString(12, null);
                ps.setString(13, null);
                ps.setString(14, null);
                ps.setString(15, "{}");
                ps.setString(16, "{}");
                ps.setString(17, "{}");
                ps.setString(18, "{}");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO BookmarkOrder (id, bookmark_db_id, category_id, display_order) VALUES (?,?,?,?)")) {
                ps.setString(1, bookmarkId);
                ps.setString(2, bookmarkId);
                ps.setString(3, "cat-sync");
                ps.setInt(4, 0);
                ps.executeUpdate();
            }
        }
    }

    private int countRowsInDatabase(String dbPath, String tableName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement statement = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int countTableRows(DatabaseUtils.DbTable table) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table.getTableName();
            try (Connection conn = SQLConnection.connect();
                 PreparedStatement statement = conn.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int countRowsByAccount(DatabaseUtils.DbTable table, String accountId) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table.getTableName() + " WHERE accountId = ?";
            try (Connection conn = SQLConnection.connect();
                 PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, accountId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int countLiveChannelRowsByAccount(String accountId) {
        try {
            String sql = "SELECT COUNT(*) FROM " + DatabaseUtils.DbTable.CHANNEL_TABLE.getTableName() +
                    " c JOIN " + DatabaseUtils.DbTable.CATEGORY_TABLE.getTableName() +
                    " cat ON c.categoryId = cat.id WHERE cat.accountId = ?";
            try (Connection conn = SQLConnection.connect();
                 PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, accountId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveStalker(String name, String mac) {
        Account account = new Account(
                name,
                "st-user",
                "st-pass",
                stalkerPortalUrl,
                mac,
                null,
                "AABBCCDDEE11",
                "AABBCCDDEEFF00112233445566778899",
                "AABBCCDDEEFF00112233445566778899",
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        account.setServerPortalUrl(stalkerPortalUrl);
        account.setAction(itv);
        AccountService.getInstance().save(account);
    }

    private void saveXtreme(String name, String username, String password) {
        Account account = new Account(
                name,
                username,
                password,
                xtremeBaseUrl,
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                xtremeBaseUrl,
                false
        );
        account.setAction(itv);
        AccountService.getInstance().save(account);
    }

    private void saveM3uLocal(String name, String path) {
        Account account = new Account(
                name,
                null,
                null,
                path,
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.M3U8_LOCAL,
                null,
                path,
                false
        );
        account.setAction(itv);
        AccountService.getInstance().save(account);
    }

    private void saveRss(String name, String feedUrl) {
        Account account = new Account(
                name,
                null,
                null,
                feedUrl,
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.RSS_FEED,
                null,
                feedUrl,
                false
        );
        account.setAction(itv);
        AccountService.getInstance().save(account);
    }

    private String writeM3uPlaylist(String fileName, boolean withDrm) throws IOException {
        String drmBlock = withDrm
                ? """
                #EXTINF:-1 tvg-id=\"drm-1\" tvg-logo=\"drm.png\" group-title=\"Live\",DRM Live
                #KODIPROP:inputstreamaddon=inputstream.adaptive
                #KODIPROP:inputstream.adaptive.manifest_type=mpd
                #KODIPROP:inputstream.adaptive.license_type=com.clearkey.alpha
                #KODIPROP:inputstream.adaptive.license_key=00112233445566778899aabbccddeeff:11223344556677889900aabbccddeeff
                http://example.com/drm/live.mpd
                """
                : "";

        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id=\"live-1\" tvg-logo=\"live1.png\" group-title=\"Live\",Live One
                http://example.com/live/one.ts
                #EXTINF:-1 tvg-id=\"live-2\" tvg-logo=\"live2.png\" group-title=\"Live\",Live Two
                http://example.com/live/two.ts
                """ + drmBlock;

        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toString();
    }

    private String stalkerBulkText() {
        return """
                %s
                mac: 00:11:22:33:44:52
                serial: AABBCCDDEE12
                device id 1: AABBCCDDEEFF001122334455667788AA
                device id 2: AABBCCDDEEFF001122334455667788AA
                signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899

                %s
                mac: 00:11:22:33:44:53
                serial: AABBCCDDEE13
                device id 1: AABBCCDDEEFF001122334455667788AB
                device id 2: AABBCCDDEEFF001122334455667788AB
                signature: AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899
                """.formatted(stalkerPortalUrl, stalkerPortalUrl);
    }

    private String xtremeBulkText() {
        return """
                %s
                User : xtuser2
                Pass : xtpass2

                %s
                User : xtuser3
                Pass : xtpass3
                """.formatted(xtremeBaseUrl + "a", xtremeBaseUrl + "b");
    }

    private String m3uBulkText() {
        return """
                %s/m3u/account-2.m3u
                %s/m3u/account-3.m3u
                """.formatted(baseUrl, baseUrl);
    }

    private void handleStalker(HttpExchange exchange) throws IOException {
        Map<String, String> q = readQuery(exchange);
        String action = q.getOrDefault("action", "");
        String type = q.getOrDefault("type", "");

        String body;
        if ("handshake".equals(action)) {
            body = "{\"js\":{\"token\":\"mock-token\"}}";
        } else if ("get_profile".equals(action) || "get_main_info".equals(action)) {
            body = "{\"js\":{\"status\":\"ok\"}}";
        } else if ("get_genres".equals(action) && "itv".equals(type)) {
            body = """
                    {"js":[
                      {"id":"10","title":"News","alias":"news","active_sub":true,"censored":0},
                      {"id":"20","title":"Sports","alias":"sports","active_sub":true,"censored":0}
                    ]}
                    """;
        } else if ("get_categories".equals(action) && "vod".equals(type)) {
            body = """
                    {"js":[
                      {"id":"101","title":"Movies A","alias":"movies-a","active_sub":true,"censored":0},
                      {"id":"102","title":"Movies B","alias":"movies-b","active_sub":true,"censored":0}
                    ]}
                    """;
        } else if ("get_categories".equals(action) && "series".equals(type)) {
            body = """
                    {"js":[
                      {"id":"201","title":"Series A","alias":"series-a","active_sub":true,"censored":0},
                      {"id":"202","title":"Series B","alias":"series-b","active_sub":true,"censored":0}
                    ]}
                    """;
        } else if ("get_all_channels".equals(action)) {
            if (isFallbackStalkerAccount(exchange)) {
                body = "";
            } else {
                body = """
                        {"js":{"data":[
                          {"id":"1001","name":"Stalker News","number":"1","cmd":"ffmpeg %s/live/play/live.php?stream=1001&play_token=pt1001","cmd_1":"","cmd_2":"","cmd_3":"","logo":"logo1","censored":0,"status":1,"hd":1,"tv_genre_id":"10"},
                          {"id":"2001","name":"Stalker Sports","number":"2","cmd":"ffmpeg %s/live/play/live.php?stream=2001&play_token=pt2001","cmd_1":"","cmd_2":"","cmd_3":"","logo":"logo2","censored":0,"status":1,"hd":1,"tv_genre_id":"20"}
                        ]}}
                        """.formatted(baseUrl, baseUrl);
            }
        } else if ("get_ordered_list".equals(action) && "vod".equals(type)) {
            String genre = q.getOrDefault("genre", "");
            String page = q.getOrDefault("p", "0");
            if ("777".equals(genre)) {
                if ("0".equals(page)) {
                    body = """
                            {"js":{"total_items":3,"max_page_items":1,"data":[
                              {"id":"7771","name":"Vod Cancel One","o_name":"Vod Cancel One","cmd":"ffmpeg %s/play/movie.php?stream=7771&type=movie","tv_genre_id":"777","stream_icon":"%s/vod/777-1.png","censored":0,"status":1,"hd":1}
                            ]}}
                            """.formatted(baseUrl, baseUrl);
                } else if ("1".equals(page)) {
                    body = """
                            {"js":{"total_items":3,"max_page_items":1,"data":[
                              {"id":"7772","name":"Vod Cancel Two","o_name":"Vod Cancel Two","cmd":"ffmpeg %s/play/movie.php?stream=7772&type=movie","tv_genre_id":"777","stream_icon":"%s/vod/777-2.png","censored":0,"status":1,"hd":1}
                            ]}}
                            """.formatted(baseUrl, baseUrl);
                } else {
                    body = "{\"js\":{\"total_items\":3,\"max_page_items\":1,\"data\":[]}}";
                }
            } else if (!"0".equals(page)) {
                body = "{\"js\":{\"total_items\":2,\"max_page_items\":999,\"data\":[]}}";
            } else {
                body = """
                        {"js":{"total_items":2,"max_page_items":999,"data":[
                          {"id":"%s1","name":"Vod %s One","o_name":"Vod %s One","cmd":"ffmpeg %s/play/movie.php?stream=%s1&type=movie","tv_genre_id":"%s","stream_icon":"%s/vod/%s-1.png","censored":0,"status":1,"hd":1},
                          {"id":"%s2","name":"Vod %s Two","o_name":"Vod %s Two","cmd":"ffmpeg %s/play/movie.php?stream=%s2&type=movie","tv_genre_id":"%s","stream_icon":"%s/vod/%s-2.png","censored":0,"status":1,"hd":1}
                        ]}}
                        """.formatted(genre, genre, genre, baseUrl, genre, genre, baseUrl, genre, genre, genre, genre, baseUrl, genre, genre, baseUrl, genre);
            }
        } else if ("get_ordered_list".equals(action) && "series".equals(type)) {
            String genre = q.getOrDefault("genre", "");
            String page = q.getOrDefault("p", "0");
            if (!"0".equals(page)) {
                body = "{\"js\":{\"total_items\":1,\"max_page_items\":999,\"data\":[]}}";
            } else {
                body = """
                        {"js":{"total_items":1,"max_page_items":999,"data":[
                          {"id":"9%s","name":"","o_name":"Series %s","cmd":"ffmpeg %s/play/movie.php?stream=9%s&type=series","tv_genre_id":"%s","series":[1,2,3,4,5],"screenshot_uri":"%s/series/%s.png","censored":0,"status":1,"hd":1}
                        ]}}
                        """.formatted(genre, genre, baseUrl, genre, genre, baseUrl, genre);
            }
        } else if ("get_ordered_list".equals(action) && "itv".equals(type)) {
            String genre = q.getOrDefault("genre", "10");
            String page = q.getOrDefault("p", "0");
            if ("99".equals(genre) && "0".equals(page)) {
                body = "{\"js\":{\"total_items\":1,\"max_page_items\":999,\"data\":[]}}";
            } else {
                body = """
                        {"js":{"total_items":1,"max_page_items":999,"data":[
                          {"id":"%s0","name":"Fallback %s","number":"1","cmd":"ffmpeg %s/live/play/live.php?stream=%s0&play_token=pt%s0","cmd_1":"","cmd_2":"","cmd_3":"","logo":"fallback","censored":0,"status":1,"hd":1,"tv_genre_id":"%s"}
                        ]}}
                        """.formatted(genre, genre, baseUrl, genre, genre, genre);
            }
        } else if ("create_link".equals(action)) {
            body = """
                    {"js":{"cmd":"ffmpeg %s/live/play/live.php?stream=&extension=ts&play_token="}}
                    """.formatted(baseUrl);
        } else {
            body = "{\"js\":{\"data\":[]}}";
        }

        writeResponse(exchange, 200, body, "application/json; charset=utf-8");
    }

    private void handleXtreme(HttpExchange exchange) throws IOException {
        Map<String, String> q = readQuery(exchange);
        String action = q.getOrDefault("action", "");
        String categoryId = q.getOrDefault("category_id", "");
        String seriesId = q.getOrDefault("series_id", "9001");

        String body;
        switch (action) {
            case "get_live_categories" -> body = """
                    [
                      {"category_id":"401","category_name":"X Live A"},
                      {"category_id":"402","category_name":"X Live B"}
                    ]
                    """;
            case "get_live_streams" -> body = """
                    [
                      {"stream_id":"%s1","name":"X Live %s One","stream_icon":"%s/xtreme/live/%s1.png","container_extension":"ts"},
                      {"stream_id":"%s2","name":"X Live %s Two","stream_icon":"%s/xtreme/live/%s2.png","container_extension":"ts"}
                    ]
                    """.formatted(categoryId, categoryId, baseUrl, categoryId, categoryId, categoryId, baseUrl, categoryId);
            case "get_vod_categories" -> body = """
                    [
                      {"category_id":"501","category_name":"X Vod A"},
                      {"category_id":"502","category_name":"X Vod B"}
                    ]
                    """;
            case "get_vod_streams" -> body = """
                    [
                      {"stream_id":"%s1","name":"X Vod %s One","stream_icon":"%s/xtreme/vod/%s1.jpg","container_extension":"mp4"},
                      {"stream_id":"%s2","name":"X Vod %s Two","stream_icon":"%s/xtreme/vod/%s2.jpg","container_extension":"mp4"}
                    ]
                    """.formatted(categoryId, categoryId, baseUrl, categoryId, categoryId, categoryId, baseUrl, categoryId);
            case "get_series_categories" -> body = """
                    [
                      {"category_id":"601","category_name":"X Series A"},
                      {"category_id":"602","category_name":"X Series B"}
                    ]
                    """;
            case "get_series" -> body = """
                    [
                      {"series_id":"9%s","stream_id":"9%s","name":"X Series %s","cover":"%s/xtreme/series/%s.jpg","container_extension":"mp4"}
                    ]
                    """.formatted(categoryId, categoryId, categoryId, baseUrl, categoryId);
            case "get_series_info" -> body = """
                    {
                      "info": {"name":"Series %s"},
                      "episodes": {
                        "1": [
                          {"id":"%s1","episode_num":"1","title":"Episode 1","container_extension":"mp4","info":{"movie_image":"%s/ep/1.jpg"}},
                          {"id":"%s2","episode_num":"2","title":"Episode 2","container_extension":"mp4","info":{"movie_image":"%s/ep/2.jpg"}},
                          {"id":"%s3","episode_num":"3","title":"Episode 3","container_extension":"mp4","info":{"movie_image":"%s/ep/3.jpg"}},
                          {"id":"%s4","episode_num":"4","title":"Episode 4","container_extension":"mp4","info":{"movie_image":"%s/ep/4.jpg"}},
                          {"id":"%s5","episode_num":"5","title":"Episode 5","container_extension":"mp4","info":{"movie_image":"%s/ep/5.jpg"}}
                        ]
                      }
                    }
                    """.formatted(seriesId, seriesId, baseUrl, seriesId, baseUrl, seriesId, baseUrl, seriesId, baseUrl, seriesId, baseUrl);
            default -> body = "[]";
        }

        writeResponse(exchange, 200, body, "application/json; charset=utf-8");
    }

    private void handleRss(HttpExchange exchange) throws IOException {
        String body = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Mock RSS</title>
                    <link>%s</link>
                    <description>Mock feed</description>
                    <item>
                      <title>RSS Live One</title>
                      <link>%s/rss/stream/one.ts</link>
                    </item>
                    <item>
                      <title>RSS Live Two</title>
                      <link>%s/rss/stream/two.ts</link>
                    </item>
                  </channel>
                </rss>
                """.formatted(baseUrl, baseUrl, baseUrl);
        writeResponse(exchange, 200, body, "application/rss+xml; charset=utf-8");
    }

    private void handleM3uPlaylist(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri == null ? "" : uri.getPath();
        String body;
        if (path.endsWith("account-2.m3u")) {
            body = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="u-1" group-title="Uncategorized",Uncat One
                    http://example.com/m3u/uncat/one.ts
                    #EXTINF:-1 tvg-id="u-2" group-title="Uncategorized",Uncat Two
                    http://example.com/m3u/uncat/two.ts
                    #EXTINF:-1 tvg-id="u-3" group-title="Uncategorized",Uncat Three
                    http://example.com/m3u/uncat/three.ts
                    """;
        } else {
            body = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="m3u3-1" group-title="Live",M3U Three One
                    http://example.com/m3u/three/one.ts
                    #EXTINF:-1 tvg-id="m3u3-2" group-title="Live",M3U Three Two
                    http://example.com/m3u/three/two.ts
                    """;
        }
        writeResponse(exchange, 200, body, "application/vnd.apple.mpegurl; charset=utf-8");
    }

    private Map<String, String> readQuery(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI() != null ? exchange.getRequestURI().getRawQuery() : "";
        if (query == null || query.isBlank()) {
            query = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            if (pair == null || pair.isBlank()) continue;
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private boolean isFallbackStalkerAccount(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        return cookie != null && cookie.contains("00:11:22:33:44:53");
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void writeResponse(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }
}
