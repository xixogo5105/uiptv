package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackUIServiceTest {

    @Test
    void configuredPlayerOptionsAppendNamesFromPortablePaths() {
        Configuration configuration = new Configuration();
        configuration.setPlayerPath1("/usr/lib/mpv");
        configuration.setPlayerPath2("C:\\Program Files\\VLC\\vlc.exe");
        configuration.setPlayerPath3("");

        var options = PlaybackUIService.getConfiguredPlayerOptions(configuration);

        assertEquals(I18n.tr("autoPlayer1") + " (mpv)", options.get(2).label());
        assertEquals(I18n.tr("autoPlayer2") + " (vlc)", options.get(3).label());
        assertEquals(I18n.tr("autoPlayer3"), options.get(4).label());
        assertEquals("/usr/lib/mpv", options.get(2).playerPath());
        assertEquals("C:\\Program Files\\VLC\\vlc.exe", options.get(3).playerPath());
    }

    @Test
    void playbackRequest_defaultsAreApplied() throws Exception {
        PlaybackUIService.PlaybackRequest request = new PlaybackUIService.PlaybackRequest(sampleAccount(), sampleChannel(), null);

        assertEquals("", readStringField(request, "categoryId"));
        assertEquals("", readStringField(request, "channelId"));
        assertEquals("", readStringField(request, "seriesId"));
        assertEquals("", readStringField(request, "seriesCategoryId"));
        assertTrue(readBooleanField(request, "allowDrmBrowserFallback"));
        assertEquals("Playback failed: ", readStringField(request, "errorPrefix"));
    }

    @Test
    void playbackRequest_builderMethodsOverrideDefaults() throws Exception {
        PlaybackUIService.PlaybackRequest request = new PlaybackUIService.PlaybackRequest(sampleAccount(), sampleChannel(), "embedded")
                .categoryId("cat-7")
                .channelId("ep-3")
                .series("episode-3", "series-12")
                .allowDrmBrowserFallback(false)
                .errorPrefix("Error playing episode: ");

        assertEquals("cat-7", readStringField(request, "categoryId"));
        assertEquals("ep-3", readStringField(request, "channelId"));
        assertEquals("episode-3", readStringField(request, "seriesId"));
        assertEquals("series-12", readStringField(request, "seriesCategoryId"));
        assertFalse(readBooleanField(request, "allowDrmBrowserFallback"));
        assertEquals("Error playing episode: ", readStringField(request, "errorPrefix"));
    }

    @Test
    void playbackRequestKeepsSnapshotModeWhenSourceAccountChanges() {
        Account account = sampleAccount();
        PlaybackUIService.PlaybackRequest request = new PlaybackUIService.PlaybackRequest(
                AccountMediaContext.from(account, Account.AccountAction.vod),
                sampleChannel(),
                "embedded");

        account.setAccountName("changed-account");
        account.setAction(Account.AccountAction.series);

        Account playbackAccount = request.accountForPlayback();

        assertEquals("test-account", playbackAccount.getAccountName());
        assertEquals(Account.AccountAction.vod, playbackAccount.getAction());
        assertEquals(Account.AccountAction.series, account.getAction());
    }

    @Test
    void isEmbeddedPlayerPath_acceptsStableMarker() {
        assertTrue(PlaybackUIService.isEmbeddedPlayerPath(PlaybackUIService.EMBEDDED_PLAYER_PATH));
    }

    @Test
    void isEmbeddedPlayerPath_acceptsEmbeddedLabel() {
        assertTrue(PlaybackUIService.isEmbeddedPlayerPath("Embedded Player (Using VLC)"));
    }

    @Test
    void isEmbeddedPlayerPath_rejectsExecutablePath() {
        assertFalse(PlaybackUIService.isEmbeddedPlayerPath("/usr/bin/vlc"));
    }

    @Test
    void resolvedDoubleClickPlaybackDoesNotTearDownThePreparedPlayerAgain() throws Exception {
        com.uiptv.testsupport.FxTestSupport.initJavaFx();
        com.uiptv.testsupport.FxTestSupport.runOnFxThread(() -> {
            var player = org.mockito.Mockito.mock(com.uiptv.player.api.VideoPlayerInterface.class);
            try (var factory = org.mockito.Mockito.mockStatic(com.uiptv.player.MediaPlayerFactory.class)) {
                factory.when(com.uiptv.player.MediaPlayerFactory::getPlayer).thenReturn(player);
                factory.when(com.uiptv.player.MediaPlayerFactory::getPlayerType)
                        .thenReturn(com.uiptv.player.api.VideoPlayerInterface.PlayerType.VLC);
                var configuration = new com.uiptv.model.Configuration();
                configuration.setEmbeddedPlayer(true);
                var request = new PlaybackUIService.PlaybackRequest(sampleAccount(), sampleChannel(), PlaybackUIService.EMBEDDED_PLAYER_PATH);
                var build = PlaybackUIService.class.getDeclaredMethod("buildPlaybackModeContext", com.uiptv.model.Configuration.class,
                        PlaybackUIService.PlaybackRequest.class, Account.class);
                build.setAccessible(true);
                var context = build.invoke(null, configuration, request, request.accountForPlayback());
                var launch = PlaybackUIService.class.getDeclaredMethod("launchResolvedPlayback", context.getClass(),
                        PlaybackUIService.PlaybackRequest.class, com.uiptv.model.PlayerResponse.class);
                launch.setAccessible(true);
                var response = new com.uiptv.model.PlayerResponse("http://example.test/live.m3u8?token=fresh");
                launch.invoke(null, context, request, response);
                org.mockito.Mockito.verify(player).play(response);
                org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).stopForReload();
            }
            return null;
        });
    }

    private String readStringField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return value == null ? "" : value.toString();
    }

    private boolean readBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private Account sampleAccount() {
        Account account = new Account(
                "test-account",
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
        return account;
    }

    private Channel sampleChannel() {
        Channel channel = new Channel();
        channel.setChannelId("episode-1");
        channel.setName("Episode 1");
        channel.setCmd("http://127.0.0.1/media/episode-1.m3u8");
        return channel;
    }
}
