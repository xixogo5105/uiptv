package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.EpisodeList;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

class XtremeApiParserCoverageTest {

    @Test
    void streamsChannelArraysInBatchesAndHonoursCancellation() {
        Account account = account(Account.AccountAction.itv);
        String json = "[{\"stream_id\":\"1\",\"name\":\"One\",\"category_id\":\"news\",\"stream_icon\":\"logo\"},"
                + "{\"stream_id\":\"2\",\"name\":\"Two\",\"category_id\":\"news\"}]";
        List<List<Channel>> batches = new ArrayList<>();
        List<Channel> channels = XtremeApiParser.readChannelCatalogue(new StringReader(json), account, batches::add, () -> false);

        assertEquals(2, channels.size());
        assertEquals(1, batches.size());
        assertEquals(2, batches.getFirst().size());
        assertEquals("http://provider.test/user/pass/1", channels.getFirst().getCmd());
        assertEquals("news", channels.getFirst().getCategoryId());

        assertTrue(XtremeApiParser.readChannelCatalogue(new StringReader("[]"), account, null, null).isEmpty());
        StringReader cancelledReader = new StringReader(json);
        assertThrows(CancellationException.class,
                () -> XtremeApiParser.readChannelCatalogue(cancelledReader, account, null, () -> true));
        StringReader invalidReader = new StringReader("{}");
        assertThrows(org.json.JSONException.class,
                () -> XtremeApiParser.readChannelCatalogue(invalidReader, account, null, null));
    }

    @Test
    void parsesCategoriesChannelsEpisodesAndRetriesAlternativeBaseUrls() throws Exception {
        Account account = account(Account.AccountAction.itv);
        account.setM3u8Path("provider.test/path");
        account.setUrl("http://fallback.test/root/player_api.php");
        HttpUtil.HttpResult notFound = new HttpUtil.HttpResult(404, "not found", Map.of(), Map.of());
        HttpUtil.HttpResult categories = new HttpUtil.HttpResult(200,
                "[{\"category_id\":\"1\",\"category_name\":\"News\"},"
                        + "{\"category_id\":\"2\",\"category_name\":\"LIVE | &#127875; Halloween Movies\"}]",
                Map.of(), Map.of());
        HttpUtil.HttpResult channels = new HttpUtil.HttpResult(200, "[{\"stream_id\":\"7\",\"name\":\"News&#039;s\",\"category_id\":\"1\"}]", Map.of(), Map.of());
        HttpUtil.HttpResult epg = new HttpUtil.HttpResult(200, "ok", Map.of(), Map.of());

        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.sendRequest(anyString(), isNull(), eq("GET"), isNull(), any(HttpUtil.RequestOptions.class)))
                    .thenAnswer(invocation -> {
                        String url = invocation.getArgument(0);
                        if (url.contains("provider.test")) return notFound;
                        if (url.contains("get_short_epg")) return epg;
                        if (url.contains("get_live_categories")) return categories;
                        return channels;
                    });
            List<Category> parsedCategories = XtremeApiParser.parseCategories(account);
            assertEquals("News", parsedCategories.getFirst().getTitle());
            assertEquals("LIVE | 🎃 Halloween Movies", parsedCategories.get(1).getTitle());
            List<Channel> parsed = XtremeApiParser.parseChannels("1", account, null, () -> false);
            assertEquals("7", parsed.getFirst().getChannelId());
            assertEquals("News's", parsed.getFirst().getName());
            assertEquals("http://provider.test/path/user/pass/7", parsed.getFirst().getCmd());
            assertEquals("ok", XtremeApiParser.fetchEpg(account, "get_short_epg", "7"));
        }

        assertThrows(java.io.IOException.class, () -> XtremeApiParser.fetchEpg(account, "unsupported", "7"));
        Account empty = account(Account.AccountAction.itv);
        empty.setUrl("");
        empty.setM3u8Path("");
        try (MockedStatic<HttpUtil> httpUtil = mockStatic(HttpUtil.class)) {
            assertThrows(java.io.UncheckedIOException.class, () -> XtremeApiParser.parseAllChannels(empty));
            httpUtil.reset();
        }
    }

    @Test
    void actionSpecificUrlsAndEpisodePayloadsCoverParserBranches() throws Exception {
        for (Account.AccountAction action : List.of(Account.AccountAction.itv, Account.AccountAction.vod, Account.AccountAction.series)) {
            Account account = account(action);
            String url = (String) invoke("buildXtremeStreamUrl", new Class[]{Account.class, String.class, String.class}, account, "42", "mp4");
            assertTrue(url.contains("42"));
            if (action == Account.AccountAction.vod) assertTrue(url.contains("/movie/"));
            if (action == Account.AccountAction.series) assertTrue(url.contains("/series/"));
        }
        assertEquals("http://provider.test/", invoke("normalizeBaseUrl", new Class[]{String.class}, "provider.test"));
        assertEquals("http://provider.test/", invoke("normalizeBaseUrl", new Class[]{String.class}, "http://provider.test/player_api.php"));
        assertEquals("", invoke("normalizeBaseUrl", new Class[]{String.class}, " "));
        assertEquals("http://provider.test/", invoke("normalizedBaseUrl", new Class[]{Account.class}, account(Account.AccountAction.itv)));
        assertFalse((Boolean) invoke("hasMoreBaseUrlCandidates", new Class[]{List.class, int.class}, List.of("one"), 0));
        assertTrue((Boolean) invoke("hasMoreBaseUrlCandidates", new Class[]{List.class, int.class}, List.of("one", "two"), 0));

        Account account = account(Account.AccountAction.series);
        String episodeJson = "{\"info\":{\"name\":\"Show\"},\"episodes\":{\"1\":[{\"id\":\"ep-1\",\"title\":\"Pilot\",\"season\":\"1\",\"episode_num\":\"1\"}],\"2\":[]}}";
        EpisodeList episodes = (EpisodeList) invoke("doParseEpisodes", new Class[]{String.class, Account.class}, episodeJson, account);
        assertEquals(1, episodes.getEpisodes().size());
        assertEquals("Pilot", episodes.getEpisodes().getFirst().getTitle());
        assertEquals(0, ((EpisodeList) invoke("doParseEpisodes", new Class[]{String.class, Account.class}, "{}", account)).getEpisodes().size());
    }

    private static Account account(Account.AccountAction action) {
        Account account = new Account();
        account.setAction(action);
        account.setUrl("http://provider.test");
        account.setUsername("user");
        account.setPassword("pass");
        return account;
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = XtremeApiParser.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause() instanceof Exception exception ? exception : failure;
        }
    }
}
