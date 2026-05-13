package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.db.VodWatchStateDb;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VodWatchStateServiceTest extends DbBackedTest {

    @Test
    void saveAndRemove_areScopedByCategory() {
        Account account = createVodAccount("vod-watch-state");
        VodWatchStateService service = VodWatchStateService.getInstance();

        service.save(account, "movies", vod("vod-1", "Movie One", "http://vod/1.mp4"));
        service.save(account, "shows", vod("vod-1", "Movie One Duplicate", "http://vod/1b.mp4"));

        VodWatchState movies = service.getVod(account.getDbId(), "movies", "vod-1");
        VodWatchState shows = service.getVod(account.getDbId(), "shows", "vod-1");
        assertNotNull(movies);
        assertNotNull(shows);
        assertEquals("Movie One", movies.getVodName());
        assertEquals("Movie One Duplicate", shows.getVodName());

        service.remove(account.getDbId(), "movies", "vod-1");
        assertNull(VodWatchStateDb.get().getByVod(account.getDbId(), "movies", "vod-1"));
        assertNotNull(service.getVod(account.getDbId(), "shows", "vod-1"));
    }

    @Test
    void getVod_fallsBackToLatestAcrossCategoryVariants() {
        Account account = createVodAccount("vod-watch-fallback");
        VodWatchStateService service = VodWatchStateService.getInstance();

        service.save(account, "portal-cat-1", vod("vod-2", "Movie Two", "http://vod/2.mp4"));

        VodWatchState state = service.getVod(account.getDbId(), "db-cat-77", "vod-2");
        assertNotNull(state);
        assertEquals("vod-2", state.getVodId());
        assertTrue(service.isSaved(account.getDbId(), "db-cat-77", "vod-2"));
    }

    @Test
    void save_normalizesColonDelimitedVodIds() {
        Account account = createVodAccount("vod-watch-normalize");
        VodWatchStateService service = VodWatchStateService.getInstance();

        service.save(account, "movies", vod("123:123", "Movie Colon", "http://vod/123.mp4"));

        VodWatchState stored = service.getVod(account.getDbId(), "movies", "123");
        assertNotNull(stored);
        assertEquals("123", stored.getVodId());
    }

    private Account createVodAccount(String name) {
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
        account.setAction(vod);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(vod);
        return saved;
    }

    private Channel vod(String vodId, String name, String cmd) {
        Channel channel = new Channel();
        channel.setChannelId(vodId);
        channel.setName(name);
        channel.setCmd(cmd);
        channel.setLogo("https://img/" + vodId + ".png");
        return channel;
    }
}
