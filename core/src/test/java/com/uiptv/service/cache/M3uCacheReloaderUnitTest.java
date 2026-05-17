package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.util.AccountType;
import com.uiptv.util.HttpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class M3uCacheReloaderUnitTest {

    @Test
    void remoteM3uSourceAllowsRawQuerySeparators() throws Exception {
        String playlistUrl = "https://playlist.invalid/export.m3u8?countries=aa|bb|cc&kind=live";
        String playlist = """
                #EXTM3U
                #EXTINF:-1 tvg-id="fake-news" group-title="News",Fake News
                https://stream.invalid/live/fake-news.m3u8
                """;
        Account account = new Account("Fake Remote M3U", null, null, null, null, null, null, null, null, null,
                AccountType.M3U8_URL, null, playlistUrl, false);
        M3uCacheReloader reloader = new M3uCacheReloader();
        List<PlaylistEntry> entries = new ArrayList<>();

        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.openStream(
                            Mockito.eq(playlistUrl),
                            Mockito.isNull(),
                            Mockito.eq("GET"),
                            Mockito.isNull(),
                            Mockito.any(HttpUtil.RequestOptions.class)))
                    .thenAnswer(_ -> streamResult(playlistUrl, playlist));

            Method method = M3uCacheReloader.class.getDeclaredMethod("forEachM3uEntry", Account.class, Consumer.class);
            method.setAccessible(true);
            try {
                method.invoke(reloader, account, (Consumer<PlaylistEntry>) entries::add);
            } catch (InvocationTargetException e) {
                fail(e.getCause());
            }
        }

        assertEquals(1, entries.size());
        assertEquals("Fake News", entries.getFirst().getTitle());
        assertEquals("News", entries.getFirst().getGroupTitle());
    }

    @Test
    void createAllCategoryWithAccumulatedChannels_whenNoMatchingCategories() throws Exception {
        M3uCacheReloader reloader = new M3uCacheReloader();

        List<Category> categories = List.of(
                new Category("1", "News", "news", false, 0),
                new Category("2", "Sports", "sports", false, 0)
        );

        Map<String, List<Channel>> channelsMap = new HashMap<>();
        channelsMap.put("Misc", List.of(channel("m1", "Misc One"), channel("m2", "Misc Two")));

        Method m = M3uCacheReloader.class.getDeclaredMethod("filterCategoriesForM3u", List.class, Map.class, LoggerCallback.class);
        m.setAccessible(true);

        List<Category> result = (List<Category>) m.invoke(reloader, categories, channelsMap, null);

        assertEquals(1, result.size(), "Should have created a single All category");
        assertEquals(CategoryType.ALL.displayName(), result.get(0).getTitle());
        assertTrue(channelsMap.containsKey(CategoryType.ALL.displayName()));
        assertEquals(2, channelsMap.get(CategoryType.ALL.displayName()).size(), "All should contain accumulated channels");
    }

    @Test
    void singleNonAllCategory_mergesChannelsIntoExistingAll_withImmutableLists() throws Exception {
        M3uCacheReloader reloader = new M3uCacheReloader();

        Category all = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category single = new Category("1", "Movies", "movies", false, 0);

        List<Category> categories = List.of(all, single);

        Map<String, List<Channel>> channelsMap = new HashMap<>();
        // immutable lists (List.of) to exercise the conversion in mergeChannelsIntoAll
        channelsMap.put(CategoryType.ALL.displayName(), List.of(channel("a1", "All One")));
        channelsMap.put("Movies", List.of(channel("m1", "Movie One")));

        Method m = M3uCacheReloader.class.getDeclaredMethod("filterCategoriesForM3u", List.class, Map.class, LoggerCallback.class);
        m.setAccessible(true);

        List<Category> result = (List<Category>) m.invoke(reloader, categories, channelsMap, null);

        assertEquals(1, result.size(), "Should only return All");
        assertEquals(CategoryType.ALL.displayName(), result.get(0).getTitle());
        List<Channel> allChannels = channelsMap.get(CategoryType.ALL.displayName());
        assertEquals(2, allChannels.size(), "All should contain merged channels");
    }

    @Test
    void singleNonAllCategory_noChannels_createsAllWhenMissing() throws Exception {
        M3uCacheReloader reloader = new M3uCacheReloader();

        Category single = new Category("1", "Solo", "solo", false, 0);
        List<Category> categories = List.of(single);

        Map<String, List<Channel>> channelsMap = new HashMap<>();
        // single has no channels and there's no All in categories

        Method m = M3uCacheReloader.class.getDeclaredMethod("filterCategoriesForM3u", List.class, Map.class, LoggerCallback.class);
        m.setAccessible(true);

        List<Category> result = (List<Category>) m.invoke(reloader, categories, channelsMap, null);

        assertEquals(1, result.size(), "Should return a created All category when none existed");
        assertEquals(CategoryType.ALL.displayName(), result.get(0).getTitle());
        // channelsMap should NOT contain All key because there were no channels to merge
        assertTrue(!channelsMap.containsKey(CategoryType.ALL.displayName()));
    }

    private static Channel channel(String id, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id);
        return channel;
    }

    private static HttpUtil.StreamResult streamResult(String sourceUrl, String body) {
        return new HttpUtil.StreamResult(
                "GET",
                sourceUrl,
                HttpUtil.STATUS_OK,
                Map.of(),
                Map.of(),
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                Mockito.mock(org.apache.hc.client5.http.impl.classic.CloseableHttpResponse.class)
        );
    }
}
