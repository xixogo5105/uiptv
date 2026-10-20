package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class EpgApplicationServiceTest extends DbBackedTest {
    @org.junit.jupiter.api.BeforeEach void enableEpg() {
        Configuration config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true); ConfigurationService.getInstance().save(config);
    }

    @Test void playerGuideResolvesLocalIdsAndBookmarkSourcesWithoutCrossAccountLeakage() throws Exception {
        Account first = saveCatalog("Player", "First channel");
        Account second = saveCatalog("Other", "Second channel");
        Channel channel = ChannelService.getInstance().getChannelByChannelIdAndAccount("guide-id", first.getDbId(), false);
        PlayerEpgApplicationService service = new PlayerEpgApplicationService();
        var result = service.lookup(new PlayerEpgApplicationService.Query(first.getDbId(), channel.getDbId(), channel.getCategoryId(), ""));
        assertEquals("First channel", result.channelName()); assertEquals("Current", result.now().title()); assertEquals("Next", result.next().title());
        assertNull(service.lookup(new PlayerEpgApplicationService.Query(second.getDbId(), channel.getDbId(), channel.getCategoryId(), "")).now());
        assertNull(service.lookup(new PlayerEpgApplicationService.Query(first.getDbId(), channel.getChannelId(), channel.getCategoryId(), "")).now());
        Bookmark bookmark = new Bookmark(second.getAccountName(), "Group", "guide-id", "Second channel", "", "", "");
        bookmark.setAccountAction(Account.AccountAction.itv); BookmarkService.getInstance().save(bookmark);
        bookmark = BookmarkService.getInstance().read().getFirst();
        result = service.lookup(new PlayerEpgApplicationService.Query("", "", "", bookmark.getDbId()));
        assertEquals("Second channel", result.channelName()); assertEquals("Current", result.now().title());
        var bookmarkRow = new LiveGuideApplicationService().bookmarkRow(bookmark.getDbId());
        assertEquals(second.getDbId(), bookmarkRow.accountId());
        assertEquals("guide-id", bookmarkRow.channel().getChannelId());
        assertEquals("Current", new LiveGuideApplicationService().hydrate(bookmarkRow).programmes().getFirst().title());
        assertNull(new LiveGuideApplicationService().bookmarkRow("missing-local-bookmark"));
        java.util.concurrent.atomic.AtomicBoolean channelsPublished = new java.util.concurrent.atomic.AtomicBoolean();
        var rows = new LiveGuideApplicationService().list("", initial -> {
            assertFalse(initial.isEmpty()); assertTrue(initial.stream().allMatch(row -> row.programmes().isEmpty()));
            channelsPublished.set(true);
        });
        assertTrue(channelsPublished.get()); assertFalse(rows.getFirst().programmes().isEmpty());
    }
    @Test void playerGuideUsesCatalogueMetadataForStaleBookmarkSnapshots() throws Exception {
        Account account = saveCatalog("Snapshot", "Catalogue channel");
        Channel catalog = ChannelService.getInstance().getChannelByChannelIdAndAccount("guide-id", account.getDbId(), false);
        Channel snapshot = channel("guide-id", "Old bookmark title");
        snapshot.setExtraJson("{\"epg_channel_id\":\"old-station\"}");
        assertTrue(new EpgCacheApplicationService().schedule(account, snapshot).isEmpty());
        PlayerEpgApplicationService service = new PlayerEpgApplicationService();
        assertEquals("Current", service.current(account, snapshot).now().title());
        assertEquals(new EpgCacheApplicationService().schedule(account, catalog), service.schedule(account, snapshot));
        Account other = saveCatalog("SnapshotOther", "Other catalogue channel");
        assertEquals(new EpgCacheApplicationService().schedule(other,
                ChannelService.getInstance().getChannelByChannelIdAndAccount("guide-id", other.getDbId(), false)),
                service.schedule(other, snapshot));
        ChannelDb.get().delete(catalog.getDbId());
        assertEquals("Current", service.current(account, catalog).now().title(),
                "Bookmarks must still use their snapshot when the catalogue entry has been removed");
    }

    @Test void desktopCatalogAndHydrationReadDatabaseUntilBackgroundRefresh() throws Exception {
        Account account = saveCatalog("Deferred", "Channel");
        EpgApplicationService epg = new EpgApplicationService();
        epg.clearCache();
        var rows = new LiveGuideApplicationService().list(account.getDbId(), initial -> assertFalse(initial.isEmpty()));
        assertTrue(rows.getFirst().programmes().isEmpty());
        assertTrue(rows.getFirst().nativePending());
        assertTrue(new EpgCacheApplicationService().schedule(account, rows.getFirst().channel()).isEmpty());
        Channel channel = rows.getFirst().channel();
        epg.enrichCached(account, List.of(channel));
        assertNull(channel.getEpgNow());
        assertTrue(new LiveGuideApplicationService().hydrate(rows.getFirst()).programmes().isEmpty(), "Card reads never fetch a missing source");
        new EpgCacheRefreshService().refreshAccount(account,List.of(channel));
        var hydrated = new LiveGuideApplicationService().hydrate(rows.getFirst());
        assertFalse(hydrated.nativePending());
        assertEquals("Current", hydrated.programmes().getFirst().title());
        epg.enrichCached(account, List.of(channel));
        assertEquals("Current", channel.getEpgNow().title());
    }

    @Test void discoversRemotePlaylistAndCachesCompressedHttpGuide() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(xml("Remote current").getBytes(StandardCharsets.UTF_8)); }
        java.util.concurrent.atomic.AtomicInteger downloads = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist", exchange -> {
            byte[] body = "#EXTM3U url-tvg=\"/guide\"\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/guide", exchange -> {
            downloads.incrementAndGet(); byte[] body = bytes.toByteArray();
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            Account account = account("Remote", AccountType.M3U8_URL);
            account.setM3u8Path("http://127.0.0.1:" + server.getAddress().getPort() + "/playlist");
            Channel channel = channel("guide-id", "Test"); EpgApplicationService service = new EpgApplicationService();
            new EpgCacheRefreshService().refreshAccount(account, List.of(channel));
            service.enrichCached(account, List.of(channel));
            assertEquals("Remote current", channel.getEpgNow().title()); assertEquals(1, downloads.get());
            // New service instances read persisted rows without downloading the feed again.
            new EpgApplicationService().enrichCached(account, List.of(channel));
            new EpgCacheRefreshService().refreshAccount(account, List.of(channel));
            assertEquals("Remote current", channel.getEpgNow().title()); assertEquals(1, downloads.get());
            service.clearCache();
            new EpgCacheRefreshService().refreshAccount(account, List.of(channel));
            service.enrichCached(account, List.of(channel));
            assertEquals("Remote current", channel.getEpgNow().title()); assertEquals(2, downloads.get());
        } finally { server.stop(0); }
    }
    @Test void xmltvHandlesOffsetsMissingStopsAndAmbiguousNames() throws Exception {
        String xml = """
                <tv><channel id="bbc"><display-name>BBC One HD</display-name></channel>
                <channel id="a"><display-name>News</display-name></channel><channel id="b"><display-name>NEWS</display-name></channel>
                <programme channel="bbc" start="20261013190000 +0100"><title>Current</title><desc>Details</desc></programme>
                <programme channel="bbc" start="20261013200000 +0100" stop="20261013210000 +0100"><title>Next</title></programme>
                <programme channel="bbc" start="bad" stop="bad"><title>Invalid</title></programme></tv>
                """;
        XmltvGuide guide = XmltvGuide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Instant.parse("2026-10-13T18:30:00Z"));
        List<EpgProgramme> programmes = guide.forChannel("bbc", "unrelated");
        assertEquals(2, programmes.size());
        assertEquals(Instant.parse("2026-10-13T18:00:00Z").toEpochMilli(), programmes.getFirst().start());
        assertEquals(programmes.get(1).start(), programmes.getFirst().stop());
        assertEquals(programmes, guide.forChannel("unknown", " BBC  One HD "));
        assertTrue(guide.forChannel("unknown", "News").isEmpty());
        assertEquals(programmes, guide.forChannel("unknown", "BBC One"));
        assertNull(XmltvGuide.time("20260230180000 +0000"));
    }

    @Test void playlistDiscoverySupportsGzipAndEmbeddedSourceWins() throws Exception {
        Path guide = tempDir.resolve("guide.xml.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(guide))) { out.write(xml("Automatic").getBytes(StandardCharsets.UTF_8)); }
        Path playlist = tempDir.resolve("playlist.m3u");
        Files.writeString(playlist, "#EXTM3U x-tvg-url=\"guide.xml.gz\"\n#EXTINF:-1 tvg-id=\"guide-id\",Test\nhttp://example.invalid/live\n");
        Account account = account("Playlist", AccountType.M3U8_LOCAL);
        account.setM3u8Path(playlist.toString());
        Channel channel = channel("guide-id", "Test");
        channel.setDbId("different-local-id");
        channel.setExtraJson("{\"epg_channel_id\":\"guide-id\"}");
        refreshAndEnrich(account, channel);
        assertEquals("Automatic", channel.getEpgNow().title());
        assertEquals("Next", channel.getEpgNext().title());
        Path manual = tempDir.resolve("manual.xml"); Files.writeString(manual, xml("Manual"));
        account.setEpg(manual.toString());
        refreshAndEnrich(account, channel);
        assertEquals("Automatic", channel.getEpgNow().title());
        assertEquals("guide-id", channel.getChannelId());
        assertEquals("different-local-id", channel.getDbId());
        for (AccountType type : AccountType.values()) {
            account.setType(type); refreshAndEnrich(account, channel);
            assertEquals(type == AccountType.M3U8_LOCAL || type == AccountType.M3U8_URL ? "Automatic" : "Manual", channel.getEpgNow().title());
        }
    }

    @Test void standardEscapedXmltvTextCanExceedTheJdkDefaultEntityBudget() throws Exception {
        String escaped = "&amp;".repeat(2_100_000);
        String xml = xml("Escaped text").replace("<title>Escaped text</title>", "<title>Escaped text</title><desc>" + escaped + "</desc>");
        XmltvGuide parsed = XmltvGuide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Instant.now());
        assertEquals("&".repeat(2048), parsed.programmes().get("guide-id").getFirst().description());
    }

    @Test void missingSourceDoesNotBreakChannelsAndExternalEntitiesAreNotResolved() throws Exception {
        Account account = account("Missing", AccountType.STALKER_PORTAL);
        account.setEpg(tempDir.resolve("missing.xml").toString());
        Channel channel = channel("guide-id", "Test");
        new EpgApplicationService().enrichCached(account, List.of(channel));
        assertNull(channel.getEpgNow()); assertNull(channel.getEpgNext());
        Path secret = tempDir.resolve("secret.txt"); Files.writeString(secret, "must-not-appear");
        String xml = "<!DOCTYPE tv [<!ENTITY leak SYSTEM '" + secret.toUri() + "'>]><tv><channel id='x'><display-name>&leak;</display-name></channel></tv>";
        try {
            XmltvGuide parsed = XmltvGuide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Instant.now());
            assertFalse(parsed.names().containsKey("must-not-appear"));
        } catch (javax.xml.stream.XMLStreamException _) { /* Rejecting unresolved entities is also safe. */ }
    }

    @Test void liveGuideUsesOnlySelectedAccountOrOnlyLiveBookmarks() throws Exception {
        Account first = saveCatalog("First", "First channel");
        Account second = saveCatalog("Second", "Second channel");
        Bookmark saved = new Bookmark(second.getAccountName(), "All", "guide-id", "Second channel", "http://example.invalid/live", "", "");
        saved.setAccountAction(Account.AccountAction.itv); BookmarkService.getInstance().save(saved);
        Bookmark movie = new Bookmark(first.getAccountName(), "All", "movie", "Movie", "http://example.invalid/movie", "", "");
        movie.setAccountAction(Account.AccountAction.vod); BookmarkService.getInstance().save(movie);
        LiveGuideApplicationService service = new LiveGuideApplicationService();
        List<LiveGuideApplicationService.Row> accountRows = service.list(first.getDbId());
        assertEquals(List.of("First channel"), accountRows.stream().map(row -> row.channel().getName()).toList());
        assertEquals(first.getDbId(), accountRows.getFirst().accountId());
        List<LiveGuideApplicationService.Row> bookmarks = service.list("");
        assertEquals(List.of("Second channel"), bookmarks.stream().map(row -> row.channel().getName()).toList());
        assertEquals(second.getDbId(), bookmarks.getFirst().accountId());
        assertFalse(bookmarks.getFirst().bookmarkId().isBlank());
        assertTrue(service.list("nonexistent").isEmpty());
    }

    @Test void coversWarningIdentityAndSourceNormalizationBranches() {
        assertEquals(List.of(), EpgApplicationService.globalSources(null));
        assertEquals(List.of("first", "second"), EpgApplicationService.globalSources(" first\nsecond\nfirst\n "));
        assertEquals("epgSourceTooLarge", EpgApplicationService.warning(new IOException("Guide exceeds size limit")));
        assertEquals("epgSourceProgrammeLimit", EpgApplicationService.warning(new IOException("Guide exceeds programme limit")));
        assertEquals("epgSourceXmlLimit", EpgApplicationService.warning(
                new javax.xml.stream.XMLStreamException("JAXP00010004: entity budget exceeded")));
        assertEquals("epgSourceInvalid", EpgApplicationService.warning(
                new javax.xml.stream.XMLStreamException("invalid guide")));
        assertEquals("epgSourceUnavailable", EpgApplicationService.warning(new IllegalStateException("offline")));

        Account xtreme = account("Xtreme", AccountType.XTREME_API);
        Channel xtremeChannel = channel("stream-id", "Xtreme channel");
        xtremeChannel.setExtraJson("{\"epg_channel_id\":\"guide-id\"}");
        assertEquals("guide-id", EpgApplicationService.guideId(xtreme, xtremeChannel));
        xtremeChannel.setExtraJson("{bad");
        assertEquals("", EpgApplicationService.guideId(xtreme, xtremeChannel));

        Account m3u = account("M3U", AccountType.M3U8_URL);
        Channel m3uChannel = channel("provider-id", "M3U channel");
        m3uChannel.setExtraJson("{\"xmltv_id\":\"xmltv-guide\"}");
        assertEquals("xmltv-guide", EpgApplicationService.guideId(m3u, m3uChannel));
        m3uChannel.setExtraJson("{}");
        assertEquals("provider-id", EpgApplicationService.guideId(m3u, m3uChannel));
    }

    private void refreshAndEnrich(Account account, Channel channel) {
        new EpgCacheRefreshService().refreshAccount(account, List.of(channel));
        new EpgApplicationService().enrichCached(account, List.of(channel));
    }
    private Account saveCatalog(String name, String channelName) throws Exception {
        Account account = account(name, AccountType.M3U8_LOCAL);
        Path guide = tempDir.resolve(name + ".xml"); Files.writeString(guide, xml("Current")); account.setEpg(guide.toString());
        account.setDbId(null); AccountDb.get().save(account); account = AccountService.getInstance().getByName(name);
        CategoryDb.get().saveAll(List.of(new Category("provider-category", "Group", "Group", false, 0)), account);
        Category category = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("guide-id", channelName)), category.getDbId(), account);
        new EpgCacheRefreshService().refreshAccount(account,ChannelDb.get().getCachedLiveChannels(account.getDbId()));
        return account;
    }
    private Account account(String name, AccountType type) {
        Account account = new Account(); account.setDbId(UUID.randomUUID().toString()); account.setAccountName(name); account.setType(type); account.setAction(Account.AccountAction.itv); return account;
    }
    private Channel channel(String id, String name) { Channel c = new Channel(); c.setChannelId(id); c.setName(name); c.setCmd("http://example.invalid/live"); return c; }
    private String xml(String title) {
        Instant now = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        return "<tv><programme channel='guide-id' start='" + formatter.format(now.minusSeconds(1800)) + "' stop='" + formatter.format(now.plusSeconds(1800)) + "'><title>" + title + "</title></programme><programme channel='guide-id' start='" + formatter.format(now.plusSeconds(1800)) + "' stop='" + formatter.format(now.plusSeconds(5400)) + "'><title>Next</title></programme></tv>";
    }
}
