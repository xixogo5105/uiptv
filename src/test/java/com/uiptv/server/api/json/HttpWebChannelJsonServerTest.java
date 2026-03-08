package com.uiptv.server.api.json;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.ChannelService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Pagination;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpWebChannelJsonServerTest extends DbBackedTest {

    @Test
    void privateHelpers_coverSeriesWatchState_categoryResolution_andPaging() throws Exception {
        HttpWebChannelJsonServer handler = new HttpWebChannelJsonServer();

        Account account = new Account();
        account.setDbId("acc-1");
        account.setAction(Account.AccountAction.series);

        Channel rowA = new Channel();
        rowA.setChannelId("series-a");
        rowA.setCategoryId("db-cat");
        Channel rowB = new Channel();
        rowB.setChannelId("series-b");

        Channel ep1 = new Channel();
        ep1.setChannelId("ep-1");
        ep1.setSeason("1");
        ep1.setEpisodeNum("1");
        ep1.setName("Episode 1");
        Channel ep2 = new Channel();
        ep2.setChannelId("ep-2");
        ep2.setSeason("1");
        ep2.setEpisodeNum("2");
        ep2.setName("Episode 2");

        Category apiCategory = new Category();
        apiCategory.setDbId("db-cat");
        apiCategory.setCategoryId("api-cat");
        apiCategory.setTitle("Series");

        SeriesCategoryDb seriesCategoryDb = Mockito.mock(SeriesCategoryDb.class);
        CategoryDb categoryDb = Mockito.mock(CategoryDb.class);
        VodCategoryDb vodCategoryDb = Mockito.mock(VodCategoryDb.class);
        ChannelService channelService = Mockito.mock(ChannelService.class);
        SeriesWatchStateService watchStateService = Mockito.mock(SeriesWatchStateService.class);

        SeriesWatchState seriesState = new SeriesWatchState();

        try (MockedStatic<SeriesCategoryDb> seriesCategoryDbStatic = Mockito.mockStatic(SeriesCategoryDb.class);
             MockedStatic<CategoryDb> categoryDbStatic = Mockito.mockStatic(CategoryDb.class);
             MockedStatic<VodCategoryDb> vodCategoryDbStatic = Mockito.mockStatic(VodCategoryDb.class);
             MockedStatic<ChannelService> channelServiceStatic = Mockito.mockStatic(ChannelService.class);
             MockedStatic<SeriesWatchStateService> watchStateStatic = Mockito.mockStatic(SeriesWatchStateService.class)) {
            seriesCategoryDbStatic.when(SeriesCategoryDb::get).thenReturn(seriesCategoryDb);
            categoryDbStatic.when(CategoryDb::get).thenReturn(categoryDb);
            vodCategoryDbStatic.when(VodCategoryDb::get).thenReturn(vodCategoryDb);
            channelServiceStatic.when(ChannelService::getInstance).thenReturn(channelService);
            watchStateStatic.when(SeriesWatchStateService::getInstance).thenReturn(watchStateService);

            Mockito.when(seriesCategoryDb.getById("db-cat")).thenReturn(apiCategory);
            Mockito.when(seriesCategoryDb.getById("api-cat")).thenReturn(apiCategory);
            Mockito.when(seriesCategoryDb.getCategories(account)).thenReturn(List.of(apiCategory));
            Mockito.when(categoryDb.getCategoryByDbId("db-cat", account)).thenReturn(apiCategory);
            Mockito.when(vodCategoryDb.getById("vod-cat")).thenReturn(apiCategory);
            Mockito.when(channelService.getSeries("api-cat", "movie-1", account, null, null)).thenReturn(List.of(ep1, ep2));
            Mockito.when(watchStateService.getSeriesLastWatched("acc-1", "api-cat", "series-a")).thenReturn(seriesState);
            Mockito.when(watchStateService.getSeriesLastWatched("acc-1", "api-cat", "series-b")).thenReturn(null);
            Mockito.when(watchStateService.getSeriesLastWatched("acc-1", "api-cat", "movie-1")).thenReturn(seriesState);
            Mockito.when(watchStateService.isMatchingEpisode(seriesState, "ep-1", "1", "1", "Episode 1")).thenReturn(false);
            Mockito.when(watchStateService.isMatchingEpisode(seriesState, "ep-2", "1", "2", "Episode 2")).thenReturn(true);

            invoke(handler, "applySeriesRowsWatched",
                    new Class[]{Account.class, String.class, List.class},
                    account, "db-cat", List.of(rowA, rowB));
            assertTrue(rowA.isWatched());
            assertFalse(rowB.isWatched());

            invoke(handler, "applySeriesEpisodesWatched",
                    new Class[]{Account.class, String.class, String.class, List.class},
                    account, "db-cat", "movie-1", List.of(ep1, ep2));
            assertFalse(ep1.isWatched());
            assertTrue(ep2.isWatched());

            String episodesJson = invoke(handler, "resolveSeriesEpisodesJson",
                    new Class[]{Account.class, String.class, String.class},
                    account, "db-cat", "movie-1");
            JSONArray episodes = new JSONArray(episodesJson);
            assertEquals(2, episodes.length());
            assertEquals("1", episodes.getJSONObject(1).optString("watched"));

            String enriched = invoke(handler, "enrichSeriesRowsWatchedJson",
                    new Class[]{Account.class, String.class, String.class},
                    account, "db-cat", "[{\"channelId\":\"series-a\"},{\"channelId\":\"series-b\",\"categoryId\":\"db-cat\"}]");
            JSONArray rows = new JSONArray(enriched);
            assertTrue(rows.getJSONObject(0).getBoolean("watched"));
            assertFalse(rows.getJSONObject(1).getBoolean("watched"));

            assertEquals(apiCategory, invoke(handler, "resolveCategoryByDbId",
                    new Class[]{Account.class, String.class}, account, "db-cat"));
            assertEquals(1, ((List<?>) invoke(handler, "resolveCategoriesForAccount",
                    new Class[]{Account.class}, account)).size());
            assertEquals("api-cat", invoke(handler, "normalizeSeriesCategoryId",
                    new Class[]{String.class}, "db-cat"));
            assertEquals("", invoke(handler, "normalizeSeriesCategoryId",
                    new Class[]{String.class}, " "));
        }

        assertEquals(20, (int) invoke(handler, "parseInt",
                new Class[]{String.class, int.class, int.class, int.class}, "5", 10, 20, 50));
        assertEquals(50, (int) invoke(handler, "parseInt",
                new Class[]{String.class, int.class, int.class, int.class}, "100", 10, 20, 50));
        assertEquals(10, (int) invoke(handler, "parseInt",
                new Class[]{String.class, int.class, int.class, int.class}, "bad", 10, 20, 50));

        Pagination pagination = new Pagination();
        pagination.setPaginationLimit(5);
        pagination.setMaxPageItems(12);
        assertTrue((Boolean) invokeStatic(HttpWebChannelJsonServer.class, "estimateHasMore",
                new Class[]{Pagination.class, int.class, int.class, int.class, int.class},
                pagination, 1, 0, 5, 5));
        assertFalse((Boolean) invokeStatic(HttpWebChannelJsonServer.class, "estimateHasMore",
                new Class[]{Pagination.class, int.class, int.class, int.class, int.class},
                null, 0, 0, 3, 5));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeStatic(Class<?> target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(null, args);
    }
}
