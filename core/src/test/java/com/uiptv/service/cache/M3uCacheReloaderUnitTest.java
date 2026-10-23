package com.uiptv.service.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.openStream(
                            eq(playlistUrl),
                            eq(Map.of("User-Agent", "UIPTV")),
                            eq("GET"),
                            isNull(),
                            any(HttpUtil.RequestOptions.class)))
                    .thenAnswer(_ -> streamResult(playlistUrl, playlist));

            Method method = M3uCacheReloader.class.getDeclaredMethod("forEachM3uEntry", Account.class, Consumer.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(reloader, account, (Consumer<PlaylistEntry>) entries::add));
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

    @Test
    void privateM3uHelpersCoverHiddenBucketsAndSourceEligibility() throws Exception {
        M3uCacheReloader reloader = new M3uCacheReloader();
        Category hidden = new Category("hidden", "Hidden", "hidden", false, 0);
        Category visible = new Category("visible", "Visible", "visible", false, 0);

        @SuppressWarnings("unchecked")
        java.util.Set<String> hiddenKeys = (java.util.Set<String>) invoke(reloader, "hiddenM3uCategoryKeys",
                new Class[]{List.class, List.class}, List.of(hidden, visible), List.of(visible));
        assertEquals(java.util.Set.of("hidden"), hiddenKeys);

        Channel hiddenChannel = channel("shared", "Hidden");
        Channel visibleChannel = channel("visible", "Visible");
        Map<String, List<Channel>> buckets = new java.util.LinkedHashMap<>();
        buckets.put("Hidden", List.of(hiddenChannel));
        buckets.put("Visible", new ArrayList<>(List.of(hiddenChannel, visibleChannel)));
        invoke(reloader, "removeHiddenM3uCategoryChannels", new Class[]{Map.class, List.class, List.class},
                buckets, List.of(hidden, visible), List.of(visible));
        assertFalse(buckets.containsKey("Hidden"));
        assertEquals(List.of(visibleChannel), buckets.get("Visible"));

        invoke(reloader, "applyM3uChannelCensoring", new Class[]{Map.class}, (Object) null);
        assertEquals(0, invoke(reloader, "totalChannels", new Class[]{Map.class}, (Object) null));
        invoke(reloader, "releaseList", new Class[]{List.class}, List.of("immutable"));

        @SuppressWarnings("unchecked")
        java.util.Set<String> noKeys = (java.util.Set<String>) invoke(reloader, "categoryTitleKeys",
                new Class[]{List.class}, (Object) null);
        assertTrue(noKeys.isEmpty());
        java.util.Set<String> channelKeys = new java.util.LinkedHashSet<>();
        invoke(reloader, "collectChannelKeys", new Class[]{List.class, java.util.Set.class},
                java.util.Arrays.asList(null, hiddenChannel), channelKeys);
        assertEquals(java.util.Set.of("id:shared"), channelKeys);
        Map<String, List<Channel>> releasable = new java.util.LinkedHashMap<>();
        releasable.put("mutable", new ArrayList<>(List.of(hiddenChannel)));
        releasable.put("immutable", List.of(visibleChannel));
        invoke(reloader, "releaseChannelMap", new Class[]{Map.class}, releasable);
        assertTrue(releasable.isEmpty());

        assertFalse((Boolean) invoke(reloader, "canReadFreshCategoriesFromSource", new Class[]{Account.class}, (Object) null));
        Account blank = new Account();
        blank.setM3u8Path(" ");
        assertFalse((Boolean) invoke(reloader, "canReadFreshCategoriesFromSource", new Class[]{Account.class}, blank));
        blank.setType(AccountType.M3U8_URL);
        blank.setM3u8Path("https://playlist.test/list.m3u8");
        assertTrue((Boolean) invoke(reloader, "canReadFreshCategoriesFromSource", new Class[]{Account.class}, blank));
    }

    @Test
    void categoryFilteringDropsEmptyUncategorizedAndRetainsPopulatedGroups() throws Exception {
        M3uCacheReloader reloader = new M3uCacheReloader();
        Category all = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category news = new Category("news", "News", "news", false, 0);
        Category uncategorized = new Category("uncat", CategoryType.UNCATEGORIZED.displayName(), "uncat", false, 0);
        Category empty = new Category("empty", "Empty", "empty", false, 0);
        Map<String, List<Channel>> buckets = new java.util.LinkedHashMap<>();
        buckets.put(CategoryType.ALL.displayName(), new ArrayList<>(List.of(channel("all", "All"))));
        buckets.put("News", new ArrayList<>(List.of(channel("news", "News"))));
        buckets.put(CategoryType.UNCATEGORIZED.displayName(), new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Category> filtered = (List<Category>) invoke(reloader, "filterCategoriesForM3u",
                new Class[]{List.class, Map.class, LoggerCallback.class},
                List.of(all, news, uncategorized, empty), buckets, null);

        assertEquals(List.of(CategoryType.ALL.displayName(), "News"), filtered.stream().map(Category::getTitle).toList());
        assertFalse(filtered.stream().anyMatch(category -> "Empty".equals(category.getTitle())));
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = M3uCacheReloader.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw error;
        }
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
                mock(org.apache.hc.client5.http.impl.classic.CloseableHttpResponse.class)
        );
    }
}
