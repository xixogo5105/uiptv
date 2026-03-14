package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationUIClearWatchingNowTest extends DbBackedTest {

    @Test
    void clearWatchingNowStates_removesSeriesAndVodWatchEntries() {
        Account account = createSeriesAccount("config-clear-watch-now");

        Channel vodChannel = new Channel();
        vodChannel.setChannelId("vod-1");
        vodChannel.setName("VOD 1");
        vodChannel.setCmd("http://127.0.0.1/vod/1.m3u8");
        VodWatchStateService.getInstance().save(account, "vod-cat", vodChannel);

        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                "series-cat",
                "series-1",
                "ep-1",
                "Episode 1",
                "1",
                "1"
        );

        assertFalse(SeriesWatchStateService.getInstance()
                .getAllSeriesLastWatchedByAccount(account.getDbId()).isEmpty());
        assertFalse(VodWatchStateService.getInstance().getAllByAccount(account.getDbId()).isEmpty());

        ConfigurationUI.clearWatchingNowStates();

        assertTrue(SeriesWatchStateService.getInstance()
                .getAllSeriesLastWatchedByAccount(account.getDbId()).isEmpty());
        assertTrue(VodWatchStateService.getInstance().getAllByAccount(account.getDbId()).isEmpty());
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(
                name,
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
        account.setAction(series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(series);
        return saved;
    }
}
