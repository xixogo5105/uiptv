package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.util.AccountType;
import com.uiptv.util.RssParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelServiceBatchLookupTest extends DbBackedTest {

    @Test
    void getChannelsByChannelIdsAndAccount_returnsOnlyRequestedChannelsForAccount() {
        Account account = new Account("batch-account", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(Account.AccountAction.itv);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("batch-account");
        saved.setAction(Account.AccountAction.itv);

        Category category = new Category("cat-1", "News", "news", false, 0);
        CategoryDb.get().saveAll(List.of(category), saved);
        Category storedCategory = CategoryDb.get().getCategories(saved).getFirst();

        Channel first = new Channel();
        first.setChannelId("one");
        first.setName("One");
        first.setCmd("http://example.test/one.m3u8");

        Channel second = new Channel();
        second.setChannelId("two");
        second.setName("Two");
        second.setCmd("http://example.test/two.m3u8");

        Channel third = new Channel();
        third.setChannelId("three");
        third.setName("Three");
        third.setCmd("http://example.test/three.m3u8");

        ChannelDb.get().saveAll(List.of(first, second, third), storedCategory.getDbId(), saved);

        List<Channel> channels = ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one", "three", "missing"), saved.getDbId());

        assertEquals(2, channels.size());
        assertTrue(channels.stream().anyMatch(channel -> "one".equals(channel.getChannelId())));
        assertTrue(channels.stream().anyMatch(channel -> "three".equals(channel.getChannelId())));
    }

    @Test
    void getChannelsByChannelIdsAndAccount_deduplicatesRequestedIds() {
        Account account = new Account("batch-dedupe", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(Account.AccountAction.itv);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("batch-dedupe");
        saved.setAction(Account.AccountAction.itv);

        Category category = new Category("cat-1", "News", "news", false, 0);
        CategoryDb.get().saveAll(List.of(category), saved);
        Category storedCategory = CategoryDb.get().getCategories(saved).getFirst();

        Channel first = new Channel();
        first.setChannelId("dup");
        first.setName("Dup");
        first.setCmd("http://example.test/dup.m3u8");

        Channel second = new Channel();
        second.setChannelId("other");
        second.setName("Other");
        second.setCmd("http://example.test/other.m3u8");

        ChannelDb.get().saveAll(List.of(first, second), storedCategory.getDbId(), saved);

        List<Channel> channels = ChannelService.getInstance()
                .getChannelsByChannelIdsAndAccount(List.of("dup", "dup", "other", "dup"), saved.getDbId());

        assertEquals(2, channels.size());
        assertEquals(1, channels.stream().filter(channel -> "dup".equals(channel.getChannelId())).count());
        assertEquals(1, channels.stream().filter(channel -> "other".equals(channel.getChannelId())).count());
    }

    @Test
    void getChannelsByChannelIdsAndAccount_returnsEmptyForNullBlankOrMissingInput() {
        assertNotNull(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(null, "account-id"));
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(null, "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of(), "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(Arrays.asList(" ", "", null), "account-id").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one"), "").isEmpty());
        assertTrue(ChannelService.getInstance().getChannelsByChannelIdsAndAccount(List.of("one"), null).isEmpty());
    }

    @Test
    void cachedLiveLookup_fallsBackFromIdToNameAcrossCategories() {
        Account saved = saveAccount("cached-live-name", Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(
                new Category("cat-a", "News", "news", false, 0),
                new Category("cat-b", "Sports", "sports", false, 0)
        ), saved);
        List<Category> categories = CategoryDb.get().getCategories(saved);

        Channel news = channel("live-1", "News One", "http://stream/news");
        Channel sports = channel("live-2", "Sports One", "http://stream/sports");
        ChannelDb.get().saveAll(List.of(news), categories.get(0).getDbId(), saved);
        ChannelDb.get().saveAll(List.of(sports), categories.get(1).getDbId(), saved);

        assertEquals("live-1", ChannelService.getInstance().findCachedLiveChannel(saved, "live-1", "").getChannelId());
        assertEquals("live-2", ChannelService.getInstance().findCachedLiveChannel(saved, "", " sports one ").getChannelId());
        assertEquals(null, ChannelService.getInstance().findCachedLiveChannel(saved, "", ""));
        assertEquals(null, ChannelService.getInstance().findCachedLiveChannel(null, "live-1", "News One"));
    }

    @Test
    void cachedVodAndSeriesLookup_resolvesCategoryByDbIdTitleOrProviderId() {
        Account saved = saveAccount("cached-vod-series", Account.AccountAction.vod);
        CategoryDb.get().saveAll(List.of(new Category("provider-10", "Movies", "movies", false, 0)), saved);
        Category category = CategoryDb.get().getCategories(saved).getFirst();

        Channel movie = channel("vod-1", "Movie One", "http://stream/movie");
        VodChannelDb.get().saveAll(List.of(movie), category.getDbId(), saved);

        assertEquals("vod-1", ChannelService.getInstance().findCachedVodChannel(saved, category.getDbId(), "vod-1", "").getChannelId());
        assertEquals("vod-1", ChannelService.getInstance().findCachedVodChannel(saved, "Movies", "", " movie one ").getChannelId());
        assertEquals("vod-1", ChannelService.getInstance().findCachedVodChannel(saved, "provider-10", "missing", "Movie One").getChannelId());
        assertEquals(null, ChannelService.getInstance().findCachedVodChannel(saved, "missing", "vod-1", "Movie One"));

        saved.setAction(Account.AccountAction.series);
        CategoryDb.get().saveAll(List.of(new Category("provider-10", "Movies", "movies", false, 0)), saved);
        category = CategoryDb.get().getCategories(saved).getFirst();
        Channel series = channel("series-1", "Series One", "http://stream/series");
        SeriesChannelDb.get().saveAll(List.of(series), category.getDbId(), saved);
        assertEquals("series-1", ChannelService.getInstance().findCachedSeriesChannel(saved, "Movies", "series-1", "").getChannelId());
        assertEquals("series-1", ChannelService.getInstance().findCachedSeriesChannel(saved, "provider-10", "", "Series One").getChannelId());
    }

    @Test
    void privateHelpers_coverLogoFallbackDedupeThrottleAndCategoryResolution() throws Exception {
        ChannelService service = ChannelService.getInstance();
        Account saved = saveAccount("channel-private-helpers", Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("provider-20", "Kids", "kids", false, 0)), saved);
        Category category = CategoryDb.get().getCategories(saved).getFirst();

        assertEquals(category.getDbId(), invoke(service, "resolveCategoryDbId", new Class[]{Account.class, String.class}, saved, category.getDbId()));
        assertEquals(category.getDbId(), invoke(service, "resolveCategoryDbId", new Class[]{Account.class, String.class}, saved, "Kids"));
        assertEquals(category.getDbId(), invoke(service, "resolveCategoryDbId", new Class[]{Account.class, String.class}, saved, "provider-20"));
        assertEquals("", invoke(service, "resolveCategoryDbId", new Class[]{Account.class, String.class}, saved, ""));

        assertEquals("http://logo.test/img.png", invoke(service, "extractLogoFromExtraJson", new Class[]{String.class}, "{\"stream_icon\":\"http://logo.test/img.png\"}"));
        assertEquals("http://logo.test/cover.png", invoke(service, "extractLogoFromExtraJson", new Class[]{String.class}, "{\"cover\":\"http://logo.test/cover.png\"}"));
        assertEquals("http://logo.test/movie.png", invoke(service, "extractLogoFromExtraJson", new Class[]{String.class}, "{\"movie_image\":\"http://logo.test/movie.png\"}"));
        assertEquals("", invoke(service, "extractLogoFromExtraJson", new Class[]{String.class}, "{bad"));
        assertEquals("http://cdn.test/logo.png", invoke(service, "normalizeLogoUrl", new Class[]{Account.class, String.class}, saved, "http://cdn.test/logo.png"));
        assertEquals("", invoke(service, "normalizeLogoUrl", new Class[]{Account.class, String.class}, saved, " "));
        saved.setServerPortalUrl("http://portal.test/stalker_portal/server/load.php");
        assertEquals("http://portal.test/logo.png", invoke(service, "normalizeLogoUrl", new Class[]{Account.class, String.class}, saved, "/logo.png"));
        assertEquals("http://cdn.test/logo.png", invoke(service, "normalizeLogoUrl", new Class[]{Account.class, String.class}, saved, "//cdn.test/logo.png"));
        saved.setServerPortalUrl("not a uri");
        assertEquals("/logo.png", invoke(service, "normalizeLogoUrl", new Class[]{Account.class, String.class}, saved, "/logo.png"));
        assertEquals("logo.png", invoke(service, "trimWrappedLogo", new Class[]{String.class}, " 'logo.png' "));
        assertEquals("logo.png", invoke(service, "trimWrappedLogo", new Class[]{String.class}, " \"logo.png\" "));

        List<Channel> deduped = invoke(service, "dedupeChannels", new Class[]{List.class}, List.of(
                channel("dup", "Same", "http://stream/1"),
                channel("dup", "Same", "http://stream/1"),
                channel("other", "Other", "http://stream/2")
        ));
        assertEquals(2, deduped.size());
        assertTrue(((List<Channel>) invoke(service, "dedupeChannels", new Class[]{List.class}, (Object) null)).isEmpty());

        java.lang.reflect.Constructor<?> throttleConstructor = Class.forName("com.uiptv.service.ChannelService$RequestThrottle")
                .getDeclaredConstructor(long.class, long.class, long.class);
        throttleConstructor.setAccessible(true);
        Object throttle = throttleConstructor.newInstance(0L, 10L, 0L);
        assertEquals(Long.valueOf(0L), invoke(throttle, "onSuccess", new Class[]{}));
        assertEquals(Long.valueOf(0L), invoke(throttle, "onFailure", new Class[]{}));
    }

    @Test
    void parsesPaginationItvVodAndSeriesJsonVariants() throws Exception {
        ChannelService service = ChannelService.getInstance();
        Pagination pagination = service.parsePagination("""
                {"pagination":{"total_items":42,"max_page_items":10}}
                """, null);
        assertEquals(42, pagination.getMaxPageItems());
        assertEquals(5, pagination.getPageCount());

        Pagination jsPagination = service.parsePagination("""
                {"js":{"total_items":12,"max_page_items":6}}
                """, message -> { });
        assertEquals(12, jsPagination.getMaxPageItems());
        assertEquals(2, jsPagination.getPageCount());
        assertNull(service.parsePagination("{}", null));
        assertNull(service.parsePagination("{bad", null));

        List<Channel> live = service.parseItvChannels("""
                {"js":{"data":[
                  {"id":1,"name":"Live One","number":"101","cmd":"ffmpeg http://live/one","cmd_1":"a","cmd_2":"b","cmd_3":"c",
                   "logo":"\\/logo.png","censored":0,"status":1,"hd":1,"tv_genre_id":"news"},
                  {"id":1,"name":"Live One","number":"101","cmd":"ffmpeg http://live/one","cmd_1":"a","cmd_2":"b","cmd_3":"c",
                   "logo":"\\/logo.png","censored":0,"status":1,"hd":1,"tv_genre_id":"news"}
                ]}}
                """, false);
        assertEquals(1, live.size());
        assertEquals("news", live.getFirst().getCategoryId());
        assertTrue(service.parseItvChannels("{bad", true).isEmpty());

        Account vodAccount = saveAccount("parse-vod", Account.AccountAction.vod);
        vodAccount.setServerPortalUrl("http://portal.test/stalker_portal/server/load.php");
        List<Channel> vodChannels = service.parseVodChannels(vodAccount, """
                {"data":[
                  {"id":7,"o_name":"Movie Name","cmd":"movie-cmd","tv_genre_id":"movies","screenshot_uri":"","stream_icon":"","cover":"\\/covers\\/movie.jpg",
                   "censored":0,"status":1,"hd":0}
                ]}
                """, false);
        assertEquals(1, vodChannels.size());
        assertEquals("Movie Name", vodChannels.getFirst().getName());
        assertEquals("http://portal.test/covers/movie.jpg", vodChannels.getFirst().getLogo());

        Account seriesAccount = saveAccount("parse-series", Account.AccountAction.series);
        List<Channel> seriesChannels = service.parseVodChannels(seriesAccount, """
                {"js":{"data":[
                  {"id":8,"name":"Series Name","cmd":"series-cmd","tv_genre_id":"series","movie_image":"http://img/series.jpg",
                   "series":[3,1],"censored":0,"status":1,"hd":1}
                ]}}
                """, false);
        assertEquals(2, seriesChannels.size());
        assertEquals("Series Name - Episode 1", seriesChannels.getFirst().getName());
        assertTrue(service.parseVodChannels(seriesAccount, "{bad", true).isEmpty());
    }

    @Test
    void channelParamsAndProgressRecordsExposeExpectedValues() {
        var params = ChannelService.getChannelOrSeriesParams("cat", 3, Account.AccountAction.series, "", "season-1");

        assertEquals("series", params.get("type"));
        assertEquals("0", params.get("movie_id"));
        assertEquals("season-1", params.get("season_id"));
        assertEquals("3", params.get("p"));

        ChannelService.PageProgress progress = new ChannelService.PageProgress(1, 2, 3, 4);
        assertEquals(1, progress.fetchedItems());
        assertEquals(2, progress.totalItems());
        assertEquals(3, progress.pageNumber());
        assertEquals(4, progress.pageCount());
    }

    @Test
    void getOverloadsUseRssFeedBranchAndPublishCallback() throws IOException {
        ChannelService service = ChannelService.getInstance();
        Account rss = new Account("rss-account", "", "", "", null, null, null, null, null, null, AccountType.RSS_FEED, null, "rss://feed", false);
        rss.setAction(Account.AccountAction.itv);
        PlaylistEntry entry = new PlaylistEntry("rss-id", "RSS Title", "RSS Title", "http://stream/rss.mp4", "http://logo/rss.png");

        try (MockedStatic<RssParser> rssParser = Mockito.mockStatic(RssParser.class)) {
            rssParser.when(() -> RssParser.parse("rss://feed")).thenReturn(List.of(entry));

            assertEquals(1, service.get(CategoryType.ALL.displayName(), rss, "db-id").size());
            assertEquals(1, service.get("RSS Title", rss, "db-id", message -> { }).size());

            List<List<Channel>> published = new ArrayList<>();
            List<Channel> channels = service.get("rss-id", rss, "db-id", message -> { }, published::add);

            assertEquals(1, channels.size());
            assertEquals(1, published.size());
            assertEquals("RSS Title", published.getFirst().getFirst().getName());
        }
    }

    private Account saveAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        return saved;
    }

    private Channel channel(String id, String name, String cmd) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd(cmd);
        return channel;
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
