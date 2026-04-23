package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3uCacheReloaderUnitTest {

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
}
