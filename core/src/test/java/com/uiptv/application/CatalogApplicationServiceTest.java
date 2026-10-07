package com.uiptv.application;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SQLConnection;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.SeasonInfo;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.XtremeApiParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogApplicationServiceTest extends DbBackedTest {

    @Test
    void listCategories_appliesModeAndReturnsResolvedCategories() {
        Account account = new Account("categories", "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://127.0.0.1/mock", false);
        Category category = new Category("10", "Sports", "sports", false, 0);
        category.setDbId("10");

        try (MockedStatic<AccountService> accountServiceStatic = mockStatic(AccountService.class);
             MockedStatic<CategoryService> categoryServiceStatic = mockStatic(CategoryService.class)) {
            AccountService accountService = mock(AccountService.class);
            CategoryService categoryService = mock(CategoryService.class);
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            when(accountService.getById("1")).thenReturn(account);
            when(categoryService.get(any(Account.class))).thenReturn(List.of(category));

            List<Category> resolved = CatalogApplicationService.getInstance().listCategories("1", CatalogMode.SERIES);

            ArgumentCaptor<Account> serviceAccount = ArgumentCaptor.forClass(Account.class);
            verify(categoryService).get(serviceAccount.capture());
            assertEquals(Account.AccountAction.itv, account.getAction());
            assertEquals(Account.AccountAction.series, serviceAccount.getValue().getAction());
            assertEquals(account.getAccountName(), serviceAccount.getValue().getAccountName());
            assertNotSame(account, serviceAccount.getValue());
            assertEquals(1, resolved.size());
            assertEquals("Sports", resolved.getFirst().getTitle());
        }
    }

    @Test
    void listSeriesEpisodes_returnsCachedEpisodesWithWatchedFlags() {
        Account account = createSeriesAccount("series-cached");

        Category category = new Category("api-cat", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), account);
        Category savedCategory = SeriesCategoryDb.get().getCategories(account).get(0);

        Channel episode1 = new Channel();
        episode1.setChannelId("ep-1");
        episode1.setName("Episode 1");
        episode1.setSeason("1");
        episode1.setEpisodeNum("1");
        Channel episode2 = new Channel();
        episode2.setChannelId("ep-2");
        episode2.setName("Episode 2");
        episode2.setSeason("1");
        episode2.setEpisodeNum("2");

        SeriesEpisodeDb.get().saveAll(account, savedCategory.getCategoryId(), "series-1", List.of(episode1, episode2));
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                savedCategory.getCategoryId(),
                "series-1",
                "ep-2",
                "Episode 2",
                "1",
                "2"
        );

        List<Channel> response = CatalogApplicationService.getInstance()
                .listSeriesEpisodes(new CatalogSeriesEpisodesQuery(account.getDbId(), savedCategory.getDbId(), "series-1"));

        assertEquals(2, response.size());
        assertTrue(response.get(1).isWatched());
    }

    @Test
    void listSeriesEpisodes_doesNotClearStalkerRatings() {
        Account account = new Account("stalker-ratings", "user", "pass", "http://portal", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, "http://portal", false);
        account.setDbId("stalker-rating-acc");
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("stalker-ratings");
        saved.setAction(Account.AccountAction.series);

        Category category = new Category("api-cat", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), saved);
        Category savedCategory = SeriesCategoryDb.get().getCategories(saved).get(0);

        Channel episode = new Channel();
        episode.setChannelId("ep-1");
        episode.setName("Episode 1");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        episode.setRating("8.5");

        SeriesEpisodeDb.get().saveAll(saved, savedCategory.getCategoryId(), "series-1", List.of(episode));

        List<Channel> response = CatalogApplicationService.getInstance()
                .listSeriesEpisodes(new CatalogSeriesEpisodesQuery(saved.getDbId(), savedCategory.getDbId(), "series-1"));

        assertEquals(1, response.size());
        assertEquals("8.5", response.getFirst().getRating());
    }

    @ParameterizedTest
    @ValueSource(strings = {"channels", "episodes", "web"})
    void stalkerEpisodeRoutes_enrichRatingsUsingSeriesTitle(String route) throws Exception {
        Account account = new Account("stalker-series-rating", "user", "pass", "http://portal", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, "http://portal", false);
        account.setDbId("stalker-series-acc");
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("stalker-series-rating");
        saved.setAction(Account.AccountAction.series);

        Category category = new Category("api-cat", "Series", "series", false, 0);
        category.setDbId("db-cat");
        SeriesCategoryDb.get().saveAll(List.of(category), saved);
        Category savedCategory = SeriesCategoryDb.get().getCategories(saved).get(0);

        Channel series = new Channel();
        series.setChannelId("series-1");
        series.setName("Test Series");
        SeriesChannelDb.get().saveAll(List.of(series), savedCategory.getDbId(), saved);

        JSONObject providerResponse = new JSONObject().put("js", new JSONObject().put("data", new JSONArray()
                .put(new JSONObject().put("id", "provider-row").put("name", "Season 2")
                        .put("cmd", "http://portal/play/ep-1").put("series", new JSONArray().put(1)))));
        List<Channel> providerEpisodes = ChannelService.getInstance().parseVodChannels(saved, providerResponse.toString(), false);
        assertEquals(1, providerEpisodes.size());

        ChannelService channelService = mock(ChannelService.class);
        ImdbMetadataService imdbMetadataService = mock(ImdbMetadataService.class);

        try (MockedStatic<ChannelService> channelStatic = mockStatic(ChannelService.class);
             MockedStatic<ImdbMetadataService> imdbStatic = mockStatic(ImdbMetadataService.class);
             MockedStatic<HandshakeService> handshakeStatic = mockStatic(HandshakeService.class)) {
            channelStatic.when(ChannelService::getInstance).thenReturn(channelService);
            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbMetadataService);
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(mock(HandshakeService.class));

            when(channelService.getSeries(eq(savedCategory.getCategoryId()), eq("series-1"), eq(saved), any(), any()))
                    .thenReturn(providerEpisodes);

            JSONObject imdbResult = new JSONObject()
                    .put("name", "Test Series")
                    .put("episodesMeta", new JSONArray().put(new JSONObject()
                            .put("season", "2")
                            .put("episodeNum", "1")
                            .put("rating", "8.5")
                            .put("title", "Episode 1")));
            when(imdbMetadataService.findBestEffortDetails(eq("Test Series"), eq(""), anyList()))
                    .thenReturn(imdbResult);

            CatalogApplicationService catalog = CatalogApplicationService.getInstance();
            List<Channel> result = switch (route) {
                case "episodes" -> catalog.listSeriesEpisodes(new CatalogSeriesEpisodesQuery(
                        saved.getDbId(), savedCategory.getDbId(), "series-1"));
                case "web" -> catalog.listWebChannels(new CatalogWebChannelsQuery(
                        saved.getDbId(), CatalogMode.SERIES, savedCategory.getDbId(), "series-1", 0, 20, 1, 0)).items();
                default -> catalog.listChannels(new CatalogChannelsQuery(
                        saved.getDbId(), CatalogMode.SERIES, savedCategory.getDbId(), "series-1"));
            };

            assertEquals(1, result.size());
            assertEquals("8.5", result.getFirst().getRating());
            assertEquals("http://portal/play/ep-1", result.getFirst().getCmd());
            assertEquals("2", result.getFirst().getSeason());
            assertEquals("1", result.getFirst().getEpisodeNum());
            verify(imdbMetadataService).findBestEffortDetails(eq("Test Series"), eq(""), anyList());

            List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(saved, savedCategory.getCategoryId(), "series-1");
            assertEquals(1, cached.size());
            assertEquals("8.5", cached.getFirst().getRating());
        }
    }

    @Test
    void getSeriesDetails_enrichesCanonicalCacheWithoutRenewingProviderFreshness() throws Exception {
        Account account = new Account("stalker-details", "user", "pass", "http://portal", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, "http://portal", false);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("stalker-details");
        saved.setAction(Account.AccountAction.series);
        Category category = new Category("provider-cat", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), saved);
        Category savedCategory = SeriesCategoryDb.get().getCategories(saved).getFirst();
        Channel episode = new Channel();
        episode.setChannelId("episode-1");
        episode.setName("Episode 1");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        episode.setCmd("cmd://original");
        SeriesEpisodeDb.get().saveAll(saved, "provider-cat", "series-1", List.of(episode));
        String originalDbId = SeriesEpisodeDb.get().getEpisodes(saved, "provider-cat", "series-1").getFirst().getDbId();
        long cachedAt = System.currentTimeMillis() - 10_000;
        try (var connection = SQLConnection.connect();
             var statement = connection.prepareStatement("UPDATE SeriesEpisode SET cachedAt=?")) {
            statement.setLong(1, cachedAt);
            statement.executeUpdate();
        }
        ImdbMetadataService metadataService = mock(ImdbMetadataService.class);
        JSONObject metadata = new JSONObject().put("episodesMeta", new JSONArray().put(new JSONObject()
                .put("season", "1").put("episodeNum", "1").put("rating", "9.2")));
        try (MockedStatic<ImdbMetadataService> imdbStatic = mockStatic(ImdbMetadataService.class);
             MockedStatic<HandshakeService> handshakeStatic = mockStatic(HandshakeService.class)) {
            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(metadataService);
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(mock(HandshakeService.class));
            when(metadataService.findBestEffortDetails(anyString(), anyString(), anyList())).thenReturn(metadata);
            CatalogSeriesDetailsResult details = CatalogApplicationService.getInstance().getSeriesDetails(
                    new CatalogSeriesDetailsQuery(saved.getDbId(), savedCategory.getDbId(), "series-1", "Test Show"));
            assertEquals("9.2", details.episodes().getJSONObject(0).getString("rating"));
        }
        Channel cached = SeriesEpisodeDb.get().getEpisodes(saved, "provider-cat", "series-1").getFirst();
        assertEquals("9.2", cached.getRating());
        assertEquals(originalDbId, cached.getDbId());
        assertEquals("cmd://original", cached.getCmd());
        assertTrue(SeriesEpisodeDb.get().getEpisodes(saved, savedCategory.getDbId(), "series-1").isEmpty());
        try (var connection = SQLConnection.connect();
             var statement = connection.prepareStatement("SELECT cachedAt FROM SeriesEpisode");
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals(cachedAt, rows.getLong(1));
        }
    }

    @Test
    void listSeriesEpisodes_loadsEpisodesFromProvider_whenCacheEmpty() {
        Account account = createSeriesAccount("series-provider");

        Episode episode = new Episode();
        episode.setId("ep-21");
        episode.setTitle("Episode 21");
        episode.setCmd("http://origin/ep-21.m3u8");
        episode.setSeason("2");
        episode.setEpisodeNum("1");
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://img/ep-21.png");
        info.setPlot("Plot");
        info.setReleaseDate("2020");
        info.setRating("7.5");
        info.setDuration("45");
        info.setSeason("2");
        episode.setInfo(info);

        EpisodeList list = new EpisodeList();
        list.getEpisodes().add(episode);

        try (MockedStatic<XtremeApiParser> xtremeParser = mockStatic(XtremeApiParser.class)) {
            xtremeParser.when(() -> XtremeApiParser.parseEpisodes(eq("series-21"), any(Account.class))).thenReturn(list);

            List<Channel> result = CatalogApplicationService.getInstance()
                    .listSeriesEpisodes(new CatalogSeriesEpisodesQuery(account.getDbId(), "", "series-21"));

            assertEquals(1, result.size());
            assertEquals("Episode 21", result.getFirst().getName());
            assertEquals("ep-21", result.getFirst().getChannelId());
        }
    }

    @Test
    void getVodDetails_prefersImdbCoverWhileFallingBackForMissingFields() {
        Account account = createVodAccount("vod-details");
        VodCategoryDb.get().saveAll(List.of(new Category("vod-cat", "Movies", "movies", false, 0)), account);
        Category category = VodCategoryDb.get().getCategories(account).getFirst();
        VodChannelDb.get().saveAll(List.of(channel("vod-9", "Provider Title", "https://img/provider.png")), category.getDbId(), account);

        ImdbMetadataService imdbService = mock(ImdbMetadataService.class);
        JSONObject imdb = new JSONObject();
        imdb.put("plot", "IMDB Plot");
        imdb.put("rating", "8.7");
        imdb.put("releaseDate", "2024-06-01");
        imdb.put("cover", "https://img/imdb.png");
        imdb.put("imdbUrl", "https://www.imdb.com/title/tt1234567/");
        when(imdbService.findBestEffortMovieDetails(anyString(), anyString(), anyList()))
                .thenReturn(imdb);

        try (MockedStatic<ImdbMetadataService> imdbStatic = mockStatic(ImdbMetadataService.class)) {
            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbService);

            CatalogVodDetailsResult response = CatalogApplicationService.getInstance().getVodDetails(
                    new CatalogVodDetailsQuery(account.getDbId(), category.getDbId(), "vod-9", "Movie Nine")
            );

            assertEquals("Movie Nine", response.name());
            assertEquals("https://img/imdb.png", response.cover());
            assertEquals("IMDB Plot", response.plot());
            assertEquals("8.7", response.rating());
            assertEquals("2024-06-01", response.releaseDate());
            assertEquals("https://www.imdb.com/title/tt1234567/", response.imdbUrl());
        }
    }

    @Test
    void getSeriesDetails_mergesProviderAndImdbMetadata() {
        Account account = new Account("series-acc", "user", "pass", "http://xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://xtreme", false);
        account.setDbId("acc-1");
        account.setAction(Account.AccountAction.series);

        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setTitle("Episode 1");
        episode.setCmd("http://origin/ep-1.m3u8");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://img/ep-1.png");
        info.setPlot("Provider plot");
        info.setReleaseDate("2019");
        info.setRating("8.1");
        info.setDuration("42");
        episode.setInfo(info);

        SeasonInfo seasonInfo = new SeasonInfo();
        seasonInfo.setName("Provider Season");
        seasonInfo.setReleaseDate("2018");

        EpisodeList episodeList = new EpisodeList();
        episodeList.setSeasonInfo(seasonInfo);
        episodeList.getEpisodes().add(episode);

        JSONObject imdbFirst = new JSONObject()
                .put("name", "IMDB Title")
                .put("cover", "http://cover")
                .put("episodesMeta", new JSONArray().put(new JSONObject()
                        .put("season", "1")
                        .put("episodeNum", "1")
                        .put("plot", "Meta plot")
                        .put("logo", "http://logo")
                        .put("rating", "9.0")
                        .put("title", "Episode 1")));
        JSONObject imdbFallback = new JSONObject()
                .put("plot", "Fallback plot")
                .put("releaseDate", "2021");

        AccountService accountService = mock(AccountService.class);
        ImdbMetadataService imdbMetadataService = mock(ImdbMetadataService.class);

        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<XtremeApiParser> xtremeParser = mockStatic(XtremeApiParser.class);
             MockedStatic<ImdbMetadataService> imdbStatic = mockStatic(ImdbMetadataService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("acc-1")).thenReturn(account);

            xtremeParser.when(() -> XtremeApiParser.parseEpisodes("series-1", account)).thenReturn(episodeList);

            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbMetadataService);
            when(imdbMetadataService.findBestEffortDetails(anyString(), anyString(), anyList()))
                    .thenReturn(imdbFirst, imdbFallback);

            CatalogSeriesDetailsResult response = CatalogApplicationService.getInstance()
                    .getSeriesDetails(new CatalogSeriesDetailsQuery("acc-1", "cat-1", "series-1", "Show (2021)"));

            assertEquals("IMDB Title", response.seasonInfo().getString("name"));
            assertEquals("2018", response.seasonInfo().getString("releaseDate"));
            assertEquals(1, response.episodes().length());
            JSONObject ep = response.episodes().getJSONObject(0);
            assertEquals("Episode 1", ep.getString("name"));
            assertTrue(ep.getString("description").contains("Meta") || ep.getString("description").contains("Provider"));
            assertTrue(ep.getString("logo").contains("http://"));
            assertEquals("8.1", ep.getString("rating"));
        }
    }

    @Test
    void getSeriesDetails_appliesMetaRatingWhenProviderRatingIsBlank() {
        Account account = new Account("series-acc-blank", "user", "pass", "http://xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://xtreme", false);
        account.setDbId("acc-blank");
        account.setAction(Account.AccountAction.series);

        Episode episode = new Episode();
        episode.setId("ep-blank");
        episode.setTitle("Blank Rating Episode");
        episode.setCmd("http://origin/ep-blank.m3u8");
        episode.setSeason("1");
        episode.setEpisodeNum("1");
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("http://img/ep-blank.png");
        info.setPlot("Provider plot");
        info.setReleaseDate("2019");
        info.setRating("");
        info.setDuration("42");
        episode.setInfo(info);

        SeasonInfo seasonInfo = new SeasonInfo();
        seasonInfo.setName("Provider Season");
        seasonInfo.setReleaseDate("2018");

        EpisodeList episodeList = new EpisodeList();
        episodeList.setSeasonInfo(seasonInfo);
        episodeList.getEpisodes().add(episode);

        JSONObject imdbFirst = new JSONObject()
                .put("name", "IMDB Title")
                .put("cover", "http://cover")
                .put("episodesMeta", new JSONArray().put(new JSONObject()
                        .put("season", "1")
                        .put("episodeNum", "1")
                        .put("plot", "Meta plot")
                        .put("logo", "http://logo")
                        .put("rating", "9.0")
                        .put("title", "Blank Rating Episode")));

        AccountService accountService = mock(AccountService.class);
        ImdbMetadataService imdbMetadataService = mock(ImdbMetadataService.class);

        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<XtremeApiParser> xtremeParser = mockStatic(XtremeApiParser.class);
             MockedStatic<ImdbMetadataService> imdbStatic = mockStatic(ImdbMetadataService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("acc-blank")).thenReturn(account);

            xtremeParser.when(() -> XtremeApiParser.parseEpisodes("series-blank", account)).thenReturn(episodeList);

            imdbStatic.when(ImdbMetadataService::getInstance).thenReturn(imdbMetadataService);
            when(imdbMetadataService.findBestEffortDetails(anyString(), anyString(), anyList()))
                    .thenReturn(imdbFirst);

            CatalogSeriesDetailsResult response = CatalogApplicationService.getInstance()
                    .getSeriesDetails(new CatalogSeriesDetailsQuery("acc-blank", "cat-1", "series-blank", "Show (2021)"));

            assertEquals(1, response.episodes().length());
            JSONObject ep = response.episodes().getJSONObject(0);
            assertEquals("9.0", ep.getString("rating"));
            assertEquals("9.0", SeriesEpisodeDb.get().getEpisodes(account, "cat-1", "series-blank").getFirst().getRating());
        }
    }

    @Test
    void listChannels_readsAllCategoriesAndAppliesSeriesWatchedFlags() throws Exception {
        Account account = createSeriesAccount("series-all");
        Category cat1 = new Category("api-1", "Action", "action", false, 0);
        Category cat2 = new Category("api-2", "Drama", "drama", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(cat1, cat2), account);
        List<Category> saved = SeriesCategoryDb.get().getCategories(account);
        Category saved1 = saved.get(0);
        Category saved2 = saved.get(1);

        Channel row1 = new Channel();
        row1.setChannelId("series-a");
        row1.setName("Series A");
        row1.setCategoryId(saved1.getCategoryId());
        Channel row2 = new Channel();
        row2.setChannelId("series-b");
        row2.setName("Series B");
        row2.setCategoryId(saved2.getCategoryId());

        ChannelService channelService = mock(ChannelService.class);
        try (MockedStatic<ChannelService> channelStatic = mockStatic(ChannelService.class)) {
            channelStatic.when(ChannelService::getInstance).thenReturn(channelService);
            when(channelService.get(saved1.getCategoryId(), account, saved1.getDbId())).thenReturn(List.of(row1));
            when(channelService.get(saved2.getCategoryId(), account, saved2.getDbId())).thenReturn(List.of(row2));

            SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                    account, saved1.getCategoryId(), "series-a", "ep-1", "Episode 1", "1", "1"
            );

            List<Channel> channels = CatalogApplicationService.getInstance()
                    .listChannels(new CatalogChannelsQuery(account.getDbId(), CatalogMode.SERIES, "All", ""));

            assertEquals(2, channels.size());
            assertTrue(channels.stream().anyMatch(c -> "series-a".equals(c.getChannelId()) && c.isWatched()));
            assertTrue(channels.stream().anyMatch(c -> "series-b".equals(c.getChannelId()) && !c.isWatched()));
        }
    }

    @Test
    void listWebChannels_buildsPagedResult_forStalkerFallbackPageOne() throws Exception {
        Account account = new Account("stalker-web", "user", "pass", "http://portal", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, "http://portal", false);
        account.setDbId("stalker-1");
        account.setAction(Account.AccountAction.series);

        Category category = new Category("api-cat", "Series", "series", false, 0);
        category.setDbId("db-cat");

        AccountService accountService = mock(AccountService.class);
        ChannelService channelService = mock(ChannelService.class);
        try (MockedStatic<AccountService> accountStatic = mockStatic(AccountService.class);
             MockedStatic<ChannelService> channelStatic = mockStatic(ChannelService.class);
             MockedStatic<FetchAPI> fetchStatic = mockStatic(FetchAPI.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            channelStatic.when(ChannelService::getInstance).thenReturn(channelService);
            when(accountService.getById("stalker-1")).thenReturn(account);
            when(channelService.parsePagination(anyString(), isNull())).thenReturn(new Pagination());

            Channel paged = new Channel();
            paged.setChannelId("series-1");
            paged.setName("Series 1");
            paged.setCategoryId("api-cat");
            when(channelService.parseVodChannels(eq(account), anyString(), eq(true)))
                    .thenReturn(List.of(), List.of(paged));
            fetchStatic.when(() -> FetchAPI.fetch(anyMap(), eq(account))).thenReturn("{\"page\":0}", "{\"page\":1}");

            try (MockedStatic<SeriesCategoryDb> seriesCategoryStatic = mockStatic(SeriesCategoryDb.class)) {
                SeriesCategoryDb seriesCategoryDb = mock(SeriesCategoryDb.class);
                seriesCategoryStatic.when(SeriesCategoryDb::get).thenReturn(seriesCategoryDb);
                when(seriesCategoryDb.getById("db-cat")).thenReturn(category);
                when(seriesCategoryDb.getById("api-cat")).thenReturn(category);

                CatalogPagedChannelsResult result = CatalogApplicationService.getInstance()
                        .listWebChannels(new CatalogWebChannelsQuery("stalker-1", CatalogMode.SERIES, "db-cat", "", 0, 120, 1, 0));

                assertEquals(1, result.items().size());
                assertEquals(1, result.apiOffset());
            }
        }
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(Account.AccountAction.series);
        return saved;
    }

    private Account createVodAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.vod);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.vod);
        return persisted;
    }

    private Channel channel(String channelId, String name, String logo) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setLogo(logo);
        channel.setCmd("http://example.com/" + channelId);
        return channel;
    }
}
