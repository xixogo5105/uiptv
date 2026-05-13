package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryResolverTest extends DbBackedTest {

    @Test
    void resolveCategories_addsAllForNonStalker() {
        Account account = new Account("cat-acc", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        Category sports = new Category("10", "Sports", "sports", false, 0);
        sports.setDbId("10");

        CategoryResolver resolver = new CategoryResolver();
        List<Category> resolved = resolver.resolveCategories(account, List.of(sports));

        assertEquals(2, resolved.size());
        assertEquals("all", resolved.get(0).getDbId());
        assertEquals(CategoryType.ALL.displayName(), resolved.get(0).getTitle());
    }

    @Test
    void resolveCategories_filtersUncategorizedWithoutChannels() {
        Account account = new Account("cat-acc", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        Category uncategorized = new Category("uncat", CategoryType.UNCATEGORIZED.displayName(), CategoryType.UNCATEGORIZED.identifier(), false, 0);
        uncategorized.setDbId("uncat-db");
        Category news = new Category("11", "News", "news", false, 0);
        news.setDbId("11");

        CategoryResolver resolver = new CategoryResolver();
        List<Category> resolved = resolver.resolveCategories(account, List.of(uncategorized, news));

        assertEquals(2, resolved.size());
        assertEquals(CategoryType.ALL.displayName(), resolved.get(0).getTitle());
        assertEquals("News", resolved.get(1).getTitle());
    }
}
