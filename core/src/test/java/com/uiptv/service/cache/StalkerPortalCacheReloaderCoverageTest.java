package com.uiptv.service.cache;

import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalkerPortalCacheReloaderCoverageTest {
    private final StalkerPortalCacheReloader reloader = new StalkerPortalCacheReloader();

    @Test
    void derivesNamedGenresAndCountsUnnamedProviderIds() throws Exception {
        Channel news = channel("news", "{\"category_name\":\" News \"}");
        Channel sports = channel("sports", "{\"genre\":\"Sports\"}");
        Channel unnamed = channel("unknown", "{\"category\":\"null\"}");
        List<Category> derived = invoke("deriveCategoriesFromChannels", new Class[]{List.class},
                java.util.Arrays.asList(news, sports, unnamed, null));

        assertEquals(List.of("news", "sports"), derived.stream().map(Category::getCategoryId).toList());
        assertEquals(List.of("News", "Sports"), derived.stream().map(Category::getTitle).toList());
        assertEquals(1, ((Integer) invoke("countUnnamedGenreIds", new Class[]{List.class, List.class},
                List.of(news, sports, unnamed), derived)).intValue());
        assertEquals(List.of(), invoke("deriveCategoriesFromChannels", new Class[]{List.class}, List.of()));
        assertNull(invoke("resolveGenreTitle", new Class[]{Channel.class}, channel("empty", "not-json")));
        assertEquals("Sports", invoke("resolveGenreTitle", new Class[]{Channel.class}, sports));
        assertNull(invoke("genreTitleFromChannelJson", new Class[]{Channel.class, String.class},
                null, "category"));
    }

    @Test
    void groupsChannelsAndAddsUncategorizedCategoryOnlyForOrphans() throws Exception {
        Category news = new Category("1", "News", null, false, 0);
        Category uncategorized = new Category("uncategorized", "Uncategorized", null, false, 0);
        Channel matched = channel("1", "{}");
        Channel orphan = channel("missing", "{}");
        Object grouping = invoke("groupChannelsByCategory",
                new Class[]{List.class, List.class, Map.class}, List.of(matched, orphan), List.of(news), Map.of());
        @SuppressWarnings("unchecked")
        Map<String, List<Channel>> matchedByCategory = recordField(grouping, "matchedChannelsByCatId");
        assertEquals(List.of(matched), matchedByCategory.get("1"));
        assertEquals(List.of(orphan), recordField(grouping, "orphanedChannels"));

        @SuppressWarnings("unchecked")
        List<Category> withSynthetic = (List<Category>) invoke("categoriesWithUncategorizedIfNeeded",
                new Class[]{List.class, List.class}, List.of(news), List.of(orphan));
        assertEquals(2, withSynthetic.size());
        assertTrue(withSynthetic.stream().anyMatch(category -> "Uncategorized".equals(category.getTitle())));

        @SuppressWarnings("unchecked")
        List<Category> alreadyPresent = (List<Category>) invoke("categoriesWithUncategorizedIfNeeded",
                new Class[]{List.class, List.class}, List.of(news, uncategorized), List.of(orphan));
        assertEquals(2, alreadyPresent.size());
        assertTrue((Boolean) invoke("isUncategorizedCategory", new Class[]{Category.class}, uncategorized));
        assertFalse((Boolean) invoke("isUncategorizedCategory", new Class[]{Category.class}, news));

        Map<String, Category> saved = new HashMap<>();
        saved.put("1", news);
        saved.put("uncategorized", uncategorized);
        assertEquals(uncategorized, invoke("findUncategorizedCategory", new Class[]{Map.class}, saved));
        assertNull(invoke("findUncategorizedCategory", new Class[]{Map.class}, Map.of("1", news)));
    }

    @Test
    void categoryPaginationStopsSafelyOnAllPortalResponses() throws Exception {
        Map<String, Channel> channels = new HashMap<>();
        channels.put("one", channel("one", "{}"));
        Object completeByExpected = invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                0, 1, List.of(channels.get("one")), channels, 0);
        assertTrue((Boolean) recordField(completeByExpected, "complete"));

        Object firstEmpty = invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                0, -1, List.of(), channels, 1);
        assertFalse((Boolean) recordField(firstEmpty, "complete"));

        Object finalEmpty = invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                1, 1, List.of(), channels, 1);
        assertTrue((Boolean) recordField(finalEmpty, "complete"));

        assertThrows(IllegalStateException.class, () -> invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                1, 3, List.of(), channels, 1));
        assertThrows(IllegalStateException.class, () -> invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                2, -1, List.of(channel("one", "{}")), channels, 1));
        Object continuing = invoke("categoryPageResult",
                new Class[]{int.class, long.class, List.class, Map.class, int.class},
                1, -1, List.of(channel("two", "{}")), channels, 1);
        assertFalse((Boolean) recordField(continuing, "complete"));
    }

    @Test
    void catalogueAndGenreParsingHandleEmptyMalformedAndValidInputs() throws Exception {
        Object empty = invoke("parseCatalogueJson", new Class[]{String.class}, " ");
        assertNull(recordField(empty, "channels"));
        Object malformed = invoke("parseCatalogueJson", new Class[]{String.class}, "not-json");
        assertTrue(((List<?>) recordField(malformed, "channels")).isEmpty());

        assertEquals(0, ((Integer) invoke("countUnnamedGenreIds", new Class[]{List.class, List.class}, null, List.of())).intValue());
        assertEquals(0, ((Integer) invoke("countUnnamedGenreIds", new Class[]{List.class, List.class}, List.of(), List.of())).intValue());
        assertNull(invoke("resolveGenreTitle", new Class[]{Channel.class}, new Channel()));
    }

    private static Channel channel(String categoryId, String extraJson) {
        Channel channel = new Channel();
        channel.setChannelId(categoryId + "-channel");
        channel.setCategoryId(categoryId);
        channel.setExtraJson(extraJson);
        return channel;
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = StalkerPortalCacheReloader.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return (T) method.invoke(reloader, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw error;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T recordField(Object record, String name) throws Exception {
        assertNotNull(record);
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (T) accessor.invoke(record);
    }
}
