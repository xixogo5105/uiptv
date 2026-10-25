package com.uiptv.util;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.ConfigurationDb;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.json.*;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompactChannelImportTest extends DbBackedTest {
    private JSONObject row() {
        return new JSONObject().put("id", "provider-7").put("name", "Station").put("number", "7")
                .put("cmd", "http://example.test/live").put("cmd_1", "alternate1").put("cmd_2", "alternate2")
                .put("cmd_3", "alternate3").put("logo", "http://example.test/logo")
                .put("tv_genre_id", "genre").put("xmltv_id", "guide7").put("genre_name", "News")
                .put("stream_icon", "http://example.test/alternate-logo").put("plot", "A plot")
                .put("release_date", "2026").put("rating", "8").put("duration", "60")
                .put("cmds", new JSONArray().put(new JSONObject().put("url", "ignored")))
                .put("unknown", "x".repeat(50_000));
    }

    @Test void liveReloadPersistsCompactMetadataWithoutChangingDedicatedPlaybackColumns() {
        Account account = account();
        AccountService.getInstance().save(account);
        CategoryDb.get().saveAll(List.of(new Category("genre", "News", "News", false, 0)), account);
        Category category = CategoryDb.get().getCategories(account).getFirst();
        Channel channel = ChannelService.getInstance().parseItvChannel(row());
        assertEquals("genre", channel.getCategoryId());
        ChannelDb.get().saveAll(List.of(channel), category.getDbId(), account);
        Channel saved = ChannelDb.get().getChannels(category.getDbId()).getFirst();
        assertEquals("provider-7", saved.getChannelId());
        assertEquals("Station", saved.getName());
        assertEquals("http://example.test/live", saved.getCmd());
        assertEquals("alternate1", saved.getCmd_1());
        assertEquals("alternate2", saved.getCmd_2());
        assertEquals("alternate3", saved.getCmd_3());
        assertEquals("http://example.test/logo", saved.getLogo());
        assertCompact(saved);
        assertEquals("guide7", new JSONObject(saved.getExtraJson()).getString("xmltv_id"));

        ConfigurationDb.get().clearCache(account);
        CategoryDb.get().saveAll(List.of(new Category("genre", "News", "News", false, 0)), account);
        Category reloaded = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(ChannelService.getInstance().parseItvChannel(row())), reloaded.getDbId(), account);
        assertEquals(1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertCompact(ChannelDb.get().getChannels(reloaded.getDbId()).getFirst());
    }

    @Test void vodAndExpandedSeriesKeepMetadataAndLogoFallbackWithoutRepeatedParentPayload() {
        Account account = account();
        account.setAction(Account.AccountAction.vod);
        JSONObject provider = row().put("screenshot_uri", "http://example.test/vod-image");
        String payload = new JSONObject().put("js", new JSONObject().put("data", new JSONArray().put(provider))).toString();
        Channel vod = ChannelService.getInstance().parseVodChannels(account, payload, false).getFirst();
        assertEquals("http://example.test/vod-image", vod.getLogo());
        assertEquals("A plot", new WatchingNowVodResolver().resolveMetadata(vod).getPlot());
        assertCompact(vod);
        account.setAction(Account.AccountAction.series);
        provider.put("series", new JSONArray().put("episode-1").put("episode-2"));
        payload = new JSONObject().put("js", new JSONObject().put("data", new JSONArray().put(provider))).toString();
        var episodes = ChannelService.getInstance().parseVodChannels(account, payload, false);
        assertEquals(2, episodes.size());
        assertEquals(List.of("episode-1", "episode-2"), episodes.stream().map(Channel::getChannelId).toList());
        for (Channel episode : episodes) {
            assertEquals(provider.getString("cmd"), episode.getCmd());
            assertEquals("http://example.test/vod-image", episode.getLogo());
            assertCompact(episode);
            assertFalse(new JSONObject(episode.getExtraJson()).has("series"));
        }
    }

    @Test void xtremeCatalogueKeepsEpgImagesAndMetadataAndGeneratedCommands() {
        Account account = account(); account.setType(AccountType.XTREME_API);
        JSONObject provider = row().put("stream_id", "42").put("category_id", "cat")
                .put("epg_channel_id", "guide42").put("container_extension", "ts");
        Channel channel = XtremeApiParser.readChannelCatalogue(new StringReader(new JSONArray().put(provider).toString()), account, null, () -> false).getFirst();
        assertEquals("42", channel.getChannelId());
        assertEquals("cat", channel.getCategoryId());
        assertTrue(channel.getCmd().contains("42"));
        assertEquals("http://example.test/alternate-logo", channel.getLogo());
        assertEquals("guide42", new JSONObject(channel.getExtraJson()).getString("epg_channel_id"));
        assertCompact(channel);
    }

    private void assertCompact(Channel channel) {
        JSONObject json = new JSONObject(channel.getExtraJson());
        for (String key : List.of("cmds", "cmd", "cmd_1", "cmd_2", "cmd_3", "unknown")) assertFalse(json.has(key), key);
        assertTrue(channel.getExtraJson().length() < 1500);
    }

    private Account account() {
        Account account = new Account(); account.setAccountName("compact-import");
        account.setUrl("http://example.test/"); account.setUsername("user"); account.setPassword("pass");
        account.setType(AccountType.STALKER_PORTAL); account.setAction(Account.AccountAction.itv);
        return account;
    }
}
