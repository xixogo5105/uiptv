package com.uiptv.service;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesEpisodeDbCategoryScopeTest extends DbBackedTest {

    @Test
    void episodesAreIsolatedByCategoryForSameSeries() {
        Account account = createSeriesAccount("series-episode-cache-scope");

        Channel catAEpisode = new Channel();
        catAEpisode.setChannelId("ep-a-1");
        catAEpisode.setName("Category A Episode 1");
        catAEpisode.setCmd("http://127.0.0.1/a1.m3u8");
        catAEpisode.setSeason("1");
        catAEpisode.setEpisodeNum("1");

        Channel catBEpisode = new Channel();
        catBEpisode.setChannelId("ep-b-1");
        catBEpisode.setName("Category B Episode 1");
        catBEpisode.setCmd("http://127.0.0.1/b1.m3u8");
        catBEpisode.setSeason("1");
        catBEpisode.setEpisodeNum("1");

        SeriesEpisodeDb.get().saveAll(account, "cat-a", "series-1", List.of(catAEpisode));
        SeriesEpisodeDb.get().saveAll(account, "cat-b", "series-1", List.of(catBEpisode));

        List<Channel> catAResults = SeriesEpisodeDb.get().getEpisodes(account, "cat-a", "series-1");
        List<Channel> catBResults = SeriesEpisodeDb.get().getEpisodes(account, "cat-b", "series-1");

        assertEquals(1, catAResults.size());
        assertEquals(1, catBResults.size());
        assertEquals("ep-a-1", catAResults.get(0).getChannelId());
        assertEquals("ep-b-1", catBResults.get(0).getChannelId());
        assertTrue(SeriesEpisodeDb.get().isFresh(account, "cat-a", "series-1", 60_000));
        assertTrue(SeriesEpisodeDb.get().isFresh(account, "cat-b", "series-1", 60_000));
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
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(Account.AccountAction.series);
        return saved;
    }
}
