package com.uiptv.shared;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedCoverageTest {

    @Test
    void baseJsonToJson_serializesBooleanFieldsAsOneOrZero() {
        Channel channel = new Channel();
        channel.setChannelId("10");
        channel.setName("Demo");
        channel.setWatched(true);

        String json = channel.toJson();

        assertTrue(json.contains("\"channelId\":\"10\""));
        assertTrue(json.contains("\"watched\":\"1\""));
        assertTrue(channel.toString().contains("channelId=10"));
    }

    @Test
    void episodeInfoAndEpisode_coverFallbackArtwork_andJsonParsing() {
        Account account = new Account("demo", "user", "pass", "http://host", "00:11:22:33:44:55",
                null, null, null, null, null, AccountType.XTREME_API, null, "http://host/", false);
        account.setAction(Account.AccountAction.series);

        Episode episode = new Episode(account, Map.of(
                "id", "55",
                "episode_num", "4",
                "title", "Pilot",
                "container_extension", "mkv",
                "movie_image", "http://img/root.jpg",
                "info", Map.of(
                        "tmdb", "123",
                        "overview", "Plot",
                        "durationSeconds", "3600",
                        "runtime", "60m",
                        "rating_imdb", "8.8",
                        "video", Map.of(),
                        "audio", Map.of()
                )
        ));

        assertEquals("55", episode.getId());
        assertEquals("4", episode.getEpisodeNum());
        assertEquals("Pilot", episode.getTitle());
        assertEquals("http://img/root.jpg", episode.getInfo().getMovieImage());
        assertEquals("123", episode.getInfo().getTmdbId());
        assertEquals("Plot", episode.getInfo().getPlot());
        assertEquals("3600", episode.getInfo().getDurationSecs());
        assertEquals("60m", episode.getInfo().getDuration());
        assertEquals("8.8", episode.getInfo().getRating());
        assertEquals("http://host/series/user/pass/55.mkv", episode.getCmd());

        Episode parsed = Episode.fromJson("""
                {
                  "id":"88",
                  "episodeNum":"9",
                  "title":"Parsed",
                  "containerExtension":"mp4",
                  "cmd":"http://stream",
                  "info":{"movieImage":"http://img/info.jpg"},
                  "thumbnail":"http://img/root-ignored.jpg"
                }
                """);

        assertNotNull(parsed);
        assertEquals("88", parsed.getId());
        assertEquals("9", parsed.getEpisodeNum());
        assertEquals("http://stream", parsed.getCmd());
        assertEquals("http://img/info.jpg", parsed.getInfo().getMovieImage());
        assertNull(Episode.fromJson("{bad-json"));
    }

    @Test
    void playlistEntryAndBookmark_coverAccessors_andDefaultGroupTitle() {
        PlaylistEntry entry = new PlaylistEntry("1", " ", "News", "http://stream", "logo");
        assertEquals(CategoryType.ALL.displayName(), entry.getGroupTitle());
        assertEquals("http://stream", entry.getPlaylistEntry());

        entry.setPlaylistEntry("http://updated");
        assertEquals("http://updated", entry.getSourceUrl());

        Bookmark bookmark = new Bookmark();
        Channel channel = new Channel();
        channel.setLogo("http://img/logo.png");
        channel.setDrmType("widevine");
        channel.setDrmLicenseUrl("http://license");
        channel.setClearKeysJson("{\"kid\":\"key\"}");
        channel.setManifestType("dash");
        channel.setInputstreamaddon("inputstream.adaptive");

        bookmark.setFromChannel(channel);
        assertEquals("http://img/logo.png", bookmark.getLogo());
        assertEquals("widevine", bookmark.getDrmType());
        assertEquals("http://license", bookmark.getDrmLicenseUrl());
        assertEquals("{\"kid\":\"key\"}", bookmark.getClearKeysJson());
        assertEquals("dash", bookmark.getManifestType());
        assertEquals("inputstream.adaptive", bookmark.getInputstreamaddon());
    }
}
