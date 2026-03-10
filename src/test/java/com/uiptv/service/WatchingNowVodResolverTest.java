package com.uiptv.service;

import com.uiptv.db.VodChannelDb;
import com.uiptv.db.VodWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchingNowVodResolverTest extends DbBackedTest {

    @Test
    void resolve_prefersProviderMetadata() {
        Account account = createVodAccount("resolver-vod-provider");

        Channel provider = new Channel();
        provider.setChannelId("100");
        provider.setName("Provider Movie");
        provider.setLogo("https://img/provider.png");
        provider.setExtraJson(new org.json.JSONObject()
                .put("description", "Plot")
                .put("release_date", "2020-01-01")
                .put("rating_imdb", "7.5")
                .put("time", "120")
                .put("name", "Provider Movie")
                .toString());
        VodChannelDb.get().saveAll(List.of(provider), "cat-db-1", account);

        VodWatchState state = new VodWatchState();
        state.setAccountId(account.getDbId());
        state.setCategoryId("cat-db-1");
        state.setVodId("100");
        state.setVodName("Fallback Movie");
        state.setVodLogo("https://img/fallback.png");
        state.setVodCmd("http://vod/100");
        state.setUpdatedAt(100L);
        VodWatchStateDb.get().upsert(state);

        WatchingNowVodResolver resolver = new WatchingNowVodResolver();
        WatchingNowVodResolver.VodRow row = resolver.resolveForAccount(account).getFirst();

        assertEquals("Provider Movie", row.getDisplayTitle());
        assertEquals("https://img/provider.png", row.getMetadata().getLogo());
        assertEquals("Plot", row.getMetadata().getPlot());
        assertEquals("2020-01-01", row.getMetadata().getReleaseDate());
        assertEquals("7.5", row.getMetadata().getRating());
        assertEquals("120", row.getMetadata().getDuration());
        assertEquals("Provider Movie", row.getPlaybackChannel().getName());
    }

    @Test
    void resolve_fallsBackToStateWhenProviderMissing() {
        Account account = createVodAccount("resolver-vod-fallback");

        VodWatchState state = new VodWatchState();
        state.setAccountId(account.getDbId());
        state.setCategoryId("cat-db-1");
        state.setVodId("200");
        state.setVodName("Fallback Movie");
        state.setVodLogo("https://img/fallback.png");
        state.setVodCmd("http://vod/200");
        state.setUpdatedAt(200L);
        VodWatchStateDb.get().upsert(state);

        WatchingNowVodResolver resolver = new WatchingNowVodResolver();
        WatchingNowVodResolver.VodRow row = resolver.resolveForAccount(account).getFirst();

        assertEquals("Fallback Movie", row.getDisplayTitle());
        assertEquals("https://img/fallback.png", row.getMetadata().getLogo());
        assertEquals("Fallback Movie", row.getPlaybackChannel().getName());
    }

    private Account createVodAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.vod);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.vod);
        return persisted;
    }
}
