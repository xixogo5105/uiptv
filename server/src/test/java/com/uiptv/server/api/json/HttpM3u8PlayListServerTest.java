package com.uiptv.server.api.json;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpM3u8PlayListServerTest {

    @Test
    void handle_buildsPlaylistAndRestoresCommand() throws Exception {
        Account account = new Account("acc", "user", "pass", "http://portal", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://portal/list.m3u8", false);
        account.setDbId("acc-1");
        account.setAccountName("Account \"Name\"");

        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName("Channel \"One\"");
        channel.setCmd("http%3A%2F%2Forigin%2Fstream.ts");

        AccountService accountService = Mockito.mock(AccountService.class);
        ChannelDb channelDb = Mockito.mock(ChannelDb.class);
        PlayerService playerService = Mockito.mock(PlayerService.class);
        HandshakeService handshakeService = Mockito.mock(HandshakeService.class);

        try (MockedStatic<AccountService> accountStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<ChannelDb> channelStatic = Mockito.mockStatic(ChannelDb.class);
             MockedStatic<PlayerService> playerStatic = Mockito.mockStatic(PlayerService.class);
             MockedStatic<HandshakeService> handshakeStatic = Mockito.mockStatic(HandshakeService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            channelStatic.when(ChannelDb::get).thenReturn(channelDb);
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(handshakeService);

            Mockito.when(accountService.getById("acc-1")).thenReturn(account);
            Mockito.when(channelDb.getChannelById("ch-1", "cat-1")).thenReturn(channel);
            Mockito.when(playerService.get(account, channel))
                    .thenReturn(new PlayerResponse("http://origin/stream.ts"));

            HttpM3u8PlayListServer handler = new HttpM3u8PlayListServer();
            TestHttpExchange exchange = new TestHttpExchange("/playlist.m3u8?accountId=acc-1&categoryId=cat-1&channelId=ch-1", "GET");
            handler.handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().contains("#EXTM3U"));
            assertTrue(exchange.getResponseBodyText().contains("http://origin/stream.ts"));
            assertTrue(exchange.getResponseBodyText().contains("tvg-name=\"Channel 'One'\""));
            assertTrue(exchange.getResponseBodyText().contains("group-title=\"Account 'Name'\""));
            assertEquals("http%3A%2F%2Forigin%2Fstream.ts", channel.getCmd());
        }
    }
}
