package com.uiptv.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCoverageTest {

    @Test
    void categoryFromJson_parsesFields_andRejectsInvalidJson() {
        String json = """
                {
                  "dbId":"7",
                  "accountId":"42",
                  "accountType":"XTREME_API",
                  "categoryId":"55",
                  "title":"Movies",
                  "alias":"movies",
                  "extraJson":"{}",
                  "activeSub":true,
                  "censored":1
                }
                """;

        Category category = Category.fromJson(json);

        assertNotNull(category);
        assertEquals("7", category.getDbId());
        assertEquals("42", category.getAccountId());
        assertEquals("XTREME_API", category.getAccountType());
        assertEquals("55", category.getCategoryId());
        assertEquals("Movies", category.getTitle());
        assertEquals("movies", category.getAlias());
        assertEquals("{}", category.getExtraJson());
        assertTrue(category.isActiveSub());
        assertEquals(1, category.getCensored());
        assertNull(Category.fromJson("{not-json"));
    }

    @Test
    void channelHelpers_coverJsonParsing_clearKeys_andSeasonEpisodeComparison() {
        Channel watchedBoolean = Channel.fromJson("""
                {
                  "dbId":"1",
                  "channelId":"c1",
                  "name":"Season 2 - Episode 9",
                  "watched":true,
                  "manifestType":"hls"
                }
                """);
        Channel watchedString = Channel.fromJson("""
                {
                  "channelId":"c2",
                  "name":"Season 4 - Episode 6",
                  "watched":"1"
                }
                """);
        Channel invalid = Channel.fromJson("{oops");

        Channel channel = new Channel("c3", "Season 3 - Episode 7", "7", "cmd", null, null, null, "logo", 0, 1, 1,
                "widevine", "http://license", Map.of("kid", "key"), "inputstream.adaptive", "dash");

        assertNotNull(watchedBoolean);
        assertTrue(watchedBoolean.isWatched());
        assertEquals("hls", watchedBoolean.getManifestType());
        assertNotNull(watchedString);
        assertTrue(watchedString.isWatched());
        assertNull(invalid);

        assertEquals("{\"kid\":\"key\"}", channel.getClearKeysJson());
        assertEquals(3, channel.getCompareSeason());
        assertEquals(7, channel.getCompareEpisode());

        channel.setName("not-a-season");
        channel.setClearKeys(Map.of());
        assertEquals(0, channel.getCompareSeason());
        assertEquals(0, channel.getCompareEpisode());
        assertNull(channel.getClearKeysJson());
    }

    @Test
    void playerResponse_copiesDrmMetadata_fromChannelAndBookmark_andClearsOnNull() {
        Account account = new Account();
        account.setAccountName("demo");

        Channel channel = new Channel();
        channel.setDrmType("widevine");
        channel.setDrmLicenseUrl("http://license");
        channel.setClearKeysJson("{\"kid\":\"key\"}");
        channel.setInputstreamaddon("inputstream.adaptive");
        channel.setManifestType("dash");

        Bookmark bookmark = new Bookmark();
        bookmark.setDrmType("playready");
        bookmark.setDrmLicenseUrl("http://bookmark-license");
        bookmark.setClearKeysJson("{\"other\":\"value\"}");
        bookmark.setInputstreamaddon("bookmark-addon");
        bookmark.setManifestType("hls");

        PlayerResponse response = new PlayerResponse("http://stream");
        response.setFromChannel(channel, account);
        assertEquals(account, response.getAccount());
        assertEquals(channel, response.getChannel());
        assertEquals("widevine", response.getDrmType());
        assertEquals("http://license", response.getDrmLicenseUrl());
        assertEquals("{\"kid\":\"key\"}", response.getClearKeysJson());
        assertEquals("inputstream.adaptive", response.getInputstreamaddon());
        assertEquals("dash", response.getManifestType());

        response.setFromBookmark(bookmark, account);
        assertEquals("playready", response.getDrmType());
        assertEquals("http://bookmark-license", response.getDrmLicenseUrl());
        assertEquals("{\"other\":\"value\"}", response.getClearKeysJson());
        assertEquals("bookmark-addon", response.getInputstreamaddon());
        assertEquals("hls", response.getManifestType());

        response.setFromChannel(null, account);
        assertNull(response.getDrmType());
        assertNull(response.getDrmLicenseUrl());
        assertNull(response.getClearKeysJson());
        assertNull(response.getInputstreamaddon());
        assertNull(response.getManifestType());

        response.setFromBookmark(null, account);
        assertNull(response.getDrmType());
        assertFalse(response.getUrl().isBlank());
    }
}
