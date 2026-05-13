package com.uiptv.application;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackApplicationServiceTest {

    @Test
    void buildPlaylist_decodesCommandRefreshesTokenAndRestoresOriginalCommand() throws Exception {
        Account account = new Account("Demo Account", "user", "pass", "http://demo", null, null, null, null, null, null,
                AccountType.XTREME_API, null, null, false);
        account.setDbId("acc-1");

        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName("Movie \"One\", HD");
        channel.setCmd("http%3A%2F%2Fprovider.test%2Fencoded.m3u8");

        AccountService accountService = Mockito.mock(AccountService.class);
        ChannelDb channelDb = Mockito.mock(ChannelDb.class);
        HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
        PlayerService playerService = Mockito.mock(PlayerService.class);

        try (MockedStatic<AccountService> accountStatic = Mockito.mockStatic(AccountService.class);
             MockedStatic<ChannelDb> channelDbStatic = Mockito.mockStatic(ChannelDb.class);
             MockedStatic<HandshakeService> handshakeStatic = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<PlayerService> playerStatic = Mockito.mockStatic(PlayerService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            channelDbStatic.when(ChannelDb::get).thenReturn(channelDb);
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(handshakeService);
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);
            Mockito.when(accountService.getById("acc-1")).thenReturn(account);
            Mockito.when(channelDb.getChannelById("ch-1", "cat-1")).thenReturn(channel);
            Mockito.when(playerService.get(account, channel)).thenReturn(new PlayerResponse("http://stream.test/live.m3u8"));

            PlaybackPlaylistResult result = PlaybackApplicationService.getInstance()
                    .buildPlaylist(new PlaybackPlaylistRequest("acc-1", "cat-1", "ch-1"));

            Mockito.verify(handshakeService).hardTokenRefresh(account);
            Mockito.verify(playerService).get(account, channel);
            assertEquals("http%3A%2F%2Fprovider.test%2Fencoded.m3u8", channel.getCmd());
            assertEquals("acc-1-cat-1-ch-1.m3u8", result.filename());
            assertTrue(result.body().startsWith("#EXTM3U\n#EXTINF:-1"));
            assertTrue(result.body().contains("tvg-id=\"acc-1\""));
            assertTrue(result.body().contains("group-title=\"Demo Account\""));
            assertTrue(result.body().contains("http://stream.test/live.m3u8"));
        }
    }
}
