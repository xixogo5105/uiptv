package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkResolverTest extends DbBackedTest {

    @Test
    void savedCatalogPolicySurvivesAccountCachingChanges() {
        Account account = new Account("saved-policy", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);
        Bookmark bookmark = new Bookmark(account.getAccountName(), "Sports", "channel", "Match", "http://stream", "", "");
        BookmarkService.getInstance().save(bookmark);
        account.setCacheEnabled(true);
        AccountService.getInstance().save(account);
        Bookmark saved = BookmarkService.getInstance().getBookmark(bookmark.getDbId());
        BookmarkService.getInstance().save(saved);
        assertEquals(false, new org.json.JSONObject(saved.getCategoryJson()).getBoolean("catalogCacheEnabledAtSave"));
        assertEquals("http://stream", new PlayerRequestResolver().resolveBookmarkChannel(saved).getCmd());
    }

    @Test
    void resolveBookmark_usesSnapshotData_andDefaultsAccountAction() {
        Account account = new Account("bookmark-acc", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        AccountService.getInstance().save(account);

        Channel snapshot = new Channel();
        snapshot.setLogo("http://img/logo.png");
        snapshot.setDrmType("widevine");
        snapshot.setDrmLicenseUrl("http://license");
        snapshot.setClearKeysJson("{\"kid\":\"key\"}");
        snapshot.setInputstreamaddon("inputstream.adaptive");
        snapshot.setManifestType("dash");

        Bookmark bookmark = new Bookmark("bookmark-acc", "Sports", "ch-1", "Channel One", "cmd", "http://portal", "cat-1");
        bookmark.setChannelJson(snapshot.toJson());

        BookmarkResolver resolver = new BookmarkResolver();
        BookmarkResolver.ResolvedBookmark resolved = resolver.resolveBookmark(bookmark, resolver.prepare(List.of(bookmark)));

        assertEquals("http://img/logo.png", resolved.getLogo());
        assertEquals("widevine", resolved.getDrmType());
        assertEquals("http://license", resolved.getDrmLicenseUrl());
        assertEquals("{\"kid\":\"key\"}", resolved.getClearKeysJson());
        assertEquals("inputstream.adaptive", resolved.getInputstreamaddon());
        assertEquals("dash", resolved.getManifestType());
        assertEquals(Account.AccountAction.itv, resolved.getAccountAction());
    }

    @Test
    void resolveBookmarkUsesSavedSnapshotWhenCachingIsDisabledAndCatalogRowsAreAbsent() {
        Account account = new Account("bookmark-no-catalog-cache", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        account.setCacheEnabled(false);
        AccountService.getInstance().save(account);

        Channel snapshot = new Channel();
        snapshot.setLogo("http://img.example/saved-logo.png");
        snapshot.setDrmType("widevine");
        snapshot.setDrmLicenseUrl("http://license.example/key");
        Bookmark bookmark = new Bookmark(account.getAccountName(), "Sports", "channel-8", "Saved live channel",
                "http://stream.example/channel-8", "http://portal", "live-category-id");
        bookmark.setChannelJson(snapshot.toJson());

        BookmarkResolver.ResolvedBookmark resolved = new BookmarkResolver()
                .resolveBookmark(bookmark, new BookmarkResolver().prepare(List.of(bookmark)));

        assertEquals("http://img.example/saved-logo.png", resolved.getLogo());
        assertEquals("widevine", resolved.getDrmType());
        assertEquals("http://license.example/key", resolved.getDrmLicenseUrl());
        assertTrue(CategoryDb.get().getAllAccountCategories(account.getDbId()).isEmpty());
        assertTrue(ChannelDb.get().getChannelsByChannelIdsAndAccount(List.of("channel-8"), account.getDbId()).isEmpty());
    }

    @Test
    void resolveBookmark_fallsBackToChannelCache_whenSnapshotIsMissing() {
        Account account = new Account("bookmark-cache", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", false);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("bookmark-cache");

        Category category = new Category("100", "News", "news", false, 0);
        CategoryDb.get().insert(category, saved);
        Category stored = CategoryDb.get().getCategories(saved).get(0);

        Channel channel = new Channel();
        channel.setChannelId("ch-9");
        channel.setName("News One");
        channel.setLogo("http://img/news.png");
        channel.setDrmType("widevine");
        channel.setDrmLicenseUrl("http://license");
        channel.setClearKeysJson("{\"kid\":\"key\"}");
        channel.setInputstreamaddon("inputstream.adaptive");
        channel.setManifestType("hls");
        ChannelDb.insert(channel, stored);

        Bookmark bookmark = new Bookmark("bookmark-cache", "News", "ch-9", "News One", "cmd", "http://portal", stored.getDbId());

        BookmarkResolver resolver = new BookmarkResolver();
        BookmarkResolver.ResolvedBookmark resolved = resolver.resolveBookmark(bookmark, resolver.prepare(List.of(bookmark)));

        assertEquals("http://img/news.png", resolved.getLogo());
        assertEquals("widevine", resolved.getDrmType());
        assertEquals("http://license", resolved.getDrmLicenseUrl());
        assertEquals("{\"kid\":\"key\"}", resolved.getClearKeysJson());
        assertEquals("inputstream.adaptive", resolved.getInputstreamaddon());
        assertEquals("hls", resolved.getManifestType());
        assertTrue(resolved.getLogo().contains("news"));

        Bookmark liveBookmark = new Bookmark("bookmark-cache", "News", "ch-9", "Saved live title", "cmd", "http://portal", "");
        liveBookmark.setCategoryJson("{\"catalogCacheEnabledAtSave\":false}");
        BookmarkResolver.ResolvedBookmark liveResolved = resolver.resolveBookmark(
                liveBookmark, resolver.prepare(List.of(liveBookmark)));
        assertTrue(liveResolved.getLogo() == null || liveResolved.getLogo().isBlank());
        assertTrue(liveResolved.getDrmType() == null || liveResolved.getDrmType().isBlank());
    }
}
