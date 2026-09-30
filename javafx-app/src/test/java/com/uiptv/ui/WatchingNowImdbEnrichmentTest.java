package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Playing an episode marks it watched, which fires the watch-state change listener, which routes
 * through {@code applySeriesDelta} -> {@code mergePanelInPlace}. That method rebuilt the panel's
 * {@code WatchingEpisode} objects from the local cache, discarding the per-episode IMDb artwork,
 * plot and release date that {@code enrichEpisodesFromMeta} had applied. Because {@code imdbLoaded}
 * stayed true nothing ever re-applied them, so every episode card in the open detail view lost its
 * IMDb detail permanently.
 */
class WatchingNowImdbEnrichmentTest {
    private static final String ACCOUNT_ID = "acct-imdb";
    private static final String CATEGORY_ID = "cat-imdb";
    private static final String SERIES_ID = "series-imdb";
    private static final String IMDB_LOGO = "http://example.com/imdb/s01e02.jpg";

    @BeforeAll
    static void setupFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void deltaMergeKeepsPerEpisodeImdbEnrichment() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            try {
                ThumbnailWatchingNowUI ui = new ThumbnailWatchingNowUI();
                Account account = account();
                SeriesWatchState state = state();

                // A cache entry as lazyLoadImdb writes it: series-level fields plus episodesMeta.
                cacheImdbEntry(ui, account, state);

                Class<?> panelClass = Class.forName("com.uiptv.ui.BaseWatchingNowUI$SeriesPanelData");
                Constructor<?> panelCtor = panelClass.getDeclaredConstructor(
                        Account.class, SeriesWatchState.class, String.class, JSONObject.class, List.class,
                        Class.forName("com.uiptv.shared.EpisodeList"));
                panelCtor.setAccessible(true);

                JSONObject enrichedSeasonInfo = new JSONObject()
                        .put("name", "Series Title")
                        .put("plot", "The series plot")
                        .put("rating", "8.4")
                        .put("imdbUrl", "https://www.imdb.com/title/tt1/");
                Object rendered = panelCtor.newInstance(
                        account, state, "Series Title", enrichedSeasonInfo,
                        new ArrayList<>(List.of(episode(account, state, IMDB_LOGO, "Imdb plot"))), null);

                // The delta rebuild: a fresh panel carrying only name + cover, with an episode that
                // has nothing but what the local cache/portal episode list provides.
                JSONObject rebuiltSeasonInfo = new JSONObject().put("name", "Series Title");
                Object rebuilt = panelCtor.newInstance(
                        account, state, "Series Title", rebuiltSeasonInfo,
                        new ArrayList<>(List.of(episode(account, state, "", ""))), null);

                Method merge = Class.forName("com.uiptv.ui.BaseWatchingNowUI")
                        .getDeclaredMethod("mergePanelInPlace", panelClass, panelClass);
                merge.setAccessible(true);
                merge.invoke(ui, rendered, rebuilt);

                Field episodesField = panelClass.getDeclaredField("episodes");
                episodesField.setAccessible(true);
                List<?> episodes = (List<?>) episodesField.get(rendered);
                assertEquals(1, episodes.size(), "the delta merge should have swapped in the rebuilt episode");

                Field imageUrlField = episodes.getFirst().getClass().getDeclaredField("imageUrl");
                imageUrlField.setAccessible(true);
                Field plotField = episodes.getFirst().getClass().getDeclaredField("plot");
                plotField.setAccessible(true);

                assertEquals(IMDB_LOGO, imageUrlField.get(episodes.getFirst()),
                        "episode artwork from IMDb must survive the watch-state delta merge");
                assertEquals("Imdb plot", plotField.get(episodes.getFirst()),
                        "episode plot from IMDb must survive the watch-state delta merge");

                Field seasonInfoField = panelClass.getDeclaredField("seasonInfo");
                seasonInfoField.setAccessible(true);
                JSONObject seasonInfo = (JSONObject) seasonInfoField.get(rendered);
                assertEquals("8.4", seasonInfo.optString("rating", ""),
                        "the series IMDb rating must survive the watch-state delta merge");
                assertEquals("https://www.imdb.com/title/tt1/", seasonInfo.optString("imdbUrl", ""),
                        "the IMDb link must survive the watch-state delta merge");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static void cacheImdbEntry(ThumbnailWatchingNowUI ui, Account account, SeriesWatchState state) throws Exception {
        Class<?> entryClass = Class.forName("com.uiptv.ui.BaseWatchingNowUI$ImdbCacheEntry");
        Constructor<?> entryCtor = entryClass.getDeclaredConstructor(JSONObject.class, JSONArray.class);
        entryCtor.setAccessible(true);

        JSONObject seasonInfo = new JSONObject()
                .put("name", "Series Title")
                .put("plot", "The series plot")
                .put("rating", "8.4")
                .put("imdbUrl", "https://www.imdb.com/title/tt1/");
        JSONArray episodesMeta = new JSONArray()
                .put(new JSONObject()
                        .put("season", "1")
                        .put("episodeNum", "2")
                        .put("title", "Second")
                        .put("logo", IMDB_LOGO)
                        .put("plot", "Imdb plot"));
        Object entry = entryCtor.newInstance(seasonInfo, episodesMeta);
        assertNotNull(entry);

        Field cacheField = Class.forName("com.uiptv.ui.BaseWatchingNowUI")
                .getDeclaredField("imdbCacheByPanelKey");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> cache = (java.util.Map<String, Object>) cacheField.get(ui);
        cache.put(ACCOUNT_ID + "|" + CATEGORY_ID + "|" + SERIES_ID, entry);
    }

    private static Account account() {
        Account account = new Account("imdb-account", "user", "pass", "http://example.com/", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setDbId(ACCOUNT_ID);
        account.setAccountName("Imdb Account");
        return account;
    }

    private static SeriesWatchState state() {
        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(ACCOUNT_ID);
        state.setCategoryId(CATEGORY_ID);
        state.setSeriesId(SERIES_ID);
        state.setUpdatedAt(System.currentTimeMillis());
        return state;
    }

    private static Object episode(Account account, SeriesWatchState state, String imageUrl, String plot) throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("ep-2");
        channel.setName("Second");
        channel.setCmd("http://example.com/ep-2");
        channel.setLogo(imageUrl);
        channel.setSeason("1");
        channel.setEpisodeNum("2");

        Class<?> episodeClass = Class.forName("com.uiptv.ui.BaseWatchingNowUI$WatchingEpisode");
        Constructor<?> episodeCtor = episodeClass.getDeclaredConstructor(
                Account.class, SeriesWatchState.class, Channel.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class, boolean.class);
        episodeCtor.setAccessible(true);
        return episodeCtor.newInstance(account, state, channel, "1", "2", "Second", imageUrl, plot, "", "", false);
    }
}
