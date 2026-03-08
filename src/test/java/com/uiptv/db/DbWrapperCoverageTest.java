package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbWrapperCoverageTest extends DbBackedTest {

    @Test
    void accountDb_saveServerPortalUrl_ignoresUnsavedAccounts_andPersistsSavedOnes() {
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
}
