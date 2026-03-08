package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelServiceBatchLookupTest extends DbBackedTest {

    @Test
    void getChannelsByChannelIdsAndAccount_returnsOnlyRequestedChannelsForAccount() {
        Account account = new Account("batch-account", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(Account.AccountAction.itv);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("batch-account");
        saved.setAction(Account.AccountAction.itv);

        Category category = new Category("cat-1", "News", "news", false, 0);
        CategoryDb.get().saveAll(List.of(category), saved);
        Category storedCategory = CategoryDb.get().getCategories(saved).getFirst();

        Channel first = new Channel();
        first.setChannelId("one");
        first.setName("One");
        first.setCmd("http://example.test/one.m3u8");

        Channel second = new Channel();
        second.setChannelId("two");
        second.setName("Two");
        second.setCmd("http://example.test/two.m3u8");

        Channel third = new Channel();
        third.setChannelId("three");
        third.setName("Three");
        third.setCmd("http://example.test/three.m3u8");

        ChannelDb.get().saveAll(List.of(first, second, third), storedCategory.getDbId(), saved);

        List<Channel> channels = ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one", "three", "missing"), saved.getDbId());

        assertEquals(2, channels.size());
        assertTrue(channels.stream().anyMatch(channel -> "one".equals(channel.getChannelId())));
        assertTrue(channels.stream().anyMatch(channel -> "three".equals(channel.getChannelId())));
    }

    @Test
    void getChannelsByChannelIdsAndAccount_deduplicatesRequestedIds() {
        Account account = new Account("batch-dedupe", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(Account.AccountAction.itv);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("batch-dedupe");
        saved.setAction(Account.AccountAction.itv);

        Category category = new Category("cat-1", "News", "news", false, 0);
        CategoryDb.get().saveAll(List.of(category), saved);
        Category storedCategory = CategoryDb.get().getCategories(saved).getFirst();

        Channel first = new Channel();
        first.setChannelId("dup");
        first.setName("Dup");
        first.setCmd("http://example.test/dup.m3u8");

        Channel second = new Channel();
        second.setChannelId("other");
        second.setName("Other");
        second.setCmd("http://example.test/other.m3u8");

        ChannelDb.get().saveAll(List.of(first, second), storedCategory.getDbId(), saved);

        List<Channel> channels = ChannelService.getInstance()
                .getChannelsByChannelIdsAndAccount(List.of("dup", "dup", "other", "dup"), saved.getDbId());

        assertEquals(2, channels.size());
        assertEquals(1, channels.stream().filter(channel -> "dup".equals(channel.getChannelId())).count());
        assertEquals(1, channels.stream().filter(channel -> "other".equals(channel.getChannelId())).count());
    }

    @Test
    void getChannelsByChannelIdsAndAccount_returnsEmptyForNullBlankOrMissingInput() {
        assertNotNull(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(null, "account-id"));
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(null, "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of(), "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(Arrays.asList(" ", "", null), "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one"), "").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one"), null).isEmpty());
    }
}
