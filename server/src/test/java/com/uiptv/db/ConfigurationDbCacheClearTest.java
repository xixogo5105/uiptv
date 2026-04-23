package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationDbCacheClearTest extends DbBackedTest {

    @Test
    void clearCache_clearsOnlyTargetAccount_forAllCacheTablesIncludingVodAndSeries() throws Exception {
        Account first = createAccount("clear-cache-first");
        Account second = createAccount("clear-cache-second");
        seedAllCacheTables(first, "a");
        seedAllCacheTables(second, "b");

        CacheService cacheService = new CacheServiceImpl();
        cacheService.clearCache(first);

        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.CATEGORY_TABLE, first.getDbId()));
        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.CHANNEL_TABLE, first.getDbId()));
        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE, first.getDbId()));
        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE, first.getDbId()));
        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE, first.getDbId()));
        assertEquals(0, countRowsForAccount(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE, first.getDbId()));

        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.CATEGORY_TABLE, second.getDbId()));
        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.CHANNEL_TABLE, second.getDbId()));
        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE, second.getDbId()));
        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE, second.getDbId()));
        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE, second.getDbId()));
        assertEquals(1, countRowsForAccount(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE, second.getDbId()));

        Account firstAfterClear = AccountService.getInstance().getById(first.getDbId());
        Account secondAfterClear = AccountService.getInstance().getById(second.getDbId());
        assertTrue(firstAfterClear.getServerPortalUrl() == null || firstAfterClear.getServerPortalUrl().isEmpty());
        assertFalse(secondAfterClear.getServerPortalUrl() == null || secondAfterClear.getServerPortalUrl().isEmpty());
    }

    @Test
    void clearAllCache_clearsAllCacheTablesIncludingVodAndSeries() throws Exception {
        Account first = createAccount("clear-all-first");
        Account second = createAccount("clear-all-second");
        seedAllCacheTables(first, "a");
        seedAllCacheTables(second, "b");

        CacheService cacheService = new CacheServiceImpl();
        cacheService.clearAllCache();

        assertEquals(0, countTableRows(DatabaseUtils.DbTable.CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.CHANNEL_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE));
        assertEquals(0, countTableRows(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE));

        Account firstAfterClear = AccountService.getInstance().getById(first.getDbId());
        Account secondAfterClear = AccountService.getInstance().getById(second.getDbId());
        assertTrue(firstAfterClear.getServerPortalUrl() == null || firstAfterClear.getServerPortalUrl().isEmpty());
        assertTrue(secondAfterClear.getServerPortalUrl() == null || secondAfterClear.getServerPortalUrl().isEmpty());
    }

    private Account createAccount(String name) {
        Account account = new Account(
                name,
                "user",
                "pass",
                "http://portal.example/",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        account.setServerPortalUrl("http://portal.example/server");
        account.setAction(itv);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private void seedAllCacheTables(Account account, String suffix) {
        account.setAction(itv);
        CategoryDb.get().saveAll(List.of(new Category("live-" + suffix, "Live " + suffix, "Live " + suffix, false, 0)), account);
        Category liveCategory = CategoryDb.get().getCategories(account).get(0);
        ChannelDb.get().saveAll(
                List.of(new Channel("live-ch-" + suffix, "Live Channel " + suffix, "1", "ffmpeg http://live/" + suffix, null, null, null, "live.png", 0, 1, 1, null, null, null, null, null)),
                liveCategory.getDbId(),
                account
        );

        account.setAction(vod);
        VodCategoryDb.get().saveAll(List.of(new Category("vod-" + suffix, "Vod " + suffix, "Vod " + suffix, false, 0)), account);
        Category vodCategory = VodCategoryDb.get().getCategories(account).get(0);
        VodChannelDb.get().saveAll(
                List.of(new Channel("vod-ch-" + suffix, "Vod Channel " + suffix, "1", "ffmpeg http://vod/" + suffix, null, null, null, "vod.png", 0, 1, 1, null, null, null, null, null)),
                vodCategory.getDbId(),
                account
        );

        account.setAction(series);
        SeriesCategoryDb.get().saveAll(List.of(new Category("series-" + suffix, "Series " + suffix, "Series " + suffix, false, 0)), account);
        Category seriesCategory = SeriesCategoryDb.get().getCategories(account).get(0);
        SeriesChannelDb.get().saveAll(
                List.of(new Channel("series-ch-" + suffix, "Series Channel " + suffix, "1", "ffmpeg http://series/" + suffix, null, null, null, "series.png", 0, 1, 1, null, null, null, null, null)),
                seriesCategory.getDbId(),
                account
        );
    }

    private int countTableRows(DatabaseUtils.DbTable table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table.getTableName();
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int countRowsForAccount(DatabaseUtils.DbTable table, String accountId) throws SQLException {
        if (table == DatabaseUtils.DbTable.CHANNEL_TABLE) {
            String sql = "SELECT COUNT(*) FROM " + DatabaseUtils.DbTable.CHANNEL_TABLE.getTableName()
                    + " c JOIN " + DatabaseUtils.DbTable.CATEGORY_TABLE.getTableName()
                    + " cat ON c.categoryId = cat.id WHERE cat.accountId = ?";
            try (Connection conn = SQLConnection.connect();
                 PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, accountId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }

        String sql = "SELECT COUNT(*) FROM " + table.getTableName() + " WHERE accountId = ?";
        try (Connection conn = SQLConnection.connect();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
