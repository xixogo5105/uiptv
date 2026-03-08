package com.uiptv.db;

import com.uiptv.api.JsonCompliant;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbWrapperCoverageTest extends DbBackedTest {

    @Test
    void accountDb_saveServerPortalUrl_ignoresUnsavedAccounts_andPersistsSavedOnes() {
        assertNotNull(AccountDb.get());
        assertNotNull(CategoryDb.get());
        Account unsaved = new Account("account-db-unsaved", "user", "pass", "http://portal.test", null, null, null, null, null, null, AccountType.STALKER_PORTAL, null, null, false);
        unsaved.setServerPortalUrl("http://portal.test/c/");

        AccountDb.get().saveServerPortalUrl(unsaved);

        assertNull(AccountDb.get().getAccountByName("account-db-unsaved"));

        Account saved = new Account("account-db-saved", "user", "pass", "http://portal.test", null, null, null, null, null, null, AccountType.STALKER_PORTAL, null, null, false);
        AccountService.getInstance().save(saved);
        saved = AccountService.getInstance().getByName("account-db-saved");
        saved.setServerPortalUrl("http://portal.test/server/load.php");

        AccountDb.get().saveServerPortalUrl(saved);

        Account reloaded = AccountDb.get().getAccountById(saved.getDbId());
        assertEquals("http://portal.test/server/load.php", reloaded.getServerPortalUrl());
    }

    @Test
    void categoryDb_lookupHelpers_returnOnlyRowsForTheMatchingAccount() {
        Account accountA = persistAccount("category-db-a");
        Account accountB = persistAccount("category-db-b");

        CategoryDb.get().saveAll(List.of(
                new Category("news", "News", "news", false, 0),
                new Category("sports", "Sports", "sports", false, 0)
        ), accountA);
        CategoryDb.get().saveAll(List.of(
                new Category("movies", "Movies", "movies", false, 0)
        ), accountB);

        List<Category> accountACategories = CategoryDb.get().getAllAccountCategories(accountA.getDbId());
        assertEquals(2, accountACategories.size());

        Category sports = accountACategories.stream()
                .filter(category -> "Sports".equals(category.getTitle()))
                .findFirst()
                .orElseThrow();
        assertNotNull(CategoryDb.get().getCategoryByDbId(sports.getDbId(), accountA));
        assertNull(CategoryDb.get().getCategoryByDbId(sports.getDbId(), accountB));
    }

    @Test
    void seriesWatchStateDb_clearHelpers_removeScopedRows() {
        Account account = persistSeriesAccount("watch-state-db");

        SeriesWatchState stateA = state(account.getDbId(), "cat-a", "series-1", "ep-1");
        SeriesWatchState stateB = state(account.getDbId(), "cat-b", "series-2", "ep-2");
        SeriesWatchStateDb.get().upsert(stateA);
        SeriesWatchStateDb.get().upsert(stateB);

        assertEquals(2, SeriesWatchStateDb.get().getByAccount(account.getDbId()).size());
        assertEquals(1, SeriesWatchStateDb.get().getByAccount(account.getDbId(), "cat-a").size());

        SeriesWatchStateDb.get().clear(account.getDbId(), "cat-a", "series-1");
        assertNull(SeriesWatchStateDb.get().getBySeries(account.getDbId(), "cat-a", "series-1"));
        assertEquals(1, SeriesWatchStateDb.get().getBySeries(account.getDbId(), "series-2").size());

        SeriesWatchStateDb.get().clearAllSeries();
        assertTrue(SeriesWatchStateDb.get().getByAccount(account.getDbId()).isEmpty());

        SeriesWatchStateDb.get().upsert(state(account.getDbId(), "cat-c", "series-3", "ep-3"));
        SeriesWatchStateDb.get().deleteByAccount(account.getDbId());
        assertTrue(SeriesWatchStateDb.get().getByAccount(account.getDbId()).isEmpty());
    }

    @Test
    void databasePatchesUtils_resourceProbesAndBaselineWork() throws Exception {
        assertTrue(DatabasePatchesUtils.hasMigrationsListResource());
        assertTrue(DatabasePatchesUtils.hasBaselineResource());

        try (Connection conn = SQLConnection.connect(); Statement statement = conn.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS schema_migrations");
            statement.executeUpdate("DROP TABLE IF EXISTS Account");

            DatabasePatchesUtils.applyBaseline(conn);

            try (ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='Account'")) {
                assertTrue(resultSet.next());
            }
        }
    }

    @Test
    void baseDb_helpersHandleBlankNumbersAndSqlExceptions() throws Exception {
        TestBaseDb baseDb = new TestBaseDb();
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        Mockito.when(resultSet.getString("value")).thenReturn("text");
        Mockito.when(resultSet.getString("blankInt")).thenReturn("");
        Mockito.when(resultSet.getString("intVal")).thenReturn("7");
        Mockito.when(resultSet.getString("boolBlank")).thenReturn("");
        Mockito.when(resultSet.getString("boolOne")).thenReturn("1");
        Mockito.when(resultSet.getString("missing")).thenThrow(new SQLException("missing"));

        assertEquals("text", baseDb.nullSafeString(resultSet, "value"));
        assertNull(baseDb.nullSafeString(resultSet, "missing"));
        assertEquals(0, BaseDb.safeInteger(resultSet, "blankInt"));
        assertEquals(7, BaseDb.safeInteger(resultSet, "intVal"));
        assertEquals(0, BaseDb.safeInteger(resultSet, "missing"));
        assertFalse(BaseDb.safeBoolean(resultSet, "boolBlank"));
        assertTrue(BaseDb.safeBoolean(resultSet, "boolOne"));
        assertFalse(BaseDb.safeBoolean(resultSet, "missing"));
    }

    @Test
    void sqlConnection_applySchemaPrefersMigrationsThenBaselineAndFailsWhenMissing() throws Exception {
        Method applySchema = SQLConnection.class.getDeclaredMethod("applySchema", Connection.class);
        applySchema.setAccessible(true);
        Connection connection = Mockito.mock(Connection.class);

        try (MockedStatic<DatabasePatchesUtils> patches = Mockito.mockStatic(DatabasePatchesUtils.class)) {
            patches.when(DatabasePatchesUtils::hasMigrationsListResource).thenReturn(true);
            assertDoesNotThrow(() -> applySchema.invoke(null, connection));
            patches.verify(() -> DatabasePatchesUtils.applyPatches(connection));
        }

        try (MockedStatic<DatabasePatchesUtils> patches = Mockito.mockStatic(DatabasePatchesUtils.class)) {
            patches.when(DatabasePatchesUtils::hasMigrationsListResource).thenReturn(false);
            patches.when(DatabasePatchesUtils::hasBaselineResource).thenReturn(true);
            assertDoesNotThrow(() -> applySchema.invoke(null, connection));
            patches.verify(() -> DatabasePatchesUtils.applyBaseline(connection));
        }

        try (MockedStatic<DatabasePatchesUtils> patches = Mockito.mockStatic(DatabasePatchesUtils.class)) {
            patches.when(DatabasePatchesUtils::hasMigrationsListResource).thenReturn(false);
            patches.when(DatabasePatchesUtils::hasBaselineResource).thenReturn(false);
            Exception ex = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> applySchema.invoke(null, connection));
            assertTrue(ex.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    void sqlConnection_helpers_detectBusyState_andInterruptedSleep() throws Exception {
        Method isBusy = SQLConnection.class.getDeclaredMethod("isBusy", SQLException.class);
        isBusy.setAccessible(true);
        SQLException busy = new SQLException("SQLITE_BUSY: database is locked", "state", 5);
        SQLException wrapped = new SQLException("wrapper");
        wrapped.setNextException(busy);
        assertTrue((Boolean) isBusy.invoke(null, busy));
        assertTrue((Boolean) isBusy.invoke(null, wrapped));
        assertFalse((Boolean) isBusy.invoke(null, new SQLException("other failure", "state", 1)));

        Method sleepBeforeRetry = SQLConnection.class.getDeclaredMethod("sleepBeforeRetry");
        sleepBeforeRetry.setAccessible(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                sleepBeforeRetry.invoke(null);
            } catch (Throwable throwable) {
                failure.set(throwable.getCause());
            }
        });
        thread.start();
        thread.join();
        assertTrue(failure.get() instanceof DatabaseAccessException);
    }

    @Test
    void databasePatchesUtils_privateHelpers_coverDirectiveParsingAndFallbacks() throws Exception {
        Method findDirectiveLine = DatabasePatchesUtils.class.getDeclaredMethod("findDirectiveLine", String.class);
        findDirectiveLine.setAccessible(true);
        assertEquals("--@add_column Account testColumn TEXT", findDirectiveLine.invoke(null, "-- note\n--@add_column Account testColumn TEXT\nSELECT 1;"));
        assertNull(findDirectiveLine.invoke(null, "SELECT 1;"));

        Method safeError = DatabasePatchesUtils.class.getDeclaredMethod("safeError", Exception.class);
        safeError.setAccessible(true);
        assertEquals("IllegalArgumentException", safeError.invoke(null, new IllegalArgumentException()));
        assertEquals(1000, safeError.invoke(null, new Exception("x".repeat(1200))).toString().length());

        Method resourceExists = DatabasePatchesUtils.class.getDeclaredMethod("resourceExists", String.class);
        resourceExists.setAccessible(true);
        assertFalse((Boolean) resourceExists.invoke(null, "db/migrations/does-not-exist.sql"));

        Method executeDirective = DatabasePatchesUtils.class.getDeclaredMethod("executeDirective", Connection.class, String.class);
        executeDirective.setAccessible(true);
        try (Connection conn = SQLConnection.connect(); Statement statement = conn.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS temp_patch_table(id INTEGER PRIMARY KEY)");
            assertDoesNotThrow(() -> executeDirective.invoke(null, conn, "--@add_column temp_patch_table patchCol TEXT"));
            assertDoesNotThrow(() -> executeDirective.invoke(null, conn, "--@drop_column temp_patch_table patchCol"));
            Exception invalid = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> executeDirective.invoke(null, conn, "--@bogus temp_patch_table patchCol"));
            assertTrue(invalid.getCause() instanceof SQLException);
        }
    }

    @Test
    void vodAndSeriesCaches_reportFreshnessAndSupportDeleteLookups() {
        Account vodAccount = persistActionAccount("vod-db", vod);
        Account seriesAccount = persistActionAccount("series-db", series);

        Category vodCategory = new Category("vod-1", "Movies", "movies", false, 0);
        vodCategory.setExtraJson("{\"kind\":\"vod\"}");
        VodCategoryDb.get().saveAll(List.of(vodCategory), vodAccount);
        assertTrue(VodCategoryDb.get().isFresh(vodAccount, 60_000));
        assertFalse(VodCategoryDb.get().isFresh(vodAccount, 0));

        Category seriesCategory = new Category("series-1", "Shows", "shows", false, 0);
        seriesCategory.setExtraJson("{\"kind\":\"series\"}");
        SeriesCategoryDb.get().saveAll(List.of(seriesCategory), seriesAccount);
        assertTrue(SeriesCategoryDb.get().isFresh(seriesAccount, 60_000));
        assertFalse(SeriesCategoryDb.get().isFresh(seriesAccount, 0));

        Category savedVodCategory = VodCategoryDb.get().getCategories(vodAccount).get(0);
        Category savedSeriesCategory = SeriesCategoryDb.get().getCategories(seriesAccount).get(0);
        assertEquals("{\"kind\":\"vod\"}", savedVodCategory.getExtraJson());
        assertEquals("{\"kind\":\"series\"}", savedSeriesCategory.getExtraJson());

        VodCategoryDb.get().deleteByAccount(vodAccount.getDbId());
        SeriesCategoryDb.get().deleteByAccount(seriesAccount.getDbId());
        assertTrue(VodCategoryDb.get().getCategories(vodAccount).isEmpty());
        assertTrue(SeriesCategoryDb.get().getCategories(seriesAccount).isEmpty());
    }

    @Test
    void vodAndSeriesChannelWrappers_coverFreshnessLookupsAndDeletes() {
        Account vodAccount = persistActionAccount("vod-ch-db", vod);
        Account seriesAccount = persistActionAccount("series-ch-db", series);

        VodChannelDb.get().saveAll(List.of(channel("vod-chan-1", "Movie One")), "vod-cat", vodAccount);
        assertNotNull(VodChannelDb.get().getChannelByChannelId("vod-chan-1", "vod-cat", vodAccount.getDbId()));
        assertTrue(VodChannelDb.get().isFresh(vodAccount, "vod-cat", 60_000));
        assertFalse(VodChannelDb.get().isFresh(vodAccount, "vod-cat", 0));

        SeriesChannelDb.get().saveAll(List.of(
                channel("series-chan-1", "Series One"),
                channel("series-chan-1", "Series One Duplicate"),
                channel("series-chan-2", "Series Two")
        ), "series-cat", seriesAccount);
        assertEquals(3, SeriesChannelDb.get().getChannelsBySeriesIds(seriesAccount, java.util.Arrays.asList("series-chan-1", "series-chan-1", "series-chan-2", "", null)).size());
        assertTrue(SeriesChannelDb.get().getChannelsBySeriesIds(seriesAccount, List.of()).isEmpty());
        assertTrue(SeriesChannelDb.get().isFresh(seriesAccount, "series-cat", 60_000));
        assertFalse(SeriesChannelDb.get().isFresh(seriesAccount, "series-cat", 0));

        VodChannelDb.get().deleteByAccount(vodAccount.getDbId());
        SeriesChannelDb.get().deleteByAccount(seriesAccount.getDbId());
        assertTrue(VodChannelDb.get().getChannels(vodAccount, "vod-cat").isEmpty());
        assertTrue(SeriesChannelDb.get().getChannels(seriesAccount, "series-cat").isEmpty());
    }

    @Test
    void seriesEpisodeDb_usesFreshestCategoryAndSupportsAccountDelete() {
        Account account = persistSeriesAccount("series-episodes-db");

        Channel older = channel("ep-1", "Episode 1");
        older.setSeason("1");
        older.setEpisodeNum("1");
        SeriesEpisodeDb.get().saveAll(account, "cat-old", "series-100", List.of(older));

        Channel newer = channel("ep-2", "Episode 2");
        newer.setSeason("1");
        newer.setEpisodeNum("2");
        SeriesEpisodeDb.get().saveAll(account, null, "series-100", List.of(newer));

        assertTrue(SeriesEpisodeDb.get().isFresh(account, "", "series-100", 60_000));
        assertFalse(SeriesEpisodeDb.get().isFresh(account, "", "series-100", 0));
        assertTrue(SeriesEpisodeDb.get().isFreshInAnyCategory(account, "series-100", 60_000));
        assertEquals("ep-2", SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, "series-100").get(0).getChannelId());
        assertTrue(SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, "missing-series").isEmpty());
        SeriesEpisodeDb.get().deleteByAccount(account.getDbId());
        assertTrue(SeriesEpisodeDb.get().getEpisodes(account, "", "series-100").isEmpty());
    }

    private Account persistAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.M3U8_URL, null, "http://127.0.0.1/mock/list.m3u", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private Account persistSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://127.0.0.1/mock", false);
        account.setAction(series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(series);
        return saved;
    }

    private Account persistActionAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://127.0.0.1/mock", false);
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        return saved;
    }

    private Channel channel(String id, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id);
        return channel;
    }

    private SeriesWatchState state(String accountId, String categoryId, String seriesId, String episodeId) {
        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(accountId);
        state.setMode(series.name());
        state.setCategoryId(categoryId);
        state.setSeriesId(seriesId);
        state.setEpisodeId(episodeId);
        state.setEpisodeName("Episode " + episodeId);
        state.setSeason("1");
        state.setEpisodeNum(1);
        state.setUpdatedAt(1234L);
        state.setSource("TEST");
        state.setSeriesCategorySnapshot("{\"id\":\"" + categoryId + "\"}");
        state.setSeriesChannelSnapshot("{\"id\":\"" + seriesId + "\"}");
        state.setSeriesEpisodeSnapshot("{\"id\":\"" + episodeId + "\"}");
        return state;
    }

    private static final class TestBaseDb extends BaseDb {
        private TestBaseDb() {
            super(DatabaseUtils.DbTable.ACCOUNT_TABLE);
        }

        @Override
        JsonCompliant populate(ResultSet resultSet) {
            return null;
        }
    }
}
