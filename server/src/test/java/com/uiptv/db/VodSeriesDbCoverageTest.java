package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class VodSeriesDbCoverageTest extends DbBackedTest {

    private static Account persistAccount(String name, com.uiptv.model.Account.AccountAction action) {
        AccountDb accountDb = AccountDb.get();
        Account account = new Account(name, "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, null, false);
        account.setAction(action);
        accountDb.save(account);
        Account saved = accountDb.getAccountByName(name);
        saved.setAction(action);
        return saved;
    }

    @Test
    void vodCategoryDb_crudFreshnessAndErrorCoverage() throws Exception {
        Account account = persistAccount("vod-account", vod);
        VodCategoryDb db = VodCategoryDb.get();

        Category category = new Category("vod-1", "Vod", "vod", true, 7);
        category.setExtraJson("{\"x\":1}");
        db.saveAll(List.of(category), account);

        List<Category> categories = db.getCategories(account);
        assertEquals(1, categories.size());
        Category stored = categories.get(0);
        assertEquals(account.getDbId(), stored.getAccountId());
        assertEquals(account.getAction().name(), stored.getAccountType());
        assertEquals("{\"x\":1}", stored.getExtraJson());

        assertTrue(db.isFresh(account, 60_000));

        try (Connection conn = SqlConnectionRuntime.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE VodCategory SET cachedAt=0 WHERE accountId='" + account.getDbId() + "'");
        }
        assertFalse(db.isFresh(account, 1));

        db.deleteByAccount(account.getDbId());
        assertTrue(db.getCategories(account).isEmpty());

        db.saveAll(List.of(new Category("vod-2", "Vod2", "vod2", false, 0)), account);
        assertEquals(1, db.getCategories(account).size());
    }

    @Test
    void vodChannelDb_crudFreshnessAndErrorCoverage() throws Exception {
        Account account = persistAccount("vod-ch-account", vod);
        VodChannelDb db = VodChannelDb.get();

        String categoryId = "vod-cat";
        Channel channel = new Channel("ch-1", "Vod Channel", "1", "cmd", "c1", "c2", "c3", "logo", 0, 1, 1,
                "drm", "license", null, "addon", "manifest");
        channel.setExtraJson("{\"extra\":true}");
        channel.setClearKeysJson("{\"k\":\"v\"}");
        db.saveAll(List.of(channel), categoryId, account);

        List<Channel> channels = db.getChannels(account, categoryId);
        assertEquals(1, channels.size());
        Channel stored = channels.get(0);
        assertEquals(categoryId, stored.getCategoryId());
        assertEquals("{\"k\":\"v\"}", stored.getClearKeysJson());
        assertEquals("{\"extra\":true}", stored.getExtraJson());

        assertNotNull(db.getChannelByChannelId("ch-1", categoryId, account.getDbId()));
        assertNotNull(db.getChannelByChannelIdAndAccount("ch-1", account.getDbId()));
        assertNull(db.getChannelByChannelId("missing", categoryId, account.getDbId()));

        assertTrue(db.isFresh(account, categoryId, 60_000));
        try (Connection conn = SqlConnectionRuntime.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE VodChannel SET cachedAt=0 WHERE accountId='" + account.getDbId() + "'");
        }
        assertFalse(db.isFresh(account, categoryId, 1));

        db.deleteByAccount(account.getDbId());
        assertTrue(db.getChannels(account, categoryId).isEmpty());

        db.saveAll(List.of(new Channel("ch-2", "Vod Channel2", "2", "cmd", null, null, null, "logo", 0, 1, 1,
                null, null, null, null, null)), categoryId, account);
        assertEquals(1, db.getChannels(account, categoryId).size());
    }

    @Test
    void seriesCategoryDb_crudFreshnessAndErrors() throws Exception {
        Account account = persistAccount("series-account", series);
        SeriesCategoryDb categoryDb = SeriesCategoryDb.get();

        Category category = new Category("s-1", "Series", "series", true, 3);
        category.setExtraJson("{\"s\":1}");
        categoryDb.saveAll(List.of(category), account);

        List<Category> categories = categoryDb.getCategories(account);
        assertEquals(1, categories.size());
        Category stored = categories.get(0);
        assertEquals(account.getDbId(), stored.getAccountId());
        assertEquals(account.getAction().name(), stored.getAccountType());
        assertEquals("{\"s\":1}", stored.getExtraJson());

        assertTrue(categoryDb.isFresh(account, 60_000));
        try (Connection conn = SqlConnectionRuntime.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE SeriesCategory SET cachedAt=0 WHERE accountId='" + account.getDbId() + "'");
        }
        assertFalse(categoryDb.isFresh(account, 1));

        categoryDb.deleteByAccount(account.getDbId());
        assertTrue(categoryDb.getCategories(account).isEmpty());

        categoryDb.saveAll(List.of(new Category("s-2", "Series2", "series2", false, 0)), account);
        assertEquals(1, categoryDb.getCategories(account).size());
    }

    @Test
    void seriesChannelDb_filtersAndErrorCoverage() throws Exception {
        Account account = persistAccount("series-ch-account", series);
        SeriesChannelDb channelDb = SeriesChannelDb.get();
        String categoryId = "series-cat";
        Channel channel = new Channel("s-chan-1", "Series Channel", "1", "cmd", null, null, null, "logo", 0, 1, 1,
                "drm", "license", null, "addon", "manifest");
        channel.setExtraJson("{\"extra\":false}");
        channel.setClearKeysJson("{\"k\":\"v\"}");
        channelDb.saveAll(List.of(channel), categoryId, account);

        List<Channel> channels = channelDb.getChannels(account, categoryId);
        assertEquals(1, channels.size());
        Channel storedChannel = channels.get(0);
        assertEquals(categoryId, storedChannel.getCategoryId());
        assertEquals("{\"k\":\"v\"}", storedChannel.getClearKeysJson());
        assertEquals("{\"extra\":false}", storedChannel.getExtraJson());

        assertTrue(channelDb.isFresh(account, categoryId, 60_000));
        try (Connection conn = SqlConnectionRuntime.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE SeriesChannel SET cachedAt=0 WHERE accountId='" + account.getDbId() + "'");
        }
        assertFalse(channelDb.isFresh(account, categoryId, 1));

        assertTrue(channelDb.getChannelsBySeriesIds(null, List.of("x")).isEmpty());
        assertTrue(channelDb.getChannelsBySeriesIds(account, List.of(" ", "\t")).isEmpty());

        List<Channel> filtered = channelDb.getChannelsBySeriesIds(account,
                List.of("s-chan-1", "s-chan-1", "", "  "));
        assertEquals(1, filtered.size());

        channelDb.saveAll(List.of(new Channel("s-chan-2", "Series Channel2", "2", "cmd", null, null, null, "logo", 0, 1, 1,
                null, null, null, null, null)), categoryId, account);
        assertEquals(1, channelDb.getChannels(account, categoryId).size());
    }
}
