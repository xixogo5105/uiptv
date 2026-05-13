package com.uiptv.service;

import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceDeleteWatchStateTest extends DbBackedTest {

    @Test
    void delete_removesVodWatchStateForAccount() {
        AccountService accountService = AccountService.getInstance();
        Account account = new Account(
                "vod-delete-watch-state",
                "user",
                "pass",
                "http://127.0.0.1/mock",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                "http://127.0.0.1/mock",
                false
        );
        accountService.save(account);
        Account saved = accountService.getByName("vod-delete-watch-state");

        Channel channel = new Channel();
        channel.setChannelId("vod-123");
        channel.setName("Movie 123");
        channel.setCmd("http://vod/123.mp4");
        channel.setLogo("http://vod/123.png");

        VodWatchStateService.getInstance().save(saved, "movies", channel);
        assertTrue(VodWatchStateDb.get().getByAccount(saved.getDbId()).size() > 0);

        accountService.delete(saved.getDbId());

        assertTrue(VodWatchStateDb.get().getByAccount(saved.getDbId()).isEmpty());
    }
}
