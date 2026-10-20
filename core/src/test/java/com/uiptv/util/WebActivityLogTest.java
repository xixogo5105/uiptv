package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebActivityLogTest {

    @AfterEach
    void tearDown() {
        WebActivityLog.clear();
    }

    @Test
    void recordRequest_writesFriendlyPlaybackEntryWithIp() {
        WebActivityLog.recordRequest(
                "GET",
                "/player/live",
                "mode=itv&name=BBC+One",
                "192.168.1.25",
                200,
                12
        );

        String text = WebActivityLog.readAllText();
        assertTrue(text.contains("IP 192.168.1.25"));
        assertTrue(text.contains("Played live channel \"BBC One\" in the web player"));
        assertTrue(text.contains("Result: completed"));
    }

    @Test
    void describeRequest_identifiesPublishedM3uAccess() {
        String description = WebActivityLog.describeRequest("GET", "/iptv.m3u", "");

        assertTrue(description.contains("published M3U playlist"));
        assertTrue(description.contains("iptv.m3u"));
    }

    @Test
    void recordRequest_usesResolvedActivityDescriptionWhenProvided() {
        WebActivityLog.recordRequest(
                "GET",
                "/bingwatch",
                "token=t&episodeId=ep-2",
                "127.0.0.1",
                307,
                3,
                WebActivityLog.describeBingeWatchEpisode("Episode Two", "1", "2")
        );

        String text = WebActivityLog.readAllText();
        assertTrue(text.contains("Played binge-watch episode \"Episode Two\" (Season 1, Episode 2)"));
        assertTrue(text.contains("Result: redirected"));
    }

    @Test
    void activityDescriptionHelpers_includeBingeAndPublishedM3uMetadata() {
        assertEquals(
                "Downloaded a binge-watch playlist starting with \"Pilot\" (Season 1, Episode 1) containing 4 episodes",
                WebActivityLog.describeBingeWatchPlaylist("Pilot", "1", "1", 4)
        );
        assertEquals(
                "Played published M3U entry \"News One\" from account \"Provider\" in category \"News\"",
                WebActivityLog.describePublishedM3uEntry("News One", "Provider", "News")
        );
    }

    @Test
    void clear_removesTemporaryEntries() {
        WebActivityLog.recordRequest("GET", "/playlist.m3u8", "accountId=1", "127.0.0.1", 200, 1);
        WebActivityLog.clear();

        assertTrue(WebActivityLog.readAllText().isBlank());
    }

    @Test
    void listener_receivesNewEntries() {
        AtomicReference<String> received = new AtomicReference<>();
        Consumer<String> listener = received::set;
        WebActivityLog.registerListener(listener);
        try {
            WebActivityLog.recordRequest("GET", "/bookmarks.m3u8", "", "127.0.0.1", 200, 1);
        } finally {
            WebActivityLog.unregisterListener(listener);
        }

        assertTrue(received.get().contains("bookmarks M3U playlist"));
    }

    @Test
    void describeRequest_coversWebRoutesAndQueryScopes() {
        assertEquals("Opened the UIPTV web app", WebActivityLog.describeRequest(null, "", null));
        assertEquals("Opened the UIPTV web app", WebActivityLog.describeRequest("GET", "drm.html", ""));
        assertEquals("", WebActivityLog.describeRequest("GET", "/javascript/app.js", ""));
        assertEquals("Downloaded a playlist file: playlist.m3u8 for one channel",
                WebActivityLog.describeRequest("GET", "/playlist.m3u8", "channelId=channel-1"));
        assertEquals("Downloaded a playlist file: playlist.m3u8 for one category",
                WebActivityLog.describeRequest("GET", "/playlist.m3u8", "categoryId=category-1"));
        assertEquals("Downloaded a playlist file: playlist.m3u8 for one account",
                WebActivityLog.describeRequest("GET", "/playlist.m3u8", "accountId=account-1"));
        assertEquals("Played movie or video \"Film\" in the web player",
                WebActivityLog.describeRequest("GET", "/player/vod", "name=Film"));
        assertEquals("Played series episode \"Episode\" in the web player",
                WebActivityLog.describeRequest("GET", "/player/series", "name=Episode"));
        assertEquals("Played a bookmarked item in the web player",
                WebActivityLog.describeRequest("GET", "/player", "bookmarkId=bookmark-1"));
        assertEquals("Started web playback \"Unknown\"",
                WebActivityLog.describeRequest("GET", "/player", "name=Unknown"));
        assertEquals("Streamed media through the web player from media.test/movie.mp4",
                WebActivityLog.describeRequest("GET", "/proxy-stream", "src=https%3A%2F%2Fmedia.test%2Fmovie.mp4"));
        assertEquals("Streamed media through the web player from media.test",
                WebActivityLog.describeRequest("GET", "/proxy-stream", "src=https%3A%2F%2Fmedia.test"));
        assertEquals("Streamed media through the web player from not-a-uri",
                WebActivityLog.describeRequest("GET", "/proxy-stream", "src=not-a-uri"));
        assertEquals("Downloaded a remote sync database snapshot",
                WebActivityLog.describeRequest("GET", "/remote-sync/download", ""));
        assertEquals("Uploaded a remote sync database snapshot",
                WebActivityLog.describeRequest("GET", "/remote-sync/upload", ""));
        assertEquals("Requested remote sync access",
                WebActivityLog.describeRequest("GET", "/remote-sync/request", ""));
        assertEquals("Checked remote sync status",
                WebActivityLog.describeRequest("GET", "/remote-sync/status", ""));
        assertEquals("Completed a remote sync session",
                WebActivityLog.describeRequest("GET", "/remote-sync/complete", ""));
        assertEquals("Loaded accounts in the web app", WebActivityLog.describeRequest("GET", "/accounts", ""));
        assertEquals("Loaded categories in the web app", WebActivityLog.describeRequest("GET", "/categories", ""));
        assertEquals("Loaded channels in the web app", WebActivityLog.describeRequest("GET", "/channels", ""));
        assertEquals("Loaded series episodes in the web app", WebActivityLog.describeRequest("GET", "/seriesEpisodes", ""));
        assertEquals("Loaded series details in the web app", WebActivityLog.describeRequest("GET", "/seriesDetails", ""));
        assertEquals("Loaded movie details in the web app", WebActivityLog.describeRequest("GET", "/vodDetails", ""));
        assertEquals("Updated the watching-now list in the web app", WebActivityLog.describeRequest("GET", "/watchingNow", ""));
        assertEquals("Used bookmarks in the web app", WebActivityLog.describeRequest("GET", "/bookmarks", ""));
        assertEquals("Loaded web app configuration", WebActivityLog.describeRequest("GET", "/config", ""));
        assertEquals("POST request - Opened /unknown", WebActivityLog.describeRequest("POST", "/unknown", ""));
    }

    @Test
    void descriptionsAndResults_handleOptionalValuesAndStatusRanges() {
        assertEquals("Downloaded a binge-watch playlist", WebActivityLog.describeBingeWatchPlaylist("", "", "", 0));
        assertEquals("Downloaded a binge-watch playlist containing 1 episode",
                WebActivityLog.describeBingeWatchPlaylist("", "", "", 1));
        assertEquals("Played binge-watch episode Season 2, Episode 3",
                WebActivityLog.describeBingeWatchEpisode("", "2", "3"));
        assertEquals("Played published M3U entry", WebActivityLog.describePublishedM3uEntry("", "", ""));
        assertEquals("Result: completed", resultFor(200));
        assertEquals("Result: redirected", resultFor(302));
        assertEquals("Result: not found", resultFor(404));
        assertEquals("Result: rejected (401)", resultFor(401));
        assertEquals("Result: failed (503)", resultFor(503));
        assertEquals("Result: unknown", resultFor(199));
        WebActivityLog.clear();
        WebActivityLog.recordRequest("GET", "/icon.ico", "", "", 200, -1);
        assertTrue(WebActivityLog.readAllText().isBlank());
    }

    @Test
    void listenersThatFailDoNotPreventLoggingAndFileEntriesCanBeRead() throws Exception {
        Consumer<String> failing = _ -> { throw new IllegalStateException("listener failure"); };
        WebActivityLog.registerListener(failing);
        try {
            WebActivityLog.recordRequest("GET", "/", "", null, 200, 1);
            assertTrue(WebActivityLog.readAllText().contains("Opened the UIPTV web app"));
        } finally {
            WebActivityLog.unregisterListener(failing);
        }

        WebActivityLog.clear();
        Files.writeString(WebActivityLog.getLogFilePath(), "persisted entry" + System.lineSeparator());
        assertEquals("persisted entry" + System.lineSeparator(), WebActivityLog.readAllText());
    }

    private String resultFor(int status) {
        WebActivityLog.recordRequest("GET", "/", "", "127.0.0.1", status, 0);
        String result = WebActivityLog.readAllText().lines().reduce((first, second) -> second).orElse("");
        return result.substring(result.indexOf("Result:"), result.indexOf(" | ", result.indexOf("Result:")));
    }
}
