package com.uiptv.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import com.uiptv.db.AccountDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
    void reloadCache_m3u8Local_appliesFiltering_whenPauseFilteringFalse() throws IOException {
        saveConfiguration("adult", "premium", false);
        Account account = createM3uAccount("acc-cache-1", writePlaylist("cache-playlist-1.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Channel> cachedChannels = getAllCachedChannels(account);
        List<Category> cachedCategories = CategoryDb.get().getCategories(account);

        assertFalse(cachedChannels.stream().anyMatch(c -> "Premium Plus".equals(c.getName())));
        assertTrue(cachedChannels.stream().anyMatch(c -> "Sports Live".equals(c.getName())));
        // Since there's only one real category, it should be treated as All
        assertTrue(cachedCategories.stream().anyMatch(c -> CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle())));
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
        // Since there's only one real category, it should be treated as All
        assertTrue(cachedCategories.stream().anyMatch(c -> CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle())));
    }

    @Test
    void reloadCache_stalkerPortal_usesGetAllChannels_whenResponseHasData() throws IOException {
        Account account = createStalkerAccount("acc-stalker-getall");
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(0, orderedListCalls.get(), "Fallback API should not be called when get_all_channels has data");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().noneMatch(m -> m.contains("Trying last-resort category-by-category fetch")));
        assertTrue(logs.stream().anyMatch(m -> m.startsWith("Found Categories ")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Channels saved Successfully")));
    }

    @Test
    void reloadCache_m3u8Local_uncategorizedOnly_savesOnlyAllCategory_andKeepsAllChannels() throws IOException {
        Account account = createM3uAccount("acc-uncategorized-only", writeUncategorizedOnlyPlaylist("cache-playlist-uncategorized-only.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Category> categories = CategoryDb.get().getCategories(account);
        assertEquals(1, categories.size());
        assertEquals(CategoryType.ALL.displayName(), categories.get(0).getTitle());

        int channelCount = ChannelDb.get().getChannelCountForAccount(account.getDbId());
        assertEquals(3, channelCount);

        List<Channel> allChannels = ChannelService.getInstance().get("All", account, categories.get(0).getDbId());
        assertEquals(3, allChannels.size(), "All category should still expose every playlist item");
    }

    @Test
    void channelService_allCategory_aggregatesLegacyUncategorizedRows_whenAllCategoryHasNoRows() throws IOException {
        Account account = createM3uAccount("acc-all-legacy-fallback", writePlaylist("cache-playlist-legacy-all.m3u"));

        CategoryDb.get().saveAll(
                List.of(
                        new Category("all", CategoryType.ALL.displayName(), "all", false, 0),
                        new Category("uncategorized", CategoryType.UNCATEGORIZED.displayName(), CategoryType.UNCATEGORIZED.identifier(), false, 0)
                ),
                account
        );

        List<Category> categories = CategoryDb.get().getCategories(account);
        Category allCategory = categories.stream().filter(c -> CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle())).findFirst().orElseThrow();
        Category uncategorizedCategory = categories.stream().filter(c -> CategoryType.UNCATEGORIZED.displayName().equalsIgnoreCase(c.getTitle())).findFirst().orElseThrow();

        ChannelDb.get().saveAll(
                List.of(new Channel("legacy-1", "Legacy Channel", "1", "cmd://legacy", null, null, null, "logo", 0, 1, 1, null, null, null, null, null)),
                uncategorizedCategory.getDbId(),
                account
        );

        List<Channel> channels = ChannelService.getInstance().get(CategoryType.ALL.displayName(), account, allCategory.getDbId());
        assertEquals(1, channels.size(), "All category should include channels stored under legacy Uncategorized rows");
        assertEquals("Legacy Channel", channels.get(0).getName());
    }

    @Test
    void reloadCache_stalkerPortal_usesCategoryFallback_whenGetAllChannelsIsBlank() throws IOException {
        Account account = createStalkerAccount("acc-stalker-fallback");
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, true, orderedListCalls);

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertTrue(orderedListCalls.get() > 0, "Fallback API should be used when get_all_channels is blank");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Trying last-resort category-by-category fetch")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Last-resort fetch succeeded")));
    }

    @Test
    void reloadCache_stalkerPortal_keepsExistingLiveCache_whenCategoryFilterRemovesEverything() throws IOException {
        saveConfiguration("news,sports", "", false);
        Account account = createStalkerAccount("acc-stalker-filter-all-categories");
        persistExistingLiveCache(account);
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(m -> m.contains("All categories removed by active censoring. Keeping existing cache.")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Censored Categories 2")));
    }

    @Test
    void reloadCache_stalkerPortal_keepsExistingLiveCache_whenChannelFilterRemovesEverything() throws IOException {
        saveConfiguration("", "news,sports", false);
        Account account = createStalkerAccount("acc-stalker-filter-all-channels");
        persistExistingLiveCache(account);
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(m -> m.contains("All channels removed by active censoring. Keeping existing cache.")));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Censored Channels 2")));
    }

    @Test
    void reloadCache_stalkerPortal_persistsServerPortalUrl_afterInternalCacheClear() throws IOException {
        Account account = createPersistedStalkerAccount("acc-stalker-persist-portal");
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();
        String resolvedPortalUrl = "http://resolved.stalker.example/portal.php";

        assertTrue(account.getServerPortalUrl() == null || account.getServerPortalUrl().isBlank());

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock, resolvedPortalUrl);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        Account persisted = AccountDb.get().getAccountById(account.getDbId());
        assertEquals(resolvedPortalUrl, persisted.getServerPortalUrl());
        assertEquals(resolvedPortalUrl, account.getServerPortalUrl());
        assertEquals(0, orderedListCalls.get(), "get_all_channels path should still be used");
    }

    @Test
    void reloadCache_stalkerPortal_doesNotMutateCallerAccount_duringVodAndSeriesPhases() throws IOException {
        Account account = createStalkerAccount("acc-stalker-detached");
        account.setAction(Account.AccountAction.itv);
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        // Observe the caller's account from inside every provider request. A bulk refresh used to
        // flip the shared instance to vod/series here, which any concurrent reader (web server,
        // account list, change listener) could observe mid-reload.
        List<Account.AccountAction> observedActions = new ArrayList<>();
        List<String> observedTokens = new ArrayList<>();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);
            fetchMock.when(() -> FetchAPI.fetch(anyMap(), forAccount(account)))
                    .thenAnswer(invocation -> {
                        observedActions.add(account.getAction());
                        observedTokens.add(account.getToken() == null ? "" : account.getToken());
                        return mockStalkerApiResponse(invocation.getArgument(0), false, orderedListCalls);
                    });

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertFalse(observedActions.isEmpty(), "At least one provider request should have been issued");
        assertTrue(observedActions.stream().allMatch(action -> action == Account.AccountAction.itv),
                "Caller account action must stay 'itv' for the whole reload, observed: " + observedActions);
        assertTrue(observedTokens.stream().allMatch(String::isEmpty),
                "Caller account must not have its token cleared/rotated mid-reload, observed: " + observedTokens);
        assertEquals(Account.AccountAction.itv, account.getAction(), "Caller action must be unchanged after reload");
    }

    @Test
    void reloadCache_reportsVodAndSeriesPhases_toPhaseAwareLogger() throws IOException {
        Account account = createStalkerAccount("acc-phase-report");
        account.setAction(Account.AccountAction.itv);
        AtomicInteger orderedListCalls = new AtomicInteger();

        List<Account.AccountAction> phases = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, account, false, orderedListCalls);

            new CacheServiceImpl().reloadCacheWithPhases(account, (message, phase) -> {
                messages.add(message);
                phases.add(phase);
            });
        }

        assertFalse(messages.isEmpty(), "Reload should emit progress messages");
        assertTrue(phases.contains(Account.AccountAction.itv), "Live phase should be reported: " + phases);
        assertTrue(phases.contains(Account.AccountAction.vod),
                "VOD phase must be reported so the UI can label it, observed: " + phases);
        assertTrue(phases.contains(Account.AccountAction.series),
                "Series phase must be reported so the UI can label it, observed: " + phases);

        // The phase must be reported from the reload copy, not read back off the caller's account.
        assertEquals(Account.AccountAction.itv, account.getAction(),
                "Caller account action stays at the starting phase; phase is delivered via the callback");

        int vodMessageIndex = indexOfMessageContaining(messages, "Saved", "VOD/Series");
        if (vodMessageIndex >= 0) {
            assertTrue(phases.get(vodMessageIndex) == Account.AccountAction.vod
                            || phases.get(vodMessageIndex) == Account.AccountAction.series,
                    "VOD/Series save message must carry a VOD/series phase, got: " + phases.get(vodMessageIndex));
        }
    }

    private int indexOfMessageContaining(List<String> messages, String prefix, String contains) {
        for (int i = 0; i < messages.size(); i++) {
            String message = messages.get(i);
            if (message != null && message.startsWith(prefix) && message.contains(contains)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void reloadCache_stalkerPortal_derivesCategoriesFromChannels_whenGetGenresIsEmpty() throws IOException {
        Account account = createStalkerAccount("acc-derive-categories");
        account.setAction(Account.AccountAction.itv);
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();
        AtomicInteger getAllChannelsCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);

            // get_genres returns nothing usable; get_all_channels still carries tv_genre_id.
            stubRealNullSafeString(fetchMock);
            fetchMock.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), forAccount(account), any()))
                    .thenAnswer(invocation -> {
                        Map<String, String> params = invocation.getArgument(0);
                        String action = params.get("action");
                        if ("get_genres".equals(action)) {
                            return FetchAPI.FetchResult.success("{\"js\":[]}", 200);
                        }
                        if ("get_all_channels".equals(action)) {
                            getAllChannelsCalls.incrementAndGet();
                            return FetchAPI.FetchResult.success("""
                                    {
                                      "js": {
                                        "data": [
                                          {"id":"101","name":"News 1","number":"1","cmd":"ffmpeg http://stream/news1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"10","category":"News HD"},
                                          {"id":"102","name":"News 2","number":"2","cmd":"ffmpeg http://stream/news2","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"10","category":"News HD"},
                                          {"id":"201","name":"Sports 1","number":"3","cmd":"ffmpeg http://stream/sports1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"20","category":"Sports"}
                                        ]
                                      }
                                    }
                                    """, 200);
                        }
                        return FetchAPI.FetchResult.success("", 200);
                    });

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size(),
                "Two distinct tv_genre_id values should become two categories; logs=" + logs);
        assertEquals(3, ChannelDb.get().getChannelCountForAccount(account.getDbId()),
                "All channels should be saved once categories are derived");
        assertEquals(1, getAllChannelsCalls.get(), "The derived path should reuse the channels it already fetched");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Deriving categories from get_all_channels")),
                "Derivation should be reported: " + logs);
        assertTrue(logs.stream().anyMatch(m -> m.contains("Derived 2 categories from 3 channels")),
                "Derived counts should be reported: " + logs);

        List<String> titles = CategoryDb.get().getCategories(account).stream().map(Category::getTitle).sorted().toList();
        assertEquals(List.of("News HD", "Sports"), titles,
                "Category titles should come from the portal's channel category field when present");
    }

    @Test
    void reloadCache_stalkerPortal_doesNotPublishUnnamedGenreIds_whenPortalOmitsCategoryNames() throws IOException {
        Account account = createStalkerAccount("acc-derive-categories-untitled");
        account.setAction(Account.AccountAction.itv);
        List<String> logs = new ArrayList<>();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubRealNullSafeString(fetchMock);
            fetchMock.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), forAccount(account), any()))
                    .thenAnswer(invocation -> {
                        Map<String, String> params = invocation.getArgument(0);
                        String action = params.get("action");
                        if ("get_genres".equals(action)) {
                            return FetchAPI.FetchResult.success("", 200);
                        }
                        if ("get_all_channels".equals(action)) {
                            return FetchAPI.FetchResult.success("""
                                    {
                                      "js": {
                                        "data": [
                                          {"id":"101","name":"News 1","number":"1","cmd":"ffmpeg http://stream/news1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"10"},
                                          {"id":"201","name":"Sports 1","number":"2","cmd":"ffmpeg http://stream/sports1","cmd_1":"","cmd_2":"","cmd_3":"","logo":"","censored":0,"status":1,"hd":0,"tv_genre_id":"20"}
                                        ]
                                      }
                                    }
                                    """, 200);
                        }
                        return FetchAPI.FetchResult.success("", 200);
                    });

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        // The portal names no genre, so no category can be derived. Channels must not be lost:
        // they fall through the orphan path into Uncategorized.
        List<Category> categories = CategoryDb.get().getCategories(account);
        assertEquals(1, categories.size(), "No named categories should be published: " + titles(categories));
        assertEquals(CategoryType.UNCATEGORIZED.displayName(), categories.getFirst().getTitle(),
                "Unnamed genre ids must not surface as provider ids");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()),
                "Channels from unnamed genres must still be cached under Uncategorized");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Portal returned no category names")),
                "Skipping unnamed genres should be reported: " + logs);
        assertTrue(logs.stream().anyMatch(m -> m.contains("Grouping 2 channels under")),
                "Channels must be kept rather than discarded: " + logs);
        assertTrue(categories.stream().noneMatch(c -> "10".equals(c.getTitle()) || "20".equals(c.getTitle())),
                "Provider ids must never be used as category titles");
    }

    @Test
    void reloadCache_stalkerPortal_stopsRetryingAfterOneTransportFailure() throws IOException {
        Account account = createStalkerAccount("acc-transport-failure");
        account.setAction(Account.AccountAction.itv);
        List<String> logs = new ArrayList<>();
        AtomicInteger getAllChannelsCalls = new AtomicInteger();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubRealNullSafeString(fetchMock);
            fetchMock.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), forAccount(account), any()))
                    .thenAnswer(invocation -> {
                        Map<String, String> params = invocation.getArgument(0);
                        if ("get_genres".equals(params.get("action"))) {
                            return FetchAPI.FetchResult.success("""
                                    {"js":[{"id":"10","title":"News","alias":"news","active_sub":true,"censored":0}]}
                                    """, 200);
                        }
                        if ("get_all_channels".equals(params.get("action"))) {
                            getAllChannelsCalls.incrementAndGet();
                            return FetchAPI.FetchResult.transportFailure("http://portal.test/server/load.php", -1, "Read timed out");
                        }
                        return FetchAPI.FetchResult.success("", 200);
                    });
            fetchMock.when(() -> FetchAPI.fetch(anyMap(), forAccount(account)))
                    .thenAnswer(invocation -> {
                        Map<String, String> params = invocation.getArgument(0);
                        if ("get_ordered_list".equals(params.get("action"))) {
                            orderedListCalls.incrementAndGet();
                        }
                        return "";
                    });

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(1, getAllChannelsCalls.get(),
                "A transport failure must not be retried with the other parameter shapes; "
                        + "each retry pays the full response timeout again");
        assertEquals(0, orderedListCalls.get(),
                "The per-category last-resort fan-out must be skipped when the endpoint is not serving");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Stalker get_all_channels request failed")),
                "The transport failure should be surfaced: " + logs);
    }

    private List<String> titles(List<Category> categories) {
        return categories.stream().map(Category::getTitle).toList();
    }

    @Test
    void reloadCache_stalkerPortal_keepsExistingCache_whenGenresAndChannelsAreBothEmpty() throws IOException {
        Account account = createStalkerAccount("acc-derive-nothing");
        account.setAction(Account.AccountAction.itv);
        List<String> logs = new ArrayList<>();
        AtomicInteger orderedListCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubRealNullSafeString(fetchMock);
            fetchMock.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), forAccount(account), any()))
                    .thenReturn(FetchAPI.FetchResult.success("", 200));
            fetchMock.when(() -> FetchAPI.fetch(anyMap(), forAccount(account)))
                    .thenReturn("");

            new CacheServiceImpl().reloadCache(account, logs::add);
        }

        assertEquals(0, ChannelDb.get().getChannelCountForAccount(account.getDbId()),
                "Nothing should be written when both sources are empty");
        assertTrue(logs.stream().anyMatch(m -> m.contains("No categories found. Keeping existing cache.")),
                "Existing behaviour must be preserved when derivation is impossible: " + logs);
    }

    @Test
    void reloadCache_sequentialReloads_doNotContaminateEachOther() throws IOException {
        Account first = createStalkerAccount("acc-bulk-first");
        Account second = createStalkerAccount("acc-bulk-second");
        List<String> logs = new ArrayList<>();
        AtomicInteger firstRequests = new AtomicInteger();
        AtomicInteger secondRequests = new AtomicInteger();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            mockSuccessfulHandshake(handshakeMock);
            stubStalkerFetch(fetchMock, first, false, firstCalls, firstRequests);
            stubStalkerFetch(fetchMock, second, false, secondCalls, secondRequests);

            CacheService cacheService = new CacheServiceImpl();
            cacheService.reloadCache(first, logs::add);
            cacheService.reloadCache(second, logs::add);
        }

        assertTrue(firstRequests.get() > 0, "First account should have been fetched");
        assertTrue(secondRequests.get() > 0, "Second account should have been fetched");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(first.getDbId()),
                "First account cache must survive the second account's reload");
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(second.getDbId()));
        assertEquals(Account.AccountAction.itv, first.getAction());
        assertEquals(Account.AccountAction.itv, second.getAction());
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

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(any(Account.class));

            CategoryService categoryService = mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);
            when(categoryService.parseCategories(anyString(), eq(false)))
                    .thenReturn(List.of(new Category("10", "News", "news", false, 0)));

            fetchMock.when(() -> FetchAPI.fetch(anyMap(), eq(account))).thenReturn("{\"js\":[]}");

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

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class)) {
            HandshakeService handshakeService = mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken(null);
                return null;
            }).when(handshakeService).connect(any(Account.class));

            boolean verified = cacheService.verifyMacAddress(account, "00:11:22:33:44:aa");
            assertFalse(verified);
            assertEquals(originalMac, account.getMacAddress(), "MAC must be restored after failed handshake");
        }
    }

    @Test
    void verifyMacAddress_returnsFalse_whenCategoriesEmpty() {
        Account account = createStalkerAccount("acc-verify-empty-cats");
        CacheService cacheService = new CacheServiceImpl();

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(any(Account.class));

            CategoryService categoryService = mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);
            when(categoryService.parseCategories(anyString(), eq(false))).thenReturn(List.of());

            fetchMock.when(() -> FetchAPI.fetch(anyMap(), eq(account))).thenReturn("{\"js\":[]}");
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

        try (MockedStatic<HandshakeService> handshakeMock = mockStatic(HandshakeService.class);
             MockedStatic<CategoryService> categoryMock = mockStatic(CategoryService.class);
             MockedStatic<FetchAPI> fetchMock = mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = mock(HandshakeService.class);
            handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
            doAnswer(invocation -> {
                Account a = invocation.getArgument(0);
                a.setToken("valid-token");
                return null;
            }).when(handshakeService).connect(any(Account.class));

            CategoryService categoryService = mock(CategoryService.class);
            categoryMock.when(CategoryService::getInstance).thenReturn(categoryService);

            fetchMock.when(() -> FetchAPI.fetch(anyMap(), eq(account)))
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

    /**
     * Stubs both {@link FetchAPI#fetch} and {@link FetchAPI#fetchWithDiagnostics} for the given
     * account. The reloader uses the diagnostics variant so it can log why a request produced no
     * payload; keeping both stubs in one place stops the two call sites from drifting.
     */
    /**
     * {@link FetchAPI} is mocked statically in these tests, which also stubs
     * {@code FetchAPI.nullSafeString} - the helper {@code ChannelService.parseItvChannels} uses to
     * read {@code tv_genre_id}. Without this, every parsed channel would arrive with a null
     * category id. Restore the real behaviour.
     */
    private void stubRealNullSafeString(MockedStatic<FetchAPI> fetchMock) {
        fetchMock.when(() -> FetchAPI.nullSafeString(any(org.json.JSONObject.class), anyString()))
                .thenAnswer(invocation -> {
                    try {
                        return ((org.json.JSONObject) invocation.getArgument(0)).getString(invocation.getArgument(1));
                    } catch (Exception _) {
                        return "";
                    }
                });
    }

    private void stubStalkerFetch(MockedStatic<FetchAPI> fetchMock, Account account,
                                  boolean blankGetAllChannels, AtomicInteger orderedListCalls) {
        stubStalkerFetch(fetchMock, account, blankGetAllChannels, orderedListCalls, null);
    }

    private void stubStalkerFetch(MockedStatic<FetchAPI> fetchMock, Account account,
                                  boolean blankGetAllChannels, AtomicInteger orderedListCalls,
                                  AtomicInteger requestCounter) {
        fetchMock.when(() -> FetchAPI.fetch(anyMap(), forAccount(account)))
                .thenAnswer(invocation -> {
                    if (requestCounter != null) {
                        requestCounter.incrementAndGet();
                    }
                    return mockStalkerApiResponse(invocation.getArgument(0), blankGetAllChannels, orderedListCalls);
                });
        fetchMock.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), forAccount(account), any()))
                .thenAnswer(invocation -> {
                    if (requestCounter != null) {
                        requestCounter.incrementAndGet();
                    }
                    return FetchAPI.FetchResult.success(
                            mockStalkerApiResponse(invocation.getArgument(0), blankGetAllChannels, orderedListCalls), 200);
                });
    }

    /**
     * Matches any {@link Account} carrying the same {@code dbId} as {@code expected}.
     * <p>
     * {@code CacheServiceImpl.reloadCache} hands the reloader a detached copy, so matching on
     * object equality or reference no longer works once the copy has been mutated (token,
     * action). The primary key is stable across the copy.
     */
    private static Account forAccount(Account expected) {
        return argThat(candidate -> candidate != null
                && java.util.Objects.equals(expected.getDbId(), candidate.getDbId()));
    }

    private List<Channel> getAllCachedChannels(Account account) {
        assertTrue(ChannelDb.get().getChannelCountForAccount(account.getDbId()) > 0);
        List<Category> categories = CategoryDb.get().getCategories(account);
        assertTrue(categories.size() >= 1);

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

    private Account createPersistedStalkerAccount(String accountId) {
        Account account = new Account(
                "persisted-test-account-" + accountId,
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
        account.setAction(Account.AccountAction.itv);
        account.setServerPortalUrl("");
        AccountDb.get().save(account);
        Account persisted = AccountDb.get().getAccountByName(account.getAccountName());
        persisted.setAction(Account.AccountAction.itv);
        return persisted;
    }

    private void persistExistingLiveCache(Account account) {
        CategoryDb.get().saveAll(
                List.of(
                        new Category("10", "News", "news", false, 0),
                        new Category("20", "Sports", "sports", false, 0)
                ),
                account
        );
        for (Category category : CategoryDb.get().getCategories(account)) {
            if ("10".equals(category.getCategoryId())) {
                ChannelDb.get().saveAll(List.of(channel("101", "News 1", "10")), category.getDbId(), account);
            } else if ("20".equals(category.getCategoryId())) {
                ChannelDb.get().saveAll(List.of(channel("201", "Sports 1", "20")), category.getDbId(), account);
            }
        }
    }

    private void mockSuccessfulHandshake(MockedStatic<HandshakeService> handshakeMock) {
        mockSuccessfulHandshake(handshakeMock, null);
    }

    private void mockSuccessfulHandshake(MockedStatic<HandshakeService> handshakeMock, String resolvedPortalUrl) {
        HandshakeService handshakeService = mock(HandshakeService.class);
        handshakeMock.when(HandshakeService::getInstance).thenReturn(handshakeService);
        doAnswer(invocation -> {
            Account handshakeAccount = invocation.getArgument(0);
            handshakeAccount.setToken("mock-token");
            if (resolvedPortalUrl != null) {
                handshakeAccount.setServerPortalUrl(resolvedPortalUrl);
            }
            return null;
        }).when(handshakeService).connect(any(Account.class));
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

    private Channel channel(String id, String name, String categoryId) {
        Channel channel = new Channel(id, name, "1", "ffmpeg http://stream/" + id, "", "", "", "", 0, 1, 1,
                null, null, null, null, null);
        channel.setCategoryId(categoryId);
        return channel;
    }

    private String writeUncategorizedOnlyPlaylist(String filename) throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="u-1" tvg-logo="u1.png" group-title="Uncategorized",Uncat One
                http://example.com/uncat/one
                #EXTINF:-1 tvg-id="u-2" tvg-logo="u2.png" group-title="Uncategorized",Uncat Two
                http://example.com/uncat/two
                #EXTINF:-1 tvg-id="u-3" tvg-logo="u3.png" group-title="Uncategorized",Uncat Three
                http://example.com/uncat/three
                """;
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toString();
    }
}
