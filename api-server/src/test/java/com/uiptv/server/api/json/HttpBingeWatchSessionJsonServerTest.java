package com.uiptv.server.api.json;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.testsupport.DbBackedTest;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class HttpBingeWatchSessionJsonServerTest extends DbBackedTest {
    @Test
    void rejectsUnsupportedMethodsAndIncompletePayloads() throws Exception {
        HttpBingeWatchSessionJsonServer handler = new HttpBingeWatchSessionJsonServer();
        TestHttpExchange get = new TestHttpExchange("/bingeWatchSession", "GET");
        handler.handle(get);
        assertEquals(405, get.getResponseCode());

        TestHttpExchange missing = new TestHttpExchange("/bingeWatchSession", "POST", "{}");
        handler.handle(missing);
        assertEquals(400, missing.getResponseCode());
    }

    @Test
    void reportsMissingAccountsAndFailedSessionPreparation() throws Exception {
        HttpBingeWatchSessionJsonServer handler = new HttpBingeWatchSessionJsonServer();
        String body = requestBody("missing", "series", "1");
        AccountService accounts = mock(AccountService.class);
        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accounts);
            when(accounts.getById("missing")).thenReturn(null);
            TestHttpExchange exchange = new TestHttpExchange("/bingeWatchSession", "POST", body);
            handler.handle(exchange);
            assertEquals(404, exchange.getResponseCode());
        }
    }

    @Test
    void createsSessionAndReturnsPlaylistUrl() throws Exception {
        HttpBingeWatchSessionJsonServer handler = new HttpBingeWatchSessionJsonServer();
        Account account = new Account();
        account.setDbId("account-1");
        Channel episode = new Channel("episode-1", "Episode 1", "1", "cmd://episode", null, null, null,
                "", 0, 1, 1, null, null, null, null, null);
        AccountService accounts = mock(AccountService.class);
        SeriesWatchStateService watchStates = mock(SeriesWatchStateService.class);
        BingeWatchService bingeWatch = mock(BingeWatchService.class);
        when(accounts.getById("account-1")).thenReturn(account);
        when(watchStates.getSeriesLastWatched("account-1", "category", "series")).thenReturn(null);
        when(bingeWatch.createSession(any(), eq("series"), eq("category"), eq("1"), any(), eq(null))).thenReturn("token");
        when(bingeWatch.buildPlaylistUrl("token")).thenReturn("http://localhost/bingewatch.m3u8?token=token");

        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<SeriesWatchStateService> watchStatic = mockStatic(SeriesWatchStateService.class);
             MockedStatic<BingeWatchService> bingeStatic = mockStatic(BingeWatchService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accounts);
            watchStatic.when(SeriesWatchStateService::getInstance).thenReturn(watchStates);
            bingeStatic.when(BingeWatchService::getInstance).thenReturn(bingeWatch);
            TestHttpExchange exchange = new TestHttpExchange("/bingeWatchSession", "POST",
                    requestBody("account-1", "series", "1", "category", episode));
            handler.handle(exchange);
            assertEquals(200, exchange.getResponseCode());
            JSONObject response = new JSONObject(exchange.getResponseBodyText());
            assertEquals("ok", response.getString("status"));
            assertEquals("token", response.getString("token"));
            assertEquals("http://localhost/bingewatch.m3u8?token=token", response.getString("playlistUrl"));
        }
    }

    @Test
    void reportsWhenTheServiceCannotCreateASession() throws Exception {
        HttpBingeWatchSessionJsonServer handler = new HttpBingeWatchSessionJsonServer();
        Account account = new Account();
        account.setDbId("account-2");
        Channel episode = new Channel("episode-2", "Episode 2", "1", "cmd://episode", null, null, null,
                "", 0, 1, 1, null, null, null, null, null);
        AccountService accounts = mock(AccountService.class);
        SeriesWatchStateService watchStates = mock(SeriesWatchStateService.class);
        BingeWatchService bingeWatch = mock(BingeWatchService.class);
        when(accounts.getById("account-2")).thenReturn(account);
        when(bingeWatch.createSession(any(), eq("series"), eq(""), eq("1"), any(), eq(null))).thenReturn("");
        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<SeriesWatchStateService> watchStatic = mockStatic(SeriesWatchStateService.class);
             MockedStatic<BingeWatchService> bingeStatic = mockStatic(BingeWatchService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accounts);
            watchStatic.when(SeriesWatchStateService::getInstance).thenReturn(watchStates);
            bingeStatic.when(BingeWatchService::getInstance).thenReturn(bingeWatch);
            TestHttpExchange exchange = new TestHttpExchange("/bingeWatchSession", "POST",
                    requestBody("account-2", "series", "1", "", episode));
            handler.handle(exchange);
            assertEquals(422, exchange.getResponseCode());
        }
    }

    private static String requestBody(String accountId, String seriesId, String season) {
        return requestBody(accountId, seriesId, season, "category", new Channel("episode", "Episode", "1", "cmd://episode",
                null, null, null, "", 0, 1, 1, null, null, null, null, null));
    }

    private static String requestBody(String accountId, String seriesId, String season, String categoryId, Channel episode) {
        return new JSONObject()
                .put("accountId", accountId)
                .put("seriesId", seriesId)
                .put("season", season)
                .put("categoryId", categoryId)
                .put("episodes", List.of(episode.toJson()))
                .toString();
    }
}
