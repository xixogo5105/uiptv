package com.uiptv.service.cache;

import com.uiptv.model.Account;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtremeApiCacheReloaderCoverageTest {
    private final XtremeApiCacheReloader reloader = new XtremeApiCacheReloader();

    @Test
    void credentialAndActionHelpersHandleGroupedAndStandaloneAccounts() throws Exception {
        Account account = new Account();
        account.setUsername("original");
        account.setPassword("secret");
        @SuppressWarnings("unchecked")
        List<Account> standalone = invoke("credentialAccounts", new Class[]{Account.class}, account);
        assertSame(account, standalone.getFirst());

        account.setXtremeCredentialsJson("[{\"username\":\"one\",\"password\":\"one-pass\"},"
                + "{\"username\":\"two\",\"password\":\"two-pass\"}]");
        @SuppressWarnings("unchecked")
        List<Account> grouped = invoke("credentialAccounts", new Class[]{Account.class}, account);
        assertEquals(List.of("one", "two"), grouped.stream().map(Account::getUsername).toList());
        assertEquals("one-pass", grouped.getFirst().getPassword());
        assertNull(grouped.getFirst().getToken());

        account.setAction(Account.AccountAction.vod);
        assertTrue((Boolean) invoke("isVodOrSeriesAction", new Class[]{Account.class}, account));
        account.setAction(Account.AccountAction.series);
        assertTrue((Boolean) invoke("isVodOrSeriesAction", new Class[]{Account.class}, account));
        account.setAction(Account.AccountAction.itv);
        assertFalse((Boolean) invoke("isVodOrSeriesAction", new Class[]{Account.class}, account));
    }

    @Test
    void globalLookupAndGroupingHelpersHandleAssignedAndOrphanedChannels() throws Exception {
        Channel assigned = channel("assigned", "1");
        Channel orphan = channel("orphan", "missing");
        assertFalse((Boolean) invoke("hasUsableGlobalLookupChannels", new Class[]{List.class, com.uiptv.api.LoggerCallback.class},
                List.of(), null));
        assertTrue((Boolean) invoke("hasUsableGlobalLookupChannels", new Class[]{List.class, com.uiptv.api.LoggerCallback.class},
                List.of(orphan), null));
        assertTrue((Boolean) invoke("hasCategoryAssignments", new Class[]{List.class}, List.of(assigned)));
        assertFalse((Boolean) invoke("hasCategoryAssignments", new Class[]{List.class}, List.of(channel("none", ""))));

        Category category = new Category("1", "News", null, false, 0);
        Object grouping = invoke("groupChannelsByKnownCategory",
                new Class[]{List.class, List.class, Map.class}, List.of(assigned, orphan), List.of(category), Map.of());
        @SuppressWarnings("unchecked")
        Map<String, List<Channel>> matched = recordField(grouping, "matchedByCategory");
        assertEquals(List.of(assigned), matched.get("1"));
        assertEquals(List.of(orphan), recordField(grouping, "orphaned"));

        @SuppressWarnings("unchecked")
        List<Category> withUncategorized = invoke("categoriesWithUncategorizedIfNeeded",
                new Class[]{List.class, List.class}, List.of(category), List.of(orphan));
        assertEquals(2, withUncategorized.size());
        assertTrue((Boolean) invoke("isUncategorizedCategory", new Class[]{Category.class}, withUncategorized.getLast()));
        Map<String, Category> saved = new HashMap<>();
        saved.put("1", category);
        saved.put("uncategorized", withUncategorized.getLast());
        assertEquals(withUncategorized.getLast(), invoke("findUncategorizedCategory", new Class[]{Map.class}, saved));
    }

    @Test
    void emptyFetchResultsRespectCensoringAndReturnHandledState() throws Exception {
        Object emptyResult = newNestedRecord("CategoryFetchResult", Map.of(), 0, 0);
        Object handled = invoke("handleEmptyCategoryFetchResult",
                new Class[]{Account.class, List.class, emptyResult.getClass(), boolean.class, com.uiptv.api.LoggerCallback.class},
                new Account(), List.of(), emptyResult, false, null);
        assertTrue((Boolean) recordField(handled, "handled"));

        Object removedResult = newNestedRecord("CategoryFetchResult", Map.of(), 0, 2);
        Object censored = invoke("handleEmptyCategoryFetchResult",
                new Class[]{Account.class, List.class, removedResult.getClass(), boolean.class, com.uiptv.api.LoggerCallback.class},
                new Account(), List.of(), removedResult, true, null);
        assertTrue((Boolean) recordField(censored, "handled"));

        Object nonEmptyResult = newNestedRecord("CategoryFetchResult", Map.of(), 1, 1);
        Object notHandled = invoke("handleEmptyCategoryFetchResult",
                new Class[]{Account.class, List.class, nonEmptyResult.getClass(), boolean.class, com.uiptv.api.LoggerCallback.class},
                new Account(), List.of(), nonEmptyResult, false, null);
        assertFalse((Boolean) recordField(notHandled, "handled"));
    }

    private static Channel channel(String id, String categoryId) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(id);
        channel.setCategoryId(categoryId);
        return channel;
    }

    private Object newNestedRecord(String name, Object... values) throws Exception {
        for (Class<?> nested : XtremeApiCacheReloader.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals(name)) {
                var constructor = nested.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            }
        }
        throw new IllegalArgumentException("Missing nested record " + name);
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = XtremeApiCacheReloader.class.getDeclaredMethod(name, parameterTypes);
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
    private static <T> T recordField(Object recordValue, String name) throws Exception {
        Method accessor = recordValue.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (T) accessor.invoke(recordValue);
    }
}
