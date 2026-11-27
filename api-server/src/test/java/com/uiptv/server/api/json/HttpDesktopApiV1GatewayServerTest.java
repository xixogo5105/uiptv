package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.SeriesWatchingNowSnapshot;
import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesWatchingNowSnapshotDb;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedTest;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDesktopApiV1GatewayServerTest extends DbBackedTest {
    @Test
    void healthReturnsV1Status() throws Exception {
        StubHttpExchange exchange = new StubHttpExchange("/api/v1/health", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        assertEquals("ok", response.getString("status"));
        assertEquals("v1", response.getString("apiVersion"));
    }

    @Test
    void accountsDoNotLeakPasswordsOrProviderSecrets() throws Exception {
        Account account = new Account(
                "safe-account", "user", "provider-password",
                "http://provider.test", "00:11:22:33:44:55", null, null,
                null, null, null, AccountType.M3U8_URL, null,
                "http://provider.test/list.m3u8", false);
        account.setDbId("local-db-id");
        account.setToken("provider-token");
        AccountService.getInstance().save(account);

        StubHttpExchange exchange = new StubHttpExchange("/api/v1/accounts", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        String body = exchange.getResponseBodyText();
        JSONArray response = new JSONArray(body);
        assertEquals(1, response.length());
        assertEquals("1", response.getJSONObject(0).getString("dbId"));
        assertFalse(body.contains("provider-password"));
        assertFalse(body.contains("provider-token"));
        assertFalse(body.contains("http://provider.test"));
    }

    @Test
    void bulkImportUsesExistingParserAndDoesNotReturnPlaylistCredentials() throws Exception {
        JSONObject request = new JSONObject()
                .put("sourceType", "m3u")
                .put("data", "http://provider.test/get.php?username=user&password=secret&type=m3u")
                .put("groupAccounts", true)
                .put("convertM3uToXtreme", false);
        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/accounts/import", "POST", request.toString());
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(201, exchange.getResponseCode());
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        assertEquals(1, response.getInt("count"));
        assertEquals(1, response.getJSONArray("items").length());
        assertEquals(1, response.getJSONArray("verificationAccountIds").length());
        String body = exchange.getResponseBodyText();
        assertFalse(body.contains("get.php?username=user&password=secret"));
        assertFalse(body.contains("password="));
        assertFalse(body.contains("username"));
        assertFalse(body.contains("secret"));
        assertEquals(1, AccountService.getInstance().getAllById().size());
    }

    @Test
    void bulkImportRejectsUnknownFormatAndEmptyData() throws Exception {
        StubHttpExchange invalidFormat = new StubHttpExchange(
                "/api/v1/accounts/import", "POST",
                new JSONObject().put("sourceType", "custom").put("data", "anything").toString());
        new HttpDesktopApiV1GatewayServer().handle(invalidFormat);
        assertEquals(400, invalidFormat.getResponseCode());

        StubHttpExchange emptyData = new StubHttpExchange(
                "/api/v1/accounts/import", "POST",
                new JSONObject().put("sourceType", "m3u").put("data", " ").toString());
        new HttpDesktopApiV1GatewayServer().handle(emptyData);
        assertEquals(400, emptyData.getResponseCode());
    }

    @Test
    void logsAreRedactedAndAboutReturnsRuntimeMetadata() throws Exception {
        StubHttpExchange clear = new StubHttpExchange("/api/v1/logs", "DELETE");
        new HttpDesktopApiV1GatewayServer().handle(clear);
        assertEquals(200, clear.getResponseCode());

        com.uiptv.util.AppLog.addInfoLog(
                HttpDesktopApiV1GatewayServerTest.class,
                "Request https://provider.test/get.php?username=user&password=secret token=abc");
        StubHttpExchange logs = new StubHttpExchange("/api/v1/logs?source=app", "GET");
        new HttpDesktopApiV1GatewayServer().handle(logs);
        assertEquals(200, logs.getResponseCode());
        String logBody = logs.getResponseBodyText();
        assertTrue(logBody.contains("[redacted]"));
        assertFalse(logBody.contains("username=user"));
        assertFalse(logBody.contains("password=secret"));
        assertFalse(logBody.contains("token=abc"));

        StubHttpExchange about = new StubHttpExchange("/api/v1/about", "GET");
        new HttpDesktopApiV1GatewayServer().handle(about);
        assertEquals(200, about.getResponseCode());
        JSONObject aboutResponse = new JSONObject(about.getResponseBodyText());
        assertFalse(aboutResponse.getString("version").isBlank());
        assertFalse(aboutResponse.getString("javaVersion").isBlank());
        assertEquals("https://github.com/xixogo5105/uiptv", aboutResponse.getString("projectUrl"));
    }

    @Test
    void desktopSettingsSaveValidatedValuesWithoutReturningSecrets() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setTmdbReadAccessToken("private-tmdb-token");
        configuration.setFilterLockHash("private-filter-lock-hash");
        ConfigurationService.getInstance().save(configuration);

        StubHttpExchange read = new StubHttpExchange("/api/v1/settings", "GET");
        new HttpDesktopApiV1GatewayServer().handle(read);
        assertEquals(200, read.getResponseCode());
        JSONObject before = new JSONObject(read.getResponseBodyText());
        assertTrue(before.getBoolean("tmdbTokenConfigured"));
        assertTrue(before.getBoolean("filterLockConfigured"));
        assertEquals("__embedded_player__", before.getString("embeddedPlayerPath"));
        assertEquals("__web_browser_player__", before.getString("webBrowserPlayerPath"));
        assertFalse(read.getResponseBodyText().contains("private-tmdb-token"));
        assertFalse(read.getResponseBodyText().contains("private-filter-lock-hash"));

        JSONObject update = new JSONObject()
                .put("defaultPlayerPath", "__web_browser_player__")
                .put("embeddedPlayer", false)
                .put("themeMode", "dark")
                .put("wideView", true)
                .put("serverPort", "8181")
                .put("uiZoomPercent", "125")
                .put("cacheExpiryDays", "42")
                .put("epgEnabled", true);
        StubHttpExchange save = new StubHttpExchange("/api/v1/settings", "PUT", update.toString());
        new HttpDesktopApiV1GatewayServer().handle(save);

        assertEquals(200, save.getResponseCode());
        JSONObject after = new JSONObject(save.getResponseBodyText());
        assertEquals("__web_browser_player__", after.getString("defaultPlayerPath"));
        assertEquals("dark", after.getString("themeMode"));
        assertEquals("8181", after.getString("serverPort"));
        assertEquals(125, after.getInt("uiZoomPercent"));
        assertEquals(42, after.getInt("cacheExpiryDays"));
        assertTrue(after.getBoolean("restartRequired"));
        assertFalse(save.getResponseBodyText().contains("private-tmdb-token"));
        assertFalse(save.getResponseBodyText().contains("private-filter-lock-hash"));
        Configuration saved = ConfigurationService.getInstance().read();
        assertEquals("dark", saved.resolveThemeMode().persistedValue());
        assertTrue(saved.isEpgEnabled());
        assertFalse(saved.isEmbeddedPlayer());
    }

    @Test
    void desktopSettingsRejectInvalidPortsWithoutChangingStoredValues() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        String oldPort = configuration.getServerPort();
        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/settings", "PUT", new JSONObject().put("serverPort", "70000").toString());
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals(oldPort, ConfigurationService.getInstance().read().getServerPort());
    }

    @Test
    void filterLockRequiresUnlockBeforeReturningOrUpdatingProtectedKeywords() throws Exception {
        StubHttpExchange setPassword = new StubHttpExchange(
                "/api/v1/filter-lock", "POST",
                new JSONObject().put("action", "setPassword").put("newPassword", "safe-pass-1").toString());
        new HttpDesktopApiV1GatewayServer().handle(setPassword);
        assertEquals(200, setPassword.getResponseCode());
        assertFalse(setPassword.getResponseBodyText().contains("safe-pass-1"));
        assertFalse(setPassword.getResponseBodyText().contains("pbkdf2"));

        StubHttpExchange lockedFilters = new StubHttpExchange("/api/v1/filter-lock/filters", "GET");
        new HttpDesktopApiV1GatewayServer().handle(lockedFilters);
        assertEquals(423, lockedFilters.getResponseCode());

        StubHttpExchange wrongPassword = new StubHttpExchange(
                "/api/v1/filter-lock", "POST",
                new JSONObject().put("action", "unlock").put("currentPassword", "wrong-password").toString());
        new HttpDesktopApiV1GatewayServer().handle(wrongPassword);
        assertEquals(401, wrongPassword.getResponseCode());

        StubHttpExchange unlock = new StubHttpExchange(
                "/api/v1/filter-lock", "POST",
                new JSONObject().put("action", "unlock").put("currentPassword", "safe-pass-1").toString());
        new HttpDesktopApiV1GatewayServer().handle(unlock);
        assertEquals(200, unlock.getResponseCode());
        assertTrue(new JSONObject(unlock.getResponseBodyText()).getBoolean("unlocked"));

        StubHttpExchange updateFilters = new StubHttpExchange(
                "/api/v1/filter-lock/filters", "PUT",
                new JSONObject()
                        .put("filterCategoriesList", "adult,xxx")
                        .put("filterChannelsList", "restricted")
                        .put("restrictionsActive", true)
                        .toString());
        new HttpDesktopApiV1GatewayServer().handle(updateFilters);
        assertEquals(200, updateFilters.getResponseCode());
        Configuration configuration = ConfigurationService.getInstance().read();
        assertEquals("adult,xxx", configuration.getFilterCategoriesList());
        assertEquals("restricted", configuration.getFilterChannelsList());
        assertTrue(!configuration.isPauseFiltering());

        StubHttpExchange lock = new StubHttpExchange(
                "/api/v1/filter-lock", "POST", new JSONObject().put("action", "lock").toString());
        new HttpDesktopApiV1GatewayServer().handle(lock);
        assertFalse(new JSONObject(lock.getResponseBodyText()).getBoolean("unlocked"));
    }

    @Test
    void backupArchiveCanBeExportedAndRestoredThroughTheLocalApi() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        String backupPort = "8177";
        configuration.setServerPort(backupPort);
        ConfigurationService.getInstance().save(configuration);

        StubHttpExchange export = new StubHttpExchange("/api/v1/backups/current", "GET");
        new HttpDesktopApiV1GatewayServer().handle(export);
        assertEquals(200, export.getResponseCode());
        assertEquals("application/zip", export.getResponseHeaders().getFirst("Content-Type"));
        byte[] archive = export.getResponseBodyBytes();
        assertTrue(archive.length > 4);
        assertEquals('P', archive[0]);
        assertEquals('K', archive[1]);

        configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("8178");
        ConfigurationService.getInstance().save(configuration);
        StubHttpExchange restore = new StubHttpExchange(
                "/api/v1/backups/current", "POST", archive);
        restore.getRequestHeaders().set("Content-Type", "application/zip");
        new HttpDesktopApiV1GatewayServer().handle(restore);

        assertEquals(200, restore.getResponseCode(), restore.getResponseBodyText());
        assertEquals(backupPort, ConfigurationService.getInstance().read().getServerPort());
        JSONObject report = new JSONObject(restore.getResponseBodyText());
        assertTrue(report.getLong("databaseBytes") > 0);
        assertTrue(report.getLong("archiveBytes") > 0);
    }

    @Test
    void cacheReloadJobSkipsDisabledCacheAccountsWithoutProviderRequests() throws Exception {
        Account account = new Account(
                "disabled-cache-account", "user", "private-password", "https://provider.test",
                null, null, null, null, null, null, AccountType.M3U8_URL, null,
                "https://provider.test/list.m3u8?password=private-password", false);
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);

        StubHttpExchange availableAccounts = new StubHttpExchange(
                "/api/v1/cache-reloads/accounts", "GET");
        new HttpDesktopApiV1GatewayServer().handle(availableAccounts);
        assertEquals(200, availableAccounts.getResponseCode());
        JSONArray accountItems = new JSONArray(availableAccounts.getResponseBodyText());
        assertEquals(1, accountItems.length());
        assertFalse(accountItems.getJSONObject(0).getBoolean("cacheEnabled"));
        assertFalse(availableAccounts.getResponseBodyText().contains("private-password"));

        JSONObject request = new JSONObject()
                .put("accountIds", new JSONArray().put(account.getDbId()))
                .put("failurePolicy", "ask");
        StubHttpExchange start = new StubHttpExchange(
                "/api/v1/cache-reloads", "POST", request.toString());
        new HttpDesktopApiV1GatewayServer().handle(start);

        assertEquals(202, start.getResponseCode());
        String startBody = start.getResponseBodyText();
        assertFalse(startBody.contains("private-password"));
        String jobId = new JSONObject(startBody).getString("jobId");
        StubHttpExchange status = null;
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            status = new StubHttpExchange("/api/v1/cache-reloads/" + jobId, "GET");
            new HttpDesktopApiV1GatewayServer().handle(status);
            if (new JSONObject(status.getResponseBodyText()).getString("status").equals("completed")) {
                break;
            }
            Thread.sleep(10);
        }

        assertTrue(status != null);
        assertEquals(200, status.getResponseCode());
        JSONObject job = new JSONObject(status.getResponseBodyText());
        assertEquals("completed", job.getString("status"));
        assertEquals("skipped", job.getJSONArray("accounts").getJSONObject(0).getString("status"));
        assertFalse(status.getResponseBodyText().contains("private-password"));
        assertFalse(status.getResponseBodyText().contains("provider.test"));
    }

    @Test
    void watchingNowEpisodesUseOpaqueIdsAndKeepProviderCommandsOnTheJavaSide() throws Exception {
        Account account = new Account(
                "episode-account", "user", "private-password", "http://provider.test/xtreme/",
                null, null, null, null, null, null, AccountType.XTREME_API, null,
                "http://provider.test/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("episode-account");
        saved.setAction(Account.AccountAction.series);

        String categoryId = "series-provider-category";
        String categoryDbId = "series-local-category";
        String seriesId = "provider-series-id";
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(saved.getDbId());
        snapshot.setCategoryId(categoryId);
        snapshot.setSeriesId(seriesId);
        snapshot.setCategoryDbId(categoryDbId);
        snapshot.setSeriesTitle("Example Series");
        snapshot.setSeriesPoster("https://images.test/series.png");
        snapshot.setEpisodesJson(new JSONArray()
                .put("{\"channelId\":\"provider-episode-one\",\"name\":\"Episode One\","
                        + "\"cmd\":\"http://provider.test/stream/one\",\"season\":\"1\",\"episodeNum\":\"1\"}")
                .put("{\"channelId\":\"provider-episode-two\",\"name\":\"Episode Two\","
                        + "\"cmd\":\"http://provider.test/stream/two\",\"season\":\"2\",\"episodeNum\":\"1\"}")
                .toString());
        snapshot.setUpdatedAt(123L);
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot);

        String playbackId = DesktopWatchingNowPlaybackStore.getInstance().register(
                new WatchingNowSeriesRow(
                        saved.getDbId(), saved.getAccountName(), AccountType.XTREME_API.name(),
                        categoryId, categoryDbId, seriesId, "provider-episode-one", "Episode One",
                        "1", 1, "Example Series", "https://images.test/series.png", 123L));
        StubHttpExchange episodes = new StubHttpExchange(
                "/api/v1/watching-now/episodes?playbackId=" + playbackId + "&offset=0&limit=2", "GET");
        new HttpDesktopApiV1GatewayServer().handle(episodes);

        assertEquals(200, episodes.getResponseCode());
        JSONObject response = new JSONObject(episodes.getResponseBodyText());
        JSONArray items = response.getJSONArray("items");
        assertEquals(2, items.length());
        assertEquals("Episode One", items.getJSONObject(0).getString("title"));
        assertEquals("1", items.getJSONObject(0).getString("season"));
        assertTrue(items.getJSONObject(0).getString("playbackId").length() > 20);
        assertFalse(episodes.getResponseBodyText().contains("provider-episode-one"));
        assertFalse(episodes.getResponseBodyText().contains("http://provider.test"));
        assertFalse(episodes.getResponseBodyText().contains("cmd"));
    }

    @Test
    void unknownEndpointReturnsJson404() throws Exception {
        StubHttpExchange exchange = new StubHttpExchange("/api/v1/no-such-route", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(404, exchange.getResponseCode());
        assertEquals("application/json", exchange.getResponseHeaders().getFirst("Content-Type").split(";")[0]);
    }

    @Test
    void channelsRejectInvalidModeAndUnboundedPageSize() throws Exception {
        StubHttpExchange invalidMode = new StubHttpExchange(
                "/api/v1/channels?accountId=1&categoryId=2&mode=unknown", "GET");
        new HttpDesktopApiV1GatewayServer().handle(invalidMode);
        assertEquals(400, invalidMode.getResponseCode());

        StubHttpExchange unboundedPage = new StubHttpExchange(
                "/api/v1/channels?accountId=1&categoryId=2&pageSize=201", "GET");
        new HttpDesktopApiV1GatewayServer().handle(unboundedPage);
        assertEquals(400, unboundedPage.getResponseCode());
    }

    @Test
    void categoryRouteOnlyAllowsGet() throws Exception {
        StubHttpExchange exchange = new StubHttpExchange("/api/v1/categories", "POST");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(405, exchange.getResponseCode());
        assertEquals("GET", exchange.getResponseHeaders().getFirst("Allow"));
    }

    @Test
    void categoryRouteReturnsNotFoundForUnknownAccount() throws Exception {
        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/categories?accountId=missing-account&mode=itv", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(404, exchange.getResponseCode());
        assertEquals("Account not found", new JSONObject(exchange.getResponseBodyText()).getString("error"));
    }

    @Test
    void bookmarkListReturnsBoundedSafeDtos() throws Exception {
        Bookmark bookmark = new Bookmark(
                "bookmark-account",
                "News",
                "provider-channel",
                "Station",
                "https://provider.test/live/user/password/stream.m3u8",
                "https://provider.test",
                "provider-category"
        );
        bookmark.setLogo("https://images.test/logo.png");
        BookmarkService.getInstance().save(bookmark);

        StubHttpExchange exchange = new StubHttpExchange("/api/v1/bookmarks?offset=0&limit=10", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        String body = exchange.getResponseBodyText();
        JSONObject response = new JSONObject(body);
        JSONArray items = response.getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals("Station", items.getJSONObject(0).getString("channelName"));
        assertFalse(body.contains("provider-channel"));
        assertFalse(body.contains("password"));
        assertFalse(body.contains("https://provider.test"));
    }

    @Test
    void bookmarkCategoriesReturnsPersistedSafeCategoryValues() throws Exception {
        BookmarkService.getInstance().addCategory(new BookmarkCategory(null, "Sports"));

        StubHttpExchange exchange = new StubHttpExchange("/api/v1/bookmark-categories", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray categories = new JSONArray(exchange.getResponseBodyText());
        assertEquals(1, categories.length());
        assertFalse(categories.getJSONObject(0).getString("id").isBlank());
        assertEquals("Sports", categories.getJSONObject(0).getString("name"));
    }

    @Test
    void bookmarkCategoriesCanBeAddedAndRemoved() throws Exception {
        StubHttpExchange create = new StubHttpExchange(
                "/api/v1/bookmark-categories", "POST", new JSONObject().put("name", "Cricket").toString());
        new HttpDesktopApiV1GatewayServer().handle(create);
        assertEquals(201, create.getResponseCode());

        StubHttpExchange list = new StubHttpExchange("/api/v1/bookmark-categories", "GET");
        new HttpDesktopApiV1GatewayServer().handle(list);
        JSONArray categories = new JSONArray(list.getResponseBodyText());
        assertEquals(1, categories.length());
        String categoryId = categories.getJSONObject(0).getString("id");

        StubHttpExchange delete = new StubHttpExchange(
                "/api/v1/bookmark-categories?categoryId=" + categoryId, "DELETE");
        new HttpDesktopApiV1GatewayServer().handle(delete);
        assertEquals(200, delete.getResponseCode());
        assertEquals(0, BookmarkService.getInstance().getAllCategories().size());
    }

    @Test
    void translationsReturnLocalizedAllowlistedKeysOnly() throws Exception {
        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/translations?keys=bookmarkRecent,autoManageCategories", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONObject translations = new JSONObject(exchange.getResponseBodyText());
        assertEquals("Recent", translations.getString("bookmarkRecent"));
        assertEquals("Manage Categories", translations.getString("autoManageCategories"));

        StubHttpExchange unsupported = new StubHttpExchange(
                "/api/v1/translations?keys=missingKey", "GET");
        new HttpDesktopApiV1GatewayServer().handle(unsupported);
        assertEquals(400, unsupported.getResponseCode());
    }

    @Test
    void bookmarkListCanFilterBySavedBookmarkCategoryId() throws Exception {
        Bookmark selected = new Bookmark(
                "bookmark-account", "News", "provider-channel", "Station", "cmd", "", "provider-category");
        selected.setCategoryId("saved-category");
        BookmarkService.getInstance().save(selected);
        Bookmark other = new Bookmark(
                "bookmark-account", "Sports", "other-channel", "Other", "cmd", "", "provider-category");
        other.setCategoryId("other-saved-category");
        BookmarkService.getInstance().save(other);

        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/bookmarks?offset=0&limit=10&categoryId=saved-category", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONArray items = new JSONObject(exchange.getResponseBodyText()).getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals("saved-category", items.getJSONObject(0).getString("categoryId"));
        assertEquals("Station", items.getJSONObject(0).getString("channelName"));
    }

    @Test
    void recentBookmarksAreSeparateFromSavedBookmarksAndCanBeCleared() throws Exception {
        Bookmark recent = new Bookmark(
                "recent-account", "News", "recent-provider-channel", "Recent station",
                "https://provider.test/secret/stream", "https://provider.test", "provider-category");
        BookmarkService.getInstance().recordRecentPlayback(recent);

        StubHttpExchange list = new StubHttpExchange("/api/v1/bookmarks/recent", "GET");
        new HttpDesktopApiV1GatewayServer().handle(list);

        assertEquals(200, list.getResponseCode());
        JSONObject response = new JSONObject(list.getResponseBodyText());
        JSONArray items = response.getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals("Recent station", items.getJSONObject(0).getString("channelName"));
        assertTrue(items.getJSONObject(0).getString("dbId").startsWith("recent:"));
        assertFalse(list.getResponseBodyText().contains("secret"));
        assertFalse(list.getResponseBodyText().contains("provider.test"));

        StubHttpExchange clear = new StubHttpExchange("/api/v1/bookmarks/recent", "DELETE");
        new HttpDesktopApiV1GatewayServer().handle(clear);

        assertEquals(200, clear.getResponseCode());
        assertTrue(BookmarkService.getInstance().readRecentBookmarks().isEmpty());
    }

    @Test
    void bookmarkDeleteRequiresAnExistingId() throws Exception {
        StubHttpExchange missingParameter = new StubHttpExchange("/api/v1/bookmarks", "DELETE");
        new HttpDesktopApiV1GatewayServer().handle(missingParameter);
        assertEquals(400, missingParameter.getResponseCode());

        StubHttpExchange missingBookmark = new StubHttpExchange(
                "/api/v1/bookmarks?bookmarkId=missing", "DELETE");
        new HttpDesktopApiV1GatewayServer().handle(missingBookmark);
        assertEquals(404, missingBookmark.getResponseCode());
    }

    @Test
    void bookmarkCreationResolvesCachedRowsFromLocalIds() throws Exception {
        Account account = new Account(
                "cached-live", "user", "pass", "http://provider.test", null, null, null,
                null, null, null, AccountType.XTREME_API, null, "http://provider.test", false);
        account.setAction(Account.AccountAction.itv);
        account.setCacheEnabled(true);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getByName("cached-live");
        account.setAction(Account.AccountAction.itv);
        account.setCacheEnabled(true);
        AccountService.getInstance().save(account);
        CategoryDb.get().saveAll(
                java.util.List.of(new Category("provider-category", "News", "news", false, 0)), account);
        Category category = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(
                java.util.List.of(new Channel("provider-channel", "Station", "1",
                        "https://provider.test/secret/stream.m3u8", null, null, null, "logo",
                        0, 1, 1, null, null, null, null, null)),
                category.getDbId(), account);
        Channel channel = ChannelDb.get().getChannels(category.getDbId()).getFirst();
        StubHttpExchange exchange = new StubHttpExchange(
                "/api/v1/bookmarks",
                "POST",
                new JSONObject()
                        .put("accountId", account.getDbId())
                        .put("categoryId", category.getDbId())
                        .put("channelId", channel.getDbId())
                        .toString());

        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JSONObject response = new JSONObject(exchange.getResponseBodyText());
        assertEquals("saved", response.getString("status"));
        assertFalse(response.getString("bookmarkId").isBlank());
        String body = exchange.getResponseBodyText();
        assertFalse(body.contains("provider-channel"));
        assertFalse(body.contains("secret"));
    }

    @Test
    void watchingNowReturnsSafeSeriesSummariesAndBoundsPagination() throws Exception {
        Account account = new Account(
                "watch-state", "user", "secret-password", "http://provider.test", null, null, null,
                null, null, null, AccountType.XTREME_API, null, "http://provider.test", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        account = AccountService.getInstance().getByName("watch-state");
        account.setAction(Account.AccountAction.series);
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account, "private-category-id", "private-series-id", "private-episode-id",
                "Episode One", "1", "1");

        StubHttpExchange exchange = new StubHttpExchange("/api/v1/watching-now", "GET");
        new HttpDesktopApiV1GatewayServer().handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        String body = exchange.getResponseBodyText();
        JSONObject response = new JSONObject(body);
        assertEquals("Episode One", response.getJSONArray("series").getJSONObject(0).getString("episodeName"));
        assertFalse(response.getJSONArray("series").getJSONObject(0).getString("playbackId").isBlank());
        assertFalse(body.contains("private-series-id"));
        assertFalse(body.contains("private-episode-id"));
        assertFalse(body.contains("private-category-id"));
        assertFalse(body.contains("secret-password"));

        StubHttpExchange invalidLimit = new StubHttpExchange("/api/v1/watching-now?limit=201", "GET");
        new HttpDesktopApiV1GatewayServer().handle(invalidLimit);
        assertEquals(400, invalidLimit.getResponseCode());
    }

    @Test
    void watchingNowEpisodesRejectsMissingOrExpiredOpaqueReferences() throws Exception {
        StubHttpExchange missingPlaybackId = new StubHttpExchange(
                "/api/v1/watching-now/episodes", "GET");
        new HttpDesktopApiV1GatewayServer().handle(missingPlaybackId);
        assertEquals(400, missingPlaybackId.getResponseCode());

        StubHttpExchange expiredPlaybackId = new StubHttpExchange(
                "/api/v1/watching-now/episodes?playbackId=unknown", "GET");
        new HttpDesktopApiV1GatewayServer().handle(expiredPlaybackId);
        assertEquals(404, expiredPlaybackId.getResponseCode());
    }

    private static final class StubHttpExchange extends HttpExchange {
        private final URI uri;
        private final String method;
        private final String requestBody;
        private final byte[] requestBodyBytes;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        private StubHttpExchange(String uri, String method) {
            this(uri, method, "");
        }

        private StubHttpExchange(String uri, String method, String requestBody) {
            this.uri = URI.create(uri);
            this.method = method;
            this.requestBody = requestBody;
            this.requestBodyBytes = null;
        }

        private StubHttpExchange(String uri, String method, byte[] requestBodyBytes) {
            this.uri = URI.create(uri);
            this.method = method;
            this.requestBody = "";
            this.requestBodyBytes = requestBodyBytes.clone();
        }

        private String getResponseBodyText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        private byte[] getResponseBodyBytes() {
            return responseBody.toByteArray();
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() {}
        @Override public InputStream getRequestBody() {
            return requestBodyBytes == null
                    ? new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
                    : new ByteArrayInputStream(requestBodyBytes);
        }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long rLength) throws IOException { responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override public int getResponseCode() { return responseCode; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8888); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(InputStream i, OutputStream o) {}
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
