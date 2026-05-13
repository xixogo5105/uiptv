package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelDbCoverageTest extends DbBackedTest {

    @Test
    void insertCountLookupAndDeleteByAccount_coverDirectDbPaths() {
        Account account = new Account("channel-db-coverage", "user", "pass", "http://test.com",
                "00:11:22:33:44:55", null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("channel-db-coverage");
        assertNotNull(savedAccount);

        Category category = new Category("cat-1", "Sports", "sports", false, 0);
        CategoryDb.get().saveAll(List.of(category), savedAccount);
        Category savedCategory = CategoryDb.get().getCategories(savedAccount).get(0);

        Channel first = new Channel("ch-1", "One", "1", "cmd://1", null, null, null, "logo1", 0, 1, 1, null, null, null, null, null);
        Channel second = new Channel("ch-2", "Two", "2", "cmd://2", null, null, null, "logo2", 0, 1, 1, "widevine", "http://license", null, "addon", "dash");

        ChannelDb.insert(first, savedCategory);
        ChannelDb.insert(second, savedCategory);

        ChannelDb channelDb = ChannelDb.get();
        assertEquals(2, channelDb.getChannelCountForAccount(savedAccount.getDbId()));

        List<Channel> channels = channelDb.getChannels(savedCategory.getDbId());
        assertEquals(2, channels.size());
        assertTrue(channels.stream().anyMatch(c -> "One".equals(c.getName())));
        assertTrue(channels.stream().anyMatch(c -> "Two".equals(c.getName()) && "dash".equals(c.getManifestType())));

        Channel fetched = channelDb.getChannelByChannelIdAndAccount("ch-2", savedAccount.getDbId());
        assertNotNull(fetched);
        assertEquals("Two", fetched.getName());
        assertEquals("http://license", fetched.getDrmLicenseUrl());
        assertNull(channelDb.getChannelByChannelIdAndAccount("missing", savedAccount.getDbId()));

        channelDb.deleteByAccount(savedAccount.getDbId());
        assertEquals(0, channelDb.getChannelCountForAccount(savedAccount.getDbId()));
        assertTrue(channelDb.getChannels(savedCategory.getDbId()).isEmpty());
    }
}
