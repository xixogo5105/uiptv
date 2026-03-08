package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ThemeCssOverrideService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineSchemaCompatibilityTest extends DbBackedTest {

    @Test
    void baselineSchemaMatchesCurrentDeclaredStructure() throws Exception {
        resetDatabaseToBaselineOnly();
        try (Connection conn = openRawConnection()) {
            DatabasePatchesUtils.applyBaseline(conn);

            Map<String, List<String>> expected = expectedTableColumns();
            assertEquals(expected.keySet(), actualTables(conn));
            for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
                assertEquals(entry.getValue(), actualColumns(conn, entry.getKey()), "Column drift in " + entry.getKey());
            }
        }
    }

    @Test
    void baselineSchemaSupportsRepresentativePersistenceAcrossCoreFlows() throws Exception {
        resetDatabaseToBaselineOnly();
        try (Connection conn = openRawConnection()) {
            DatabasePatchesUtils.applyBaseline(conn);
        }

        ConfigurationService configurationService = ConfigurationService.getInstance();
        ThemeCssOverrideService themeCssOverrideService = ThemeCssOverrideService.getInstance();
        AccountService accountService = AccountService.getInstance();
        BookmarkService bookmarkService = BookmarkService.getInstance();

        Configuration configuration = new Configuration(
                "player-1",
                "player-2",
                "player-3",
                "player-1",
                "kids",
                "ads",
                true,
                true,
                "8899",
                true,
                true,
                "45",
                true
        );
        configuration.setWideView(true);
        configuration.setLanguageLocale("en-US");
        configuration.setTmdbReadAccessToken("tmdb-token");
        configuration.setUiZoomPercent("125");
        configurationService.save(configuration);

        Configuration savedConfiguration = configurationService.read();
        assertEquals("player-1", savedConfiguration.getPlayerPath1());
        assertEquals("8899", savedConfiguration.getServerPort());
        assertEquals("125", savedConfiguration.getUiZoomPercent());
        assertTrue(savedConfiguration.isWideView());

        ThemeCssOverride override = new ThemeCssOverride();
        override.setLightThemeCssName("light.css");
        override.setLightThemeCssContent(".root { -fx-font-size: 14px; }");
        override.setDarkThemeCssName("dark.css");
        override.setDarkThemeCssContent(".root { -fx-font-size: 15px; }");
        override.setUpdatedAt("123456");
        themeCssOverrideService.save(override);

        ThemeCssOverride savedOverride = themeCssOverrideService.read();
        assertEquals("light.css", savedOverride.getLightThemeCssName());
        assertEquals("dark.css", savedOverride.getDarkThemeCssName());

        Account account = new Account(
                "BaselineAccount",
                "user",
                "pass",
                "http://example.test",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                "epg.xml",
                "playlist.m3u8",
                true
        );
        account.setHttpMethod("POST");
        account.setTimezone("America/New_York");
        accountService.save(account);

        Account savedAccount = accountService.getByName("BaselineAccount");
        assertNotNull(savedAccount);
        assertEquals("POST", savedAccount.getHttpMethod());
        assertEquals("America/New_York", savedAccount.getTimezone());

        Category liveCategory = new Category("live-cat", "Live Category", "live", true, 0);
        CategoryDb.get().saveAll(List.of(liveCategory), savedAccount);
        List<Category> liveCategories = CategoryDb.get().getCategories(savedAccount);
        assertEquals(1, liveCategories.size());

        Channel liveChannel = new Channel(
                "live-1",
                "Live One",
                "1",
                "http://stream/live.m3u8",
                "alt-1",
                "alt-2",
                "alt-3",
                "logo-live",
                0,
                1,
                1,
                "widevine",
                "http://license/live",
                Map.of("kid-live", "key-live"),
                "input-live",
                "hls"
        );
        ChannelDb.get().saveAll(List.of(liveChannel), liveCategories.get(0).getDbId(), savedAccount);
        List<Channel> liveChannels = ChannelDb.get().getChannels(liveCategories.get(0).getDbId());
        assertEquals(1, liveChannels.size());
        assertEquals("widevine", liveChannels.get(0).getDrmType());

        Bookmark bookmark = new Bookmark(
                savedAccount.getAccountName(),
                liveCategories.get(0).getTitle(),
                liveChannel.getChannelId(),
                liveChannel.getName(),
                liveChannel.getCmd(),
                savedAccount.getServerPortalUrl(),
                liveCategories.get(0).getCategoryId()
        );
        bookmark.setAccountAction(savedAccount.getAction());
        bookmark.setCategoryJson("{\"id\":\"cat\"}");
        bookmark.setChannelJson("{\"id\":\"chan\"}");
        bookmark.setVodJson("{\"id\":\"vod\"}");
        bookmark.setSeriesJson("{\"id\":\"series\"}");
        bookmarkService.save(bookmark);

        List<Bookmark> bookmarks = bookmarkService.read();
        assertEquals(1, bookmarks.size());
        assertFalse(bookmarkService.getBookmarksByCategory(liveCategories.get(0).getCategoryId()).isEmpty());

        BookmarkCategory bookmarkCategory = new BookmarkCategory(null, "Pinned");
        bookmarkService.addCategory(bookmarkCategory);
        assertTrue(bookmarkService.getAllCategories().stream().anyMatch(item -> "Pinned".equals(item.getName())));

        Account vodAccount = accountService.getByName("BaselineAccount");
        vodAccount.setAction(vod);
        Category vodCategory = new Category("vod-cat", "Vod Category", "vod", false, 0);
        vodCategory.setExtraJson("{\"kind\":\"vod\"}");
        VodCategoryDb.get().saveAll(List.of(vodCategory), vodAccount);
        List<Category> vodCategories = VodCategoryDb.get().getCategories(vodAccount);
        assertEquals(1, vodCategories.size());

        Channel vodChannel = new Channel(
                "vod-1",
                "Vod One",
                "10",
                "http://stream/vod.mp4",
                null,
                null,
                null,
                "logo-vod",
                0,
                1,
                1,
                "playready",
                "http://license/vod",
                Map.of("kid-vod", "key-vod"),
                "input-vod",
                "mp4"
        );
        vodChannel.setExtraJson("{\"rating\":\"5\"}");
        VodChannelDb.get().saveAll(List.of(vodChannel), vodCategories.get(0).getCategoryId(), vodAccount);
        List<Channel> vodChannels = VodChannelDb.get().getChannels(vodAccount, vodCategories.get(0).getCategoryId());
        assertEquals(1, vodChannels.size());
        assertEquals("{\"rating\":\"5\"}", vodChannels.get(0).getExtraJson());

        Account seriesAccount = accountService.getByName("BaselineAccount");
        seriesAccount.setAction(series);
        Category seriesCategory = new Category("series-cat", "Series Category", "series", false, 0);
        seriesCategory.setExtraJson("{\"kind\":\"series\"}");
        SeriesCategoryDb.get().saveAll(List.of(seriesCategory), seriesAccount);
        List<Category> seriesCategories = SeriesCategoryDb.get().getCategories(seriesAccount);
        assertEquals(1, seriesCategories.size());

        Channel seriesChannel = new Channel(
                "series-1",
                "Series One",
                "20",
                "",
                null,
                null,
                null,
                "logo-series",
                0,
                1,
                1,
                null,
                null,
                null,
                null,
                null
        );
        seriesChannel.setExtraJson("{\"seasons\":1}");
        SeriesChannelDb.get().saveAll(List.of(seriesChannel), seriesCategories.get(0).getCategoryId(), seriesAccount);
        List<Channel> seriesChannels = SeriesChannelDb.get().getChannels(seriesAccount, seriesCategories.get(0).getCategoryId());
        assertEquals(1, seriesChannels.size());

        Channel episode = new Channel();
        episode.setChannelId("episode-1");
        episode.setName("Episode 1");
        episode.setCmd("http://stream/episode-1.mp4");
        episode.setLogo("episode-logo");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        episode.setDescription("Pilot");
        episode.setReleaseDate("2025-01-01");
        episode.setRating("8.4");
        episode.setDuration("45");
        episode.setExtraJson("{\"episode\":1}");
        SeriesEpisodeDb.get().saveAll(seriesAccount, seriesCategories.get(0).getCategoryId(), seriesChannel.getChannelId(), List.of(episode));
        List<Channel> episodes = SeriesEpisodeDb.get().getEpisodes(seriesAccount, seriesCategories.get(0).getCategoryId(), seriesChannel.getChannelId());
        assertEquals(1, episodes.size());
        assertEquals("Pilot", episodes.get(0).getDescription());

        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(seriesAccount.getDbId());
        state.setMode("series");
        state.setCategoryId(seriesCategories.get(0).getCategoryId());
        state.setSeriesId(seriesChannel.getChannelId());
        state.setEpisodeId(episode.getChannelId());
        state.setEpisodeName(episode.getName());
        state.setSeason(episode.getSeason());
        state.setEpisodeNum(1);
        state.setUpdatedAt(System.currentTimeMillis());
        state.setSource("MANUAL");
        state.setSeriesCategorySnapshot("{\"id\":\"series-cat\"}");
        state.setSeriesChannelSnapshot("{\"id\":\"series-1\"}");
        state.setSeriesEpisodeSnapshot("{\"id\":\"episode-1\"}");
        SeriesWatchStateDb.get().upsert(state);

        SeriesWatchState savedState = SeriesWatchStateDb.get()
                .getBySeries(seriesAccount.getDbId(), seriesCategories.get(0).getCategoryId(), seriesChannel.getChannelId());
        assertNotNull(savedState);
        assertEquals("episode-1", savedState.getEpisodeId());
        assertEquals("MANUAL", savedState.getSource());
    }

    private void resetDatabaseToBaselineOnly() {
        if (testDbFile.exists()) {
            assertTrue(testDbFile.delete(), "Failed to delete pre-initialized test DB");
        }
    }

    private Connection openRawConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + testDbFile.getAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> expectedTableColumns() throws Exception {
        Field structureField = DatabaseUtils.class.getDeclaredField("dbStructure");
        structureField.setAccessible(true);
        Map<String, List<DataColumn>> dbStructure = (Map<String, List<DataColumn>>) structureField.get(null);
        Map<String, List<String>> expected = new LinkedHashMap<>();
        for (Map.Entry<String, List<DataColumn>> entry : dbStructure.entrySet()) {
            List<String> columns = new ArrayList<>();
            for (DataColumn column : entry.getValue()) {
                columns.add(column.getColumnName());
            }
            expected.put(entry.getKey(), columns);
        }
        return expected;
    }

    private Set<String> actualTables(Connection conn) throws Exception {
        Set<String> tables = new LinkedHashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private List<String> actualColumns(Connection conn, String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
