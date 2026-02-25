package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheServiceImplTest extends DbBackedTest {

    @Test
    void reloadCache_m3u8Local_ignoresFiltering_whenPauseFilteringFalse() throws IOException {
        saveConfiguration("live", "premium", false);
        Account account = createM3uAccount("acc-cache-1", writePlaylist("cache-playlist-1.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Channel> cachedChannels = getAllCachedChannels(account);
        List<Category> cachedCategories = CategoryDb.get().getCategories(account);

        assertTrue(cachedChannels.stream().anyMatch(c -> "Premium Plus".equals(c.getName())));
        assertTrue(cachedChannels.stream().anyMatch(c -> "Sports Live".equals(c.getName())));
        assertTrue(cachedCategories.stream().anyMatch(c -> "Live".equalsIgnoreCase(c.getTitle())));
    }

    @Test
    void reloadCache_m3u8Local_ignoresFiltering_whenPauseFilteringTrue() throws IOException {
        saveConfiguration("live", "premium", true);
        Account account = createM3uAccount("acc-cache-2", writePlaylist("cache-playlist-2.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Channel> cachedChannels = getAllCachedChannels(account);
        List<Category> cachedCategories = CategoryDb.get().getCategories(account);

        assertTrue(cachedChannels.stream().anyMatch(c -> "Premium Plus".equals(c.getName())));
        assertTrue(cachedChannels.stream().anyMatch(c -> "Sports Live".equals(c.getName())));
        assertTrue(cachedCategories.stream().anyMatch(c -> "Live".equalsIgnoreCase(c.getTitle())));
    }

    @Test
    void reloadCache_stalkerPortal_usesGetAllChannels_whenResponseHasData() throws IOException {
        Account account = createStalkerAccount("acc-stalker-getall");
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = Mockito.mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            fetchMock.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account)))
                    .thenAnswer(invocation -> mockStalkerApiResponse(invocation.getArgument(0), false, orderedListCalls));

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(0, orderedListCalls.get(), "Fallback API should not be called when get_all_channels has data");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().noneMatch(m -> m.contains("Trying last-resort category-by-category fetch")));
        assertTrue(logs.stream().anyMatch(m -> m.startsWith("Found Categories ")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Channels saved Successfully")));
    }

    @Test
    void reloadCache_stalkerPortal_usesCategoryFallback_whenGetAllChannelsIsBlank() throws IOException {
        Account account = createStalkerAccount("acc-stalker-fallback");
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = Mockito.mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            fetchMock.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account)))
                    .thenAnswer(invocation -> mockStalkerApiResponse(invocation.getArgument(0), true, orderedListCalls));

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertTrue(orderedListCalls.get() > 0, "Fallback API should be used when get_all_channels is blank");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Trying last-resort category-by-category fetch")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Last-resort fetch succeeded")));
    }

    @Test
    void verifyMacAddress_returnsFalse_whenAccountIsNull() {
        CacheService cacheService = new CacheServiceImpl();
        assertFalse(cacheService.verifyMacAddress(null, "00:11:22:33:44:99"));
    }

    @Test
    void verifyMacAddress_returnsTrue_andRestoresOriginalMac_whenHandshakeAndCategoriesSucceed() {
        Account account = createStalkerAccount("acc-verify-ok");
        account.setAction(Account.AccountAction.itv);
        String originalMac = account.getMacAddress();
        CacheService cacheService = new CacheServiceImpl();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = Mockito.mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = Mockito.mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            Mockito.doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(Mockito.any(Account.class));

            CategoryService categoryService = Mockito.mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.parseCategories(Mockito.anyString(), Mockito.eq(false)))
                    .thenReturn(List.of(new Category("10", "News", "news", false, 0)));

            fetchMock.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account))).thenReturn("{\"js\":[]}");

            boolean verified = cacheService.verifyMacAddress(account, "00:11:22:33:44:99");
            assertTrue(verified);
            assertEquals(originalMac, account.getMacAddress(), "MAC must be restored after verification");
        }
    }

    @Test
    void verifyMacAddress_returnsFalse_whenHandshakeDoesNotConnect_andRestoresMac() {
        Account account = createStalkerAccount("acc-verify-handshake-fail");
        String originalMac = account.getMacAddress();
        CacheService cacheService = new CacheServiceImpl();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class)) {
            HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            Mockito.doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken(null);
                return null;
            }).when(handshakeService).connect(Mockito.any(Account.class));

            boolean verified = cacheService.verifyMacAddress(account, "00:11:22:33:44:aa");
            assertFalse(verified);
            assertEquals(originalMac, account.getMacAddress(), "MAC must be restored after failed handshake");
        }
    }

    @Test
    void verifyMacAddress_returnsFalse_whenCategoriesEmpty() {
        Account account = createStalkerAccount("acc-verify-empty-cats");
        CacheService cacheService = new CacheServiceImpl();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = Mockito.mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = Mockito.mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            Mockito.doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(Mockito.any(Account.class));

            CategoryService categoryService = Mockito.mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.parseCategories(Mockito.anyString(), Mockito.eq(false))).thenReturn(List.of());

            fetchMock.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account))).thenReturn("{\"js\":[]}");
            assertFalse(cacheService.verifyMacAddress(account, "00:11:22:33:44:ab"));
        }
    }

    @Test
    void verifyMacAddress_returnsFalse_whenFetchThrowsException_andUsesVodCategoryAction() {
        Account account = createStalkerAccount("acc-verify-fetch-throws");
        account.setAction(Account.AccountAction.vod);
        String originalMac = account.getMacAddress();
        CacheService cacheService = new CacheServiceImpl();
        AtomicInteger fetchCalls = new AtomicInteger();
        List<String> actions = new ArrayList<>();

        try (MockedStatic<HandshakeService> handshakeMock = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = Mockito.mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = Mockito.mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            Mockito.doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(Mockito.any(Account.class));

            CategoryService categoryService = Mockito.mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);

            fetchMock.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> params = invocation.getArgument(0);
                        actions.add(params.get("action"));
                        fetchCalls.incrementAndGet();
                        throw new RuntimeException("network down");
                    });

            assertFalse(cacheService.verifyMacAddress(account, "00:11:22:33:44:ac"));
            assertEquals(1, fetchCalls.get());
            assertEquals(List.of("get_categories"), actions, "VOD verification must query get_categories");
            assertEquals(originalMac, account.getMacAddress(), "MAC must be restored after exception");
        }
    }

    private List<Channel> getAllCachedChannels(Account account) {
        assertTrue(ChannelDb.get().getChannelCountForAccount(account.getDbId()) > 0);
        List<Category> categories = CategoryDb.get().getCategories(account);
        assertTrue(categories.size() >= 2);

        List<Channel> all = new ArrayList<>();
        for (Category category : categories) {
            all.addAll(ChannelDb.get().getChannels(category.getDbId()));
        }
        return all;
    }

    private Account createM3uAccount(String accountId, String playlistPath) {
        Account account = new Account(
                "test-account-" + accountId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.M3U8_LOCAL,
                null,
                playlistPath,
                false
        );
        account.setDbId(accountId);
        account.setAction(Account.AccountAction.itv);
        return account;
    }

    private Account createStalkerAccount(String accountId) {
        Account account = new Account(
                "test-account-" + accountId,
                "user",
                "pass",
                "http://stalker.example/portal.php",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        account.setDbId(accountId);
        account.setAction(Account.AccountAction.itv);
        account.setServerPortalUrl("http://stalker.example/portal.php");
        return account;
    }

    private void mockSuccessfulHandshake(MockedStatic<HandshakeService> handshakeMock) {
        HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
        handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
        Mockito.doAnswer(invocation -> {
            Account handshakeAccount = invocation.getArgument(0);
            handshakeAccount.setToken("mock-token");
            return null;
        }).when(handshakeService).connect(Mockito.any(Account.class));
    }

    @SuppressWarnings("unchecked")
    private String mockStalkerApiResponse(Map<String, String> params, boolean blankGetAllChannels, AtomicInteger orderedListCalls) {
        String action = params.get("action");
        if ("get_genres".equals(action)) {
            return """
                    {
                      "js": [
                        {"id":"10","title":"News","alias":"news","active_sub":true,"censored":0},
                        {"id":"20","title":"Sports","alias":"sports","active_sub":true,"censored":0}
                      ]
                    }
                    """;
        }
        if ("get_all_channels".equals(action)) {
            if (blankGetAllChannels) {
                return "";
            }
            return """
                    {
                      "js": {
                        "data": [
                          {"id":"101","name":"News 1","number":"1","cmd":"ffmpeg http://stream/news1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"n1","censored":0,"status":1,"hd":1,"tv_genre_id":"10"},
                          {"id":"201","name":"Sports 1","number":"2","cmd":"ffmpeg http://stream/sports1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"s1","censored":0,"status":1,"hd":1,"tv_genre_id":"20"}
                        ]
                      }
                    }
                    """;
        }
        if ("get_ordered_list".equals(action)) {
            orderedListCalls.incrementAndGet();
            String genre = params.get("genre");
            String page = params.get("p");
            if ("10".equals(genre) && "0".equals(page)) {
                return """
                        {
                          "js": {
                            "total_items": 1,
                            "max_page_items": 999,
                            "data": [
                              {"id":"101","name":"News 1","number":"1","cmd":"ffmpeg http://stream/news1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"n1","censored":0,"status":1,"hd":1,"tv_genre_id":"10"}
                            ]
                          }
                        }
                        """;
            }
            if ("20".equals(genre) && "0".equals(page)) {
                return """
                        {
                          "js": {
                            "total_items": 1,
                            "max_page_items": 999,
                            "data": [
                              {"id":"201","name":"Sports 1","number":"2","cmd":"ffmpeg http://stream/sports1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"s1","censored":0,"status":1,"hd":1,"tv_genre_id":"20"}
                            ]
                          }
                        }
                        """;
            }
            return """
                    {
                      "js": {
                        "total_items": 0,
                        "max_page_items": 999,
                        "data": []
                      }
                    }
                    """;
        }
        return "";
    }

    private void saveConfiguration(String categoryFilter, String channelFilter, boolean pauseFiltering) {
        Configuration configuration = new Configuration(
                null,
                null,
                null,
                null,
                categoryFilter,
                channelFilter,
                pauseFiltering,
                null,
                null,
                null,
                false,
                "8888",
                false,
                false
        );
        ConfigurationService.getInstance().save(configuration);
    }

    private String writePlaylist(String filename) throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="sports-1" tvg-logo="sports.png" group-title="Live",Sports Live
                http://example.com/live/sports
                #EXTINF:-1 tvg-id="premium-1" tvg-logo="premium.png" group-title="Live",Premium Plus
                http://example.com/live/premium
                """;
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toString();
    }
}
