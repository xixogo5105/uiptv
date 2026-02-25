package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.server.api.json.HttpWebChannelJsonServer;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.ChannelService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AccountType;
import com.uiptv.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndWebServerIntegrationFlowTest extends DbBackedTest {
    private HttpServer providerMockServer;
    private String providerBaseUrl;
    private String stalkerPortalUrl;
    private String xtremeBaseUrl;
    private String rssFeedUrl;

    private int appPort;
    private String appBaseUrl;

    @BeforeEach
    void setUpServers() throws Exception {
        providerMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerMockServer.createContext("/portal.php", this::handleStalker);
        providerMockServer.createContext("/xtreme/player_api.php", this::handleXtreme);
        providerMockServer.createContext("/rss/feed.xml", this::handleRss);
        providerMockServer.createContext("/m3u", this::handleM3uPlaylist);
        providerMockServer.start();

        int providerPort = providerMockServer.getAddress().getPort();
        providerBaseUrl = "http://127.0.0.1:" + providerPort;
        stalkerPortalUrl = providerBaseUrl + "/portal.php";
        xtremeBaseUrl = providerBaseUrl + "/xtreme/";
        rssFeedUrl = providerBaseUrl + "/rss/feed.xml";

        appPort = findFreePort();
        appBaseUrl = "http://127.0.0.1:" + appPort;
    }

    @AfterEach
    void tearDownServers() throws Exception {
        try {
            UIptvServer.stop();
        } catch (Exception ignored) {
        }
        if (providerMockServer != null) {
            providerMockServer.stop(0);
        }
    }

    @Test
    /*
     * End-to-end backend web API coverage:
     *
     * 1) Starts real UIptvServer on a random configured port.
     * 2) Seeds Stalker/Xtreme/M3U/RSS accounts against a mock provider server.
     * 3) Populates cache and then exercises backend endpoints used by web clients:
     *    - /accounts
     *    - /categories
     *    - /channels (category + All + series movieId branch)
     *    - /seriesEpisodes
     *    - /seriesDetails
     *    - /vodDetails
     *    - /player (direct channel path + bookmark path + drmLaunch payload path)
     *    - /bookmarks (OPTIONS/GET/POST/PUT/DELETE + categories view)
     *    - /playlist.m3u8
     *    - /bookmarks.m3u8
     *    - /bookmarkEntry.ts
     *    - /iptv.m3u8 and /iptv.m3u
     * 4) Verifies SPA pages wired in UIptvServer:
     *    - /
     *    - /index.html
     *    - /myflix.html
     *    - /player.html
     *    - /drm.html
     * 5) Covers HttpWebChannelJsonServer via dedicated temporary HTTP context:
     *    - Stalker paged response shape
     *    - Non-stalker fallback slicing response shape
     */
    void endToEndWebPortal_backendApisAndAllJsonHandlers() throws Exception {
        Configuration cfg = new Configuration(
                "player-a", "player-b", "player-c", "player-a",
                "", "", false, "JetBrains Mono", "12", "Medium",
                false, String.valueOf(appPort), false, false
        );
        ConfigurationService.getInstance().save(cfg);

        saveAccounts();
        warmUpCacheAndData();

        try (MockedStatic<LogUtil> logUtilMock = Mockito.mockStatic(LogUtil.class);
             MockedStatic<ImdbMetadataService> imdbStatic = Mockito.mockStatic(ImdbMetadataService.class)) {
            logUtilMock.when(() -> LogUtil.httpLog(Mockito.anyString(), Mockito.any(), Mockito.anyMap())).thenAnswer(i -> null);

            ImdbMetadataService imdb = Mockito.mock(ImdbMetadataService.class);
            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdb);
            Mockito.when(imdb.findBestEffortDetails(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(new JSONObject("""
                            {"name":"Series Mock","imdbUrl":"https://www.imdb.com/title/tt1234567/","episodesMeta":[]}
                            """));
            Mockito.when(imdb.findBestEffortMovieDetails(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(new JSONObject("""
                            {"name":"Movie Mock","imdbUrl":"https://www.imdb.com/title/tt7654321/"}
                            """));

            UIptvServer.start();
            assertTrue(UIptvServer.isRunning());

            assertSpaPages();
            assertAccountsApi();
            assertCategoriesApi();
            assertChannelsApi();
            assertSeriesApis();
            assertVodDetailsApi();
            assertBookmarksApis();
            assertPlayerApis();
            assertPlaylistApis();
            assertWebChannelJsonServerApi();
        }
    }

    private void saveAccounts() throws IOException {
        String drmLocalPlaylist = writeM3uPlaylist("m3u-drm-local.m3u", true);

        Account stalker = new Account(
                "web-stalker", "st-user", "st-pass", stalkerPortalUrl,
                "00:11:22:33:44:81", null,
                "AABBCCDDEE81", "AABBCCDDEEFF00112233445566778881", "AABBCCDDEEFF00112233445566778881",
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                AccountType.STALKER_PORTAL, null, null, false
        );
        stalker.setServerPortalUrl(stalkerPortalUrl);
        stalker.setAction(itv);
        AccountService.getInstance().save(stalker);

        Account xtreme = new Account(
                "web-xtreme", "xtuser", "xtpass", xtremeBaseUrl,
                null, null, null, null, null, null,
                AccountType.XTREME_API, null, xtremeBaseUrl, false
        );
        xtreme.setAction(itv);
        AccountService.getInstance().save(xtreme);

        Account m3uLocal = new Account(
                "web-m3u-local", null, null, drmLocalPlaylist,
                null, null, null, null, null, null,
                AccountType.M3U8_LOCAL, null, drmLocalPlaylist, false
        );
        m3uLocal.setAction(itv);
        AccountService.getInstance().save(m3uLocal);

        Account m3uUrl = new Account(
                "web-m3u-url", null, null, providerBaseUrl + "/m3u/account-2.m3u",
                null, null, null, null, null, null,
                AccountType.M3U8_URL, null, providerBaseUrl + "/m3u/account-2.m3u", false
        );
        m3uUrl.setAction(itv);
        AccountService.getInstance().save(m3uUrl);

        Account rss = new Account(
                "web-rss", null, null, rssFeedUrl,
                null, null, null, null, null, null,
                AccountType.RSS_FEED, null, rssFeedUrl, false
        );
        rss.setAction(itv);
        AccountService.getInstance().save(rss);
    }

    private void warmUpCacheAndData() throws IOException {
        CacheService cacheService = new CacheServiceImpl();
        AccountService accountService = AccountService.getInstance();

        Account stalker = accountService.getByName("web-stalker");
        stalker.setAction(itv);
        accountService.save(stalker);
        cacheService.reloadCache(stalker, m -> {});

        stalker.setAction(vod);
        accountService.save(stalker);
        CategoryService.getInstance().get(stalker, false);
        List<Category> stalkerVodCats = VodCategoryDb.get().getCategories(stalker);
        for (Category c : stalkerVodCats) {
            ChannelService.getInstance().get(c.getCategoryId(), stalker, c.getDbId(), null, null, null);
        }

        stalker.setAction(series);
        accountService.save(stalker);
        CategoryService.getInstance().get(stalker, false);
        List<Category> stalkerSeriesCats = SeriesCategoryDb.get().getCategories(stalker);
        for (Category c : stalkerSeriesCats) {
            ChannelService.getInstance().get(c.getCategoryId(), stalker, c.getDbId(), null, null, null);
        }

        Account xtreme = accountService.getByName("web-xtreme");
        xtreme.setAction(itv);
        accountService.save(xtreme);
        cacheService.reloadCache(xtreme, m -> {});

        xtreme.setAction(vod);
        accountService.save(xtreme);
        CategoryService.getInstance().get(xtreme, false);
        List<Category> xtremeVodCats = VodCategoryDb.get().getCategories(xtreme);
        for (Category c : xtremeVodCats) {
            ChannelService.getInstance().get(c.getCategoryId(), xtreme, c.getDbId(), null, null, null);
        }

        xtreme.setAction(series);
        accountService.save(xtreme);
        CategoryService.getInstance().get(xtreme, false);
        List<Category> xtremeSeriesCats = SeriesCategoryDb.get().getCategories(xtreme);
        for (Category c : xtremeSeriesCats) {
            ChannelService.getInstance().get(c.getCategoryId(), xtreme, c.getDbId(), null, null, null);
        }

        for (String name : List.of("web-m3u-local", "web-m3u-url", "web-rss")) {
            Account a = accountService.getByName(name);
            a.setAction(itv);
            accountService.save(a);
            cacheService.reloadCache(a, m -> {});
        }

        BookmarkService bookmarkService = BookmarkService.getInstance();
        bookmarkService.addCategory(new com.uiptv.model.BookmarkCategory(null, "Web Favorites"));
        com.uiptv.model.BookmarkCategory webCategory = bookmarkService.getAllCategories().stream()
                .filter(c -> "Web Favorites".equals(c.getName()))
                .findFirst()
                .orElseThrow();

        Account stalkerItv = accountService.getByName("web-stalker");
        stalkerItv.setAction(itv);
        List<Category> liveCats = CategoryDb.get().getCategories(stalkerItv);
        Category liveCategory = liveCats.stream().filter(c -> !"All".equalsIgnoreCase(c.getTitle())).findFirst().orElse(liveCats.get(0));
        Channel liveChannel = ChannelDb.get().getChannels(liveCategory.getDbId()).get(0);

        Bookmark b = new Bookmark(
                stalkerItv.getAccountName(),
                liveCategory.getTitle(),
                liveChannel.getChannelId(),
                liveChannel.getName(),
                liveChannel.getCmd(),
                stalkerItv.getServerPortalUrl(),
                liveCategory.getDbId()
        );
        b.setAccountAction(itv);
        b.setFromChannel(liveChannel);
        b.setChannelJson(liveChannel.toJson());
        b.setCategoryId(webCategory.getId());
        bookmarkService.save(b);

        M3U8PublicationService.getInstance().setSelectedAccountIds(
                Set.of(
                        accountService.getByName("web-m3u-local").getDbId(),
                        accountService.getByName("web-m3u-url").getDbId()
                )
        );
    }

    private void assertSpaPages() throws Exception {
        for (String path : List.of("/", "/index.html", "/myflix.html", "/player.html", "/drm.html")) {
            HttpTextResponse response = get(path);
            assertEquals(200, response.statusCode(), "Expected 200 for " + path);
            assertTrue(response.body().toLowerCase().contains("<html"), "Expected html body for " + path);
        }
    }

    private void assertAccountsApi() throws Exception {
        HttpTextResponse response = get("/accounts");
        assertEquals(200, response.statusCode());
        JSONArray accounts = new JSONArray(response.body());
        assertTrue(accounts.length() >= 5);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < accounts.length(); i++) {
            names.add(accounts.getJSONObject(i).optString("accountName"));
        }
        assertTrue(names.contains("web-stalker"));
        assertTrue(names.contains("web-xtreme"));
    }

    private void assertCategoriesApi() throws Exception {
        AccountService accountService = AccountService.getInstance();
        Account stalker = accountService.getByName("web-stalker");
        Account xtreme = accountService.getByName("web-xtreme");
        Account m3u = accountService.getByName("web-m3u-local");
        Account rss = accountService.getByName("web-rss");

        JSONArray stalkerItvCats = jsonArrayBody(get("/categories?accountId=" + stalker.getDbId() + "&mode=itv"));
        JSONArray stalkerVodCats = jsonArrayBody(get("/categories?accountId=" + stalker.getDbId() + "&mode=vod"));
        JSONArray stalkerSeriesCats = jsonArrayBody(get("/categories?accountId=" + stalker.getDbId() + "&mode=series"));
        JSONArray xtremeItvCats = jsonArrayBody(get("/categories?accountId=" + xtreme.getDbId() + "&mode=itv"));
        JSONArray xtremeVodCats = jsonArrayBody(get("/categories?accountId=" + xtreme.getDbId() + "&mode=vod"));
        JSONArray xtremeSeriesCats = jsonArrayBody(get("/categories?accountId=" + xtreme.getDbId() + "&mode=series"));
        JSONArray m3uCats = jsonArrayBody(get("/categories?accountId=" + m3u.getDbId() + "&mode=itv"));
        JSONArray rssCats = jsonArrayBody(get("/categories?accountId=" + rss.getDbId() + "&mode=itv"));

        assertTrue(stalkerItvCats.length() >= 2);
        assertTrue(stalkerVodCats.length() >= 2);
        assertTrue(stalkerSeriesCats.length() >= 2);
        assertTrue(xtremeItvCats.length() >= 2);
        assertTrue(xtremeVodCats.length() >= 2);
        assertTrue(xtremeSeriesCats.length() >= 2);
        assertTrue(m3uCats.length() >= 1);
        assertTrue(rssCats.length() >= 1);
    }

    private void assertChannelsApi() throws Exception {
        AccountService accountService = AccountService.getInstance();
        Account stalker = accountService.getByName("web-stalker");
        Account xtreme = accountService.getByName("web-xtreme");

        Category stalkerLiveCategory = CategoryDb.get().getCategories(stalker).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .findFirst()
                .orElseThrow();
        JSONArray stalkerChannels = jsonArrayBody(get("/channels?accountId=" + stalker.getDbId() + "&categoryId=" + stalkerLiveCategory.getDbId() + "&mode=itv"));
        assertTrue(stalkerChannels.length() >= 1);

        JSONArray stalkerAllChannels = jsonArrayBody(get("/channels?accountId=" + stalker.getDbId() + "&categoryId=All&mode=itv"));
        assertTrue(stalkerAllChannels.length() >= stalkerChannels.length());

        JSONArray stalkerSeriesCategories = jsonArrayBody(get("/categories?accountId=" + stalker.getDbId() + "&mode=series"));
        assertTrue(stalkerSeriesCategories.length() >= 1);
        String stalkerSeriesCategoryDbId = stalkerSeriesCategories.getJSONObject(0).optString("dbId");
        JSONArray stalkerSeriesChildren = jsonArrayBody(get("/channels?accountId=" + stalker.getDbId()
                + "&categoryId=" + URLEncoder.encode(stalkerSeriesCategoryDbId, StandardCharsets.UTF_8) + "&mode=series&movieId=9201"));
        assertTrue(stalkerSeriesChildren.length() >= 1);

        Category xtremeLiveCategory = CategoryDb.get().getCategories(xtreme).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .findFirst()
                .orElseThrow();
        JSONArray xtremeChannels = jsonArrayBody(get("/channels?accountId=" + xtreme.getDbId() + "&categoryId=" + xtremeLiveCategory.getDbId() + "&mode=itv"));
        assertTrue(xtremeChannels.length() >= 1);
    }

    private void assertSeriesApis() throws Exception {
        Account xtreme = AccountService.getInstance().getByName("web-xtreme");
        JSONArray xtremeSeriesCategories = jsonArrayBody(get("/categories?accountId=" + xtreme.getDbId() + "&mode=series"));
        assertTrue(xtremeSeriesCategories.length() >= 1);
        String xtremeSeriesCategoryDbId = xtremeSeriesCategories.getJSONObject(0).optString("dbId");
        JSONArray seriesRows = jsonArrayBody(get("/channels?accountId=" + xtreme.getDbId()
                + "&categoryId=" + URLEncoder.encode(xtremeSeriesCategoryDbId, StandardCharsets.UTF_8) + "&mode=series"));
        assertTrue(seriesRows.length() >= 1);
        String seriesId = seriesRows.getJSONObject(0).optString("channelId");

        JSONArray episodes = jsonArrayBody(get("/seriesEpisodes?accountId=" + xtreme.getDbId() + "&seriesId=" + URLEncoder.encode(seriesId, StandardCharsets.UTF_8)));
        assertTrue(episodes.length() >= 5);
        JSONArray cachedEpisodes = jsonArrayBody(get("/seriesEpisodes?accountId=" + xtreme.getDbId() + "&seriesId=" + URLEncoder.encode(seriesId, StandardCharsets.UTF_8)));
        assertEquals(episodes.length(), cachedEpisodes.length());

        JSONObject details = jsonObjectBody(get("/seriesDetails?accountId=" + xtreme.getDbId()
                + "&seriesId=" + URLEncoder.encode(seriesId, StandardCharsets.UTF_8)
                + "&seriesName=" + URLEncoder.encode("X Series", StandardCharsets.UTF_8)));
        assertTrue(details.has("seasonInfo"));
        assertTrue(details.has("episodes"));
        assertTrue(details.has("episodesMeta"));
    }

    private void assertVodDetailsApi() throws Exception {
        Account xtreme = AccountService.getInstance().getByName("web-xtreme");
        JSONArray xtremeVodCategories = jsonArrayBody(get("/categories?accountId=" + xtreme.getDbId() + "&mode=vod"));
        assertTrue(xtremeVodCategories.length() >= 1);
        String xtremeVodCategoryDbId = xtremeVodCategories.getJSONObject(0).optString("dbId");
        JSONArray vodRows = jsonArrayBody(get("/channels?accountId=" + xtreme.getDbId()
                + "&categoryId=" + URLEncoder.encode(xtremeVodCategoryDbId, StandardCharsets.UTF_8) + "&mode=vod"));
        assertTrue(vodRows.length() >= 1);
        JSONObject vod = vodRows.getJSONObject(0);

        JSONObject details = jsonObjectBody(get("/vodDetails?accountId=" + xtreme.getDbId()
                + "&categoryId=" + URLEncoder.encode(xtremeVodCategoryDbId, StandardCharsets.UTF_8)
                + "&channelId=" + URLEncoder.encode(vod.optString("channelId"), StandardCharsets.UTF_8)
                + "&vodName=" + URLEncoder.encode(vod.optString("name"), StandardCharsets.UTF_8)));
        assertTrue(details.has("vodInfo"));
        assertFalse(details.getJSONObject("vodInfo").optString("name").isBlank());
    }

    private void assertBookmarksApis() throws Exception {
        HttpTextResponse options = options("/bookmarks");
        assertEquals(204, options.statusCode());

        JSONArray categories = jsonArrayBody(get("/bookmarks?view=categories"));
        assertTrue(categories.length() >= 1);

        Account stalker = AccountService.getInstance().getByName("web-stalker");
        Category liveCategory = CategoryDb.get().getCategories(stalker).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .findFirst()
                .orElseThrow();

        JSONObject createPayload = new JSONObject();
        createPayload.put("accountId", stalker.getDbId());
        createPayload.put("categoryId", liveCategory.getDbId());
        createPayload.put("mode", "itv");
        createPayload.put("channelId", "web-post-1");
        createPayload.put("name", "Posted Bookmark One");
        createPayload.put("cmd", "ffmpeg " + providerBaseUrl + "/live/play/live.php?stream=7001&play_token=pt7001");
        createPayload.put("logo", providerBaseUrl + "/logo/post-1.png");

        JSONObject createRes = jsonObjectBody(postJson("/bookmarks", createPayload.toString()));
        assertEquals("ok", createRes.optString("status"));
        String bookmarkId = createRes.optString("bookmarkId");
        assertFalse(bookmarkId.isBlank());

        JSONArray bookmarks = jsonArrayBody(get("/bookmarks"));
        assertTrue(bookmarks.length() >= 2);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < bookmarks.length(); i++) {
            ids.add(bookmarks.getJSONObject(i).optString("dbId"));
        }
        JSONObject reorderPayload = new JSONObject();
        reorderPayload.put("categoryId", "");
        reorderPayload.put("orderedBookmarkDbIds", new JSONArray(ids.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())));
        JSONObject reorderRes = jsonObjectBody(putJson("/bookmarks", reorderPayload.toString()));
        assertEquals("reordered", reorderRes.optString("action"));

        JSONObject deleteRes = jsonObjectBody(delete("/bookmarks?bookmarkId=" + URLEncoder.encode(bookmarkId, StandardCharsets.UTF_8)));
        assertEquals("removed", deleteRes.optString("action"));
    }

    private void assertPlayerApis() throws Exception {
        Account stalker = AccountService.getInstance().getByName("web-stalker");
        Category liveCategory = CategoryDb.get().getCategories(stalker).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .findFirst()
                .orElseThrow();
        Channel liveChannel = ChannelDb.get().getChannels(liveCategory.getDbId()).get(0);

        JSONObject directPlayer = jsonObjectBody(get("/player?accountId=" + stalker.getDbId()
                + "&categoryId=" + liveCategory.getDbId()
                + "&channelId=" + liveChannel.getDbId()
                + "&mode=itv"));
        assertFalse(directPlayer.optString("url").isBlank());

        Bookmark bookmark = BookmarkService.getInstance().read().get(0);
        JSONObject bookmarkPlayer = jsonObjectBody(get("/player?bookmarkId=" + bookmark.getDbId() + "&mode=itv"));
        assertFalse(bookmarkPlayer.optString("url").isBlank());

        Account m3u = AccountService.getInstance().getByName("web-m3u-local");
        m3u.setAction(itv);
        List<Category> m3uCats = CategoryDb.get().getCategories(m3u);
        Channel drmChannel = null;
        for (Category c : m3uCats) {
            List<Channel> channels = ChannelDb.get().getChannels(c.getDbId());
            for (Channel ch : channels) {
                if (PlayerService.getInstance().isDrmProtected(ch)) {
                    drmChannel = ch;
                    break;
                }
            }
            if (drmChannel != null) break;
        }
        assertNotNull(drmChannel);

        JSONObject drmLaunch = new JSONObject();
        drmLaunch.put("accountId", m3u.getDbId());
        drmLaunch.put("categoryId", m3uCats.get(0).getDbId());
        drmLaunch.put("mode", "itv");
        drmLaunch.put("channel", new JSONObject(drmChannel.toJson()));
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(drmLaunch.toString().getBytes(StandardCharsets.UTF_8));
        HttpTextResponse drmPlayer = get("/player?accountId=" + m3u.getDbId()
                + "&categoryId=" + m3uCats.get(0).getDbId()
                + "&channelId=" + URLEncoder.encode(drmChannel.getChannelId(), StandardCharsets.UTF_8)
                + "&mode=itv"
                + "&name=" + URLEncoder.encode(drmChannel.getName(), StandardCharsets.UTF_8)
                + "&cmd=" + URLEncoder.encode(drmChannel.getCmd(), StandardCharsets.UTF_8)
                + "&drmType=" + URLEncoder.encode(drmChannel.getDrmType(), StandardCharsets.UTF_8)
                + "&clearKeysJson=" + URLEncoder.encode(drmChannel.getClearKeysJson(), StandardCharsets.UTF_8)
                + "&drmLaunch=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8));
        assertEquals(200, drmPlayer.statusCode());
        assertTrue(drmPlayer.body().contains("\"url\""));
    }

    private void assertPlaylistApis() throws Exception {
        Account stalker = AccountService.getInstance().getByName("web-stalker");
        Category liveCategory = CategoryDb.get().getCategories(stalker).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .findFirst()
                .orElseThrow();
        Channel liveChannel = ChannelDb.get().getChannels(liveCategory.getDbId()).get(0);

        HttpTextResponse singlePlaylist = get("/playlist.m3u8?accountId=" + stalker.getDbId()
                + "&categoryId=" + liveCategory.getDbId()
                + "&channelId=" + liveChannel.getDbId());
        assertEquals(200, singlePlaylist.statusCode());
        assertTrue(singlePlaylist.body().contains("#EXTM3U"));

        HttpTextResponse bookmarksM3u = get("/bookmarks.m3u8");
        assertEquals(200, bookmarksM3u.statusCode());
        assertTrue(bookmarksM3u.body().contains("/bookmarkEntry.ts?bookmarkId="));

        String firstBookmarkId = BookmarkService.getInstance().read().get(0).getDbId();
        HttpTextResponse bookmarkEntry = get("/bookmarkEntry.ts?bookmarkId=" + firstBookmarkId);
        assertEquals(200, bookmarkEntry.statusCode());
        assertTrue(bookmarkEntry.body().contains("#EXTM3U"));

        HttpTextResponse iptvM3u8 = get("/iptv.m3u8");
        HttpTextResponse iptvM3u = get("/iptv.m3u");
        assertEquals(200, iptvM3u8.statusCode());
        assertEquals(200, iptvM3u.statusCode());
        assertTrue(iptvM3u8.body().contains("#EXTM3U"));
        assertTrue(iptvM3u.body().contains("#EXTM3U"));
    }

    private void assertWebChannelJsonServerApi() throws Exception {
        HttpServer tempServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            tempServer.createContext("/webchannels", new HttpWebChannelJsonServer());
            tempServer.start();
            int port = tempServer.getAddress().getPort();

            Account stalker = AccountService.getInstance().getByName("web-stalker");
            Category stalkerCategory = CategoryDb.get().getCategories(stalker).stream()
                    .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                    .findFirst()
                    .orElseThrow();
            String stalkerUrl = "http://127.0.0.1:" + port + "/webchannels?accountId=" + stalker.getDbId()
                    + "&mode=itv&categoryId=" + stalkerCategory.getDbId() + "&page=0&pageSize=2&prefetchPages=1";
            JSONObject stalkerJson = jsonObjectBody(getAbsolute(stalkerUrl));
            assertTrue(stalkerJson.has("items"));
            assertTrue(stalkerJson.has("nextPage"));
            assertTrue(stalkerJson.has("hasMore"));
            assertTrue(stalkerJson.has("apiOffset"));

            Account xtreme = AccountService.getInstance().getByName("web-xtreme");
            Category xtremeCategory = CategoryDb.get().getCategories(xtreme).stream()
                    .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                    .findFirst()
                    .orElseThrow();
            String xtremeUrl = "http://127.0.0.1:" + port + "/webchannels?accountId=" + xtreme.getDbId()
                    + "&mode=itv&categoryId=" + xtremeCategory.getDbId() + "&page=0&pageSize=10&prefetchPages=1";
            JSONObject xtremeJson = jsonObjectBody(getAbsolute(xtremeUrl));
            assertTrue(xtremeJson.getJSONArray("items").length() >= 1);
        } finally {
            tempServer.stop(0);
        }
    }

    private HttpTextResponse get(String path) throws Exception {
        return send(appBaseUrl + path, "GET", null, null);
    }

    private HttpTextResponse getAbsolute(String url) throws Exception {
        return send(url, "GET", null, null);
    }

    private HttpTextResponse postJson(String path, String body) throws Exception {
        return send(appBaseUrl + path, "POST", body, "application/json");
    }

    private HttpTextResponse putJson(String path, String body) throws Exception {
        return send(appBaseUrl + path, "PUT", body, "application/json");
    }

    private HttpTextResponse delete(String path) throws Exception {
        return send(appBaseUrl + path, "DELETE", null, null);
    }

    private HttpTextResponse options(String path) throws Exception {
        return send(appBaseUrl + path, "OPTIONS", null, null);
    }

    private HttpTextResponse send(String url, String method, String body, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(20_000);
        conn.setRequestProperty("Accept", "application/json, */*");

        if (contentType != null) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        if (body != null) {
            conn.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
        }

        int status = conn.getResponseCode();
        InputStream responseStream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = "";
        if (responseStream != null) {
            try (InputStream in = responseStream) {
                responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        conn.disconnect();
        return new HttpTextResponse(status, responseBody);
    }

    private JSONArray jsonArrayBody(HttpTextResponse response) {
        assertEquals(200, response.statusCode(), response.body());
        return new JSONArray(response.body());
    }

    private JSONObject jsonObjectBody(HttpTextResponse response) {
        assertEquals(200, response.statusCode(), response.body());
        return new JSONObject(response.body());
    }

    private record HttpTextResponse(int statusCode, String body) {
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String writeM3uPlaylist(String fileName, boolean withDrm) throws IOException {
        String drmBlock = withDrm
                ? """
                #EXTINF:-1 tvg-id="drm-1" tvg-logo="drm.png" group-title="Live",DRM Live
                #KODIPROP:inputstreamaddon=inputstream.adaptive
                #KODIPROP:inputstream.adaptive.manifest_type=mpd
                #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
                #KODIPROP:inputstream.adaptive.license_key={"00112233445566778899aabbccddeeff":"11223344556677889900aabbccddeeff"}
                http://example.com/drm/live.mpd
                """
                : "";

        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="live-1" tvg-logo="live1.png" group-title="Live",Live One
                http://example.com/live/one.ts
                #EXTINF:-1 tvg-id="live-2" tvg-logo="live2.png" group-title="Live",Live Two
                http://example.com/live/two.ts
                """ + drmBlock;

        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toString();
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
            body = """
                    {"js":{"data":[
                      {"id":"1001","name":"Stalker News","number":"1","cmd":"ffmpeg %s/live/play/live.php?stream=1001&play_token=pt1001","cmd_1":"","cmd_2":"","cmd_3":"","logo":"logo1","censored":0,"status":1,"hd":1,"tv_genre_id":"10"},
                      {"id":"2001","name":"Stalker Sports","number":"2","cmd":"ffmpeg %s/live/play/live.php?stream=2001&play_token=pt2001","cmd_1":"","cmd_2":"","cmd_3":"","logo":"logo2","censored":0,"status":1,"hd":1,"tv_genre_id":"20"}
                    ]}}
                    """.formatted(providerBaseUrl, providerBaseUrl);
        } else if ("get_ordered_list".equals(action) && "itv".equals(type)) {
            String genre = q.getOrDefault("genre", "10");
            body = """
                    {"js":{"total_items":1,"max_page_items":999,"data":[
                      {"id":"%s0","name":"Live %s","number":"1","cmd":"ffmpeg %s/live/play/live.php?stream=%s0&play_token=pt%s0","cmd_1":"","cmd_2":"","cmd_3":"","logo":"live","censored":0,"status":1,"hd":1,"tv_genre_id":"%s"}
                    ]}}
                    """.formatted(genre, genre, providerBaseUrl, genre, genre, genre);
        } else if ("get_ordered_list".equals(action) && "vod".equals(type)) {
            String genre = q.getOrDefault("genre", "");
            String page = q.getOrDefault("p", "0");
            if (!"0".equals(page)) {
                body = "{\"js\":{\"total_items\":2,\"max_page_items\":999,\"data\":[]}}";
            } else {
                body = """
                        {"js":{"total_items":2,"max_page_items":999,"data":[
                          {"id":"%s1","name":"Vod %s One","o_name":"Vod %s One","cmd":"ffmpeg %s/play/movie.php?stream=%s1&type=movie","tv_genre_id":"%s","stream_icon":"%s/vod/%s-1.png","censored":0,"status":1,"hd":1},
                          {"id":"%s2","name":"Vod %s Two","o_name":"Vod %s Two","cmd":"ffmpeg %s/play/movie.php?stream=%s2&type=movie","tv_genre_id":"%s","stream_icon":"%s/vod/%s-2.png","censored":0,"status":1,"hd":1}
                        ]}}
                        """.formatted(genre, genre, genre, providerBaseUrl, genre, genre, providerBaseUrl, genre, genre, genre, genre, providerBaseUrl, genre, genre, providerBaseUrl, genre);
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
                        """.formatted(genre, genre, providerBaseUrl, genre, genre, providerBaseUrl, genre);
            }
        } else if ("create_link".equals(action)) {
            body = """
                    {"js":{"cmd":"ffmpeg %s/live/play/live.php?stream=&extension=ts&play_token="}}
                    """.formatted(providerBaseUrl);
        } else {
            body = "{\"js\":{\"data\":[]}}";
        }
        writeResponse(exchange, 200, body, "application/json; charset=utf-8");
    }

    private void handleXtreme(HttpExchange exchange) throws IOException {
        Map<String, String> q = readQuery(exchange);
        String action = q.getOrDefault("action", "");
        String categoryId = q.getOrDefault("category_id", "");
        String seriesId = q.getOrDefault("series_id", "9601");

        String body = switch (action) {
            case "get_live_categories" -> """
                    [
                      {"category_id":"401","category_name":"X Live A"},
                      {"category_id":"402","category_name":"X Live B"}
                    ]
                    """;
            case "get_live_streams" -> """
                    [
                      {"stream_id":"%s1","name":"X Live %s One","stream_icon":"%s/xtreme/live/%s1.png","container_extension":"ts"},
                      {"stream_id":"%s2","name":"X Live %s Two","stream_icon":"%s/xtreme/live/%s2.png","container_extension":"ts"}
                    ]
                    """.formatted(categoryId, categoryId, providerBaseUrl, categoryId, categoryId, categoryId, providerBaseUrl, categoryId);
            case "get_vod_categories" -> """
                    [
                      {"category_id":"501","category_name":"X Vod A"},
                      {"category_id":"502","category_name":"X Vod B"}
                    ]
                    """;
            case "get_vod_streams" -> """
                    [
                      {"stream_id":"%s1","name":"X Vod %s One","stream_icon":"%s/xtreme/vod/%s1.jpg","container_extension":"mp4"},
                      {"stream_id":"%s2","name":"X Vod %s Two","stream_icon":"%s/xtreme/vod/%s2.jpg","container_extension":"mp4"}
                    ]
                    """.formatted(categoryId, categoryId, providerBaseUrl, categoryId, categoryId, categoryId, providerBaseUrl, categoryId);
            case "get_series_categories" -> """
                    [
                      {"category_id":"601","category_name":"X Series A"},
                      {"category_id":"602","category_name":"X Series B"}
                    ]
                    """;
            case "get_series" -> """
                    [
                      {"series_id":"9%s","stream_id":"9%s","name":"X Series %s","cover":"%s/xtreme/series/%s.jpg","container_extension":"mp4"}
                    ]
                    """.formatted(categoryId, categoryId, categoryId, providerBaseUrl, categoryId);
            case "get_series_info" -> """
                    {
                      "info": {"name":"Series %s"},
                      "episodes": {
                        "1": [
                          {"id":"%s1","episode_num":"1","title":"Episode 1","container_extension":"mp4","info":{"movie_image":"%s/ep/1.jpg","plot":"plot 1"}},
                          {"id":"%s2","episode_num":"2","title":"Episode 2","container_extension":"mp4","info":{"movie_image":"%s/ep/2.jpg","plot":"plot 2"}},
                          {"id":"%s3","episode_num":"3","title":"Episode 3","container_extension":"mp4","info":{"movie_image":"%s/ep/3.jpg","plot":"plot 3"}},
                          {"id":"%s4","episode_num":"4","title":"Episode 4","container_extension":"mp4","info":{"movie_image":"%s/ep/4.jpg","plot":"plot 4"}},
                          {"id":"%s5","episode_num":"5","title":"Episode 5","container_extension":"mp4","info":{"movie_image":"%s/ep/5.jpg","plot":"plot 5"}}
                        ]
                      }
                    }
                    """.formatted(seriesId, seriesId, providerBaseUrl, seriesId, providerBaseUrl, seriesId, providerBaseUrl, seriesId, providerBaseUrl, seriesId, providerBaseUrl);
            default -> "[]";
        };

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
                    <item><title>RSS Live One</title><link>%s/rss/stream/one.ts</link></item>
                    <item><title>RSS Live Two</title><link>%s/rss/stream/two.ts</link></item>
                  </channel>
                </rss>
                """.formatted(providerBaseUrl, providerBaseUrl, providerBaseUrl);
        writeResponse(exchange, 200, body, "application/rss+xml; charset=utf-8");
    }

    private void handleM3uPlaylist(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri == null ? "" : uri.getPath();
        String body;
        if (path.endsWith("account-2.m3u")) {
            body = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="m3u2-1" group-title="Live",M3U Two One
                    http://example.com/m3u/two/one.ts
                    #EXTINF:-1 tvg-id="m3u2-2" group-title="Live",M3U Two Two
                    http://example.com/m3u/two/two.ts
                    """;
        } else {
            body = """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="m3u-1" group-title="Live",M3U One
                    http://example.com/m3u/one.ts
                    #EXTINF:-1 tvg-id="m3u-2" group-title="Live",M3U Two
                    http://example.com/m3u/two.ts
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
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
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
