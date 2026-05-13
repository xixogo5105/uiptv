package com.uiptv.service;

import com.uiptv.util.I18n;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImdbMetadataServiceTest {

    private final ImdbMetadataService service = ImdbMetadataService.getInstance();

    @Test
    void titleAndQueryHelpers_normalizeEpisodeAndSearchInputs() throws Exception {
        assertEquals("the office", invokeString("normalizeTitle", "The Office Season 2 HD"));
        assertEquals("", invokeString("sanitizeEpisodeTitle", "Episode 12 - "));
        assertEquals("Pilot", invokeString("sanitizeEpisodeTitle", "Pilot"));
        assertTrue((Boolean) invoke("isGenericEpisodeTitle", new Class[]{String.class}, "E12"));
        assertFalse((Boolean) invoke("isGenericEpisodeTitle", new Class[]{String.class}, "Finale"));

        @SuppressWarnings("unchecked")
        List<String> queries = (List<String>) invoke("buildSearchQueries", new Class[]{String.class, List.class}, "The Office 2005 Season 2", List.of("Office US 2005"));
        assertTrue(queries.contains("The Office 2005 Season 2"));
        assertTrue(queries.stream().anyMatch(q -> q.contains("office")));
        assertTrue((Double) invoke("titleSimilarity", new Class[]{String.class, String.class}, "the office us", "office us") >= 0.5d);
        assertEquals("first", invoke("firstNonBlank", new Class[]{String[].class}, (Object) new String[]{"", "first", "second"}));
    }

    @Test
    void jsonMappers_coverGenresEpisodesAndLocalizedReplacement() throws Exception {
        JSONObject result = new JSONObject();
        JSONObject localized = new JSONObject()
                .put("name", "Localized Name")
                .put("plot", "Localized Plot")
                .put("genre", "Drama")
                .put("releaseDate", "2025-01-01")
                .put("cover", "https://img/cover.jpg")
                .put("rating", "7.5");
        invoke("applyLocalizedTmdbFields", new Class[]{JSONObject.class, JSONObject.class}, result, localized);
        assertEquals("Localized Name", result.getString("name"));
        assertEquals("Drama", result.getString("genre"));

        JSONObject tmdbEpisode = new JSONObject()
                .put("name", "Episode 7")
                .put("overview", "Episode plot")
                .put("air_date", "2024-02-03")
                .put("still_path", "/still.png");
        JSONObject mappedEpisode = (JSONObject) invoke("mapTmdbEpisodeMeta", new Class[]{JSONObject.class}, tmdbEpisode);
        assertEquals("", mappedEpisode.getString("title"));
        assertEquals("Episode plot", mappedEpisode.getString("plot"));
        assertTrue(mappedEpisode.getString("logo").contains("/still.png"));

        JSONArray genres = new JSONArray()
                .put(new JSONObject().put("name", "Drama"))
                .put(new JSONObject().put("name", "Comedy"));
        @SuppressWarnings("unchecked")
        List<String> genreNames = (List<String>) invoke("extractTmdbGenreNames", new Class[]{JSONArray.class}, genres);
        assertEquals(List.of("Drama", "Comedy"), genreNames);

        JSONArray values = new JSONArray().put("A").put("").put("B");
        assertEquals("A, B", invokeString("joinNonBlankArray", values));
        assertEquals("A, B", invoke("joinStringArray", new Class[]{JSONArray.class, int.class}, values, 3).toString());
    }

    @Test
    void tmdbAndTvMazeHelpers_coverIndexingAndMatchingLogic() throws Exception {
        JSONArray tvMazeEpisodes = new JSONArray()
                .put(new JSONObject().put("season", 1).put("number", 2).put("name", "Pilot").put("summary", "<p>Summary</p>").put("airdate", "2024-01-10"))
                .put(new JSONObject().put("season", 2).put("number", 1).put("name", "Return"));
        Object index = invoke("buildTvMazeEpisodeIndex", new Class[]{JSONArray.class}, tvMazeEpisodes);
        JSONObject seasonEpisodeRow = new JSONObject().put("season", "1").put("episodeNum", "2").put("title", "Other");
        JSONObject titleRow = new JSONObject().put("season", "").put("episodeNum", "").put("title", "Return");

        JSONObject matchedBySeasonEpisode = (JSONObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), JSONObject.class}, index, seasonEpisodeRow);
        JSONObject matchedByTitle = (JSONObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), JSONObject.class}, index, titleRow);
        assertEquals("Pilot", matchedBySeasonEpisode.getString("name"));
        assertEquals("Return", matchedByTitle.getString("name"));

        invoke("applyTvMazeEpisodeMeta", new Class[]{JSONObject.class, JSONObject.class}, seasonEpisodeRow, matchedBySeasonEpisode);
        assertEquals("Summary", seasonEpisodeRow.getString("plot"));
        assertEquals("2024-01-10", seasonEpisodeRow.getString("releaseDate"));

        @SuppressWarnings("unchecked")
        Map<String, JSONObject> bySeasonEpisode = (Map<String, JSONObject>) invoke("indexEpisodesBySeasonEpisode", new Class[]{JSONArray.class}, new JSONArray().put(seasonEpisodeRow));
        @SuppressWarnings("unchecked")
        Set<String> seasons = (Set<String>) invoke("collectTmdbSeasons", new Class[]{Map.class}, bySeasonEpisode);
        assertEquals(Set.of("1"), seasons);
        assertEquals("1:2", invokeString("seasonEpisodeKey", "1", "2"));
        assertNull(invoke("seasonEpisodeKey", new Class[]{String.class, String.class}, "season", "episode"));
    }


    @Test
    void additionalMetadataHelpers_coverScoringReplacementAndGenreLogic() throws Exception {
        assertTrue((Integer) invoke("scoreCandidate", new Class[]{String.class, String.class, String.class}, "office", "The Office", "TV Series") >
                (Integer) invoke("scoreCandidate", new Class[]{String.class, String.class, String.class}, "office", "Random Movie", "movie"));

        JSONObject result = new JSONObject();
        invoke("applyImdbGenre", new Class[]{JSONObject.class, Object.class}, result, new JSONArray().put("Drama").put("Comedy"));
        assertEquals("Drama, Comedy", result.getString("genre"));

        JSONArray people = new JSONArray().put(new JSONObject().put("name", "A")).put(new JSONObject().put("name", "B"));
        assertEquals("A, B", invoke("joinPersonNames", new Class[]{JSONArray.class}, people).toString());

        JSONObject target = new JSONObject().put("name", "Existing");
        JSONObject source = new JSONObject().put("name", "Replacement").put("plot", "Plot");
        invoke("mergeIfPresent", new Class[]{JSONObject.class, JSONObject.class, String.class}, target, source, "plot");
        invoke("mergeMissing", new Class[]{JSONObject.class, JSONObject.class, String.class}, target, source, "name");
        invoke("replaceIfPresent", new Class[]{JSONObject.class, JSONObject.class, String.class}, target, source, "name");
        assertEquals("Replacement", target.getString("name"));
        assertEquals("Plot", target.getString("plot"));

        assertTrue((Boolean) invoke("hasAnyEpisodePlot", new Class[]{JSONArray.class}, new JSONArray().put(new JSONObject().put("plot", "filled"))));
        assertFalse((Boolean) invoke("hasAnyEpisodePlot", new Class[]{JSONArray.class}, new JSONArray().put(new JSONObject().put("plot", ""))));
        assertTrue(invoke("extractJsonLd", new Class[]{String.class}, "<script type=\"application/ld+json\">{}</script>").toString().contains("{}"));

        JSONObject localized = new JSONObject();
        invoke("populateTmdbLocalizedDetails", new Class[]{JSONObject.class, JSONObject.class}, localized,
                new JSONObject()
                        .put("name", "Localized")
                        .put("overview", "Plot")
                        .put("vote_average", 8.5)
                        .put("release_date", "2024-01-01")
                        .put("poster_path", "/poster.png")
                        .put("genres", new JSONArray().put(new JSONObject().put("name", "Drama"))));
        assertEquals("Localized", localized.getString("name"));
        assertEquals("Drama", localized.getString("genre"));
        assertTrue(localized.getString("cover").contains("poster.png"));
    }

    @Test
    void stringAndHeaderHelpers_coverSanitizationAndFallbackFormatting() throws Exception {
        assertEquals("112", invokeString("safeNumeric", "S01E12"));
        assertEquals("", invokeString("safeNumeric", "none"));
        assertEquals("Fish & Chips 'Tonight'", invokeString("stripHtml", "<p>Fish &amp; Chips &#39;Tonight&#39;</p>"));
        assertTrue(invokeString("buildAcceptLanguageHeader").contains("en"));
        assertTrue(invokeString("withLanguageQuery", "https://imdb.test/title/tt123").contains("language="));
        assertEquals("", invokeString("withLanguageQuery", ""));
        assertTrue((Boolean) invoke("canFetchTmdbLocalizedDetails", new Class[]{String.class, String.class}, "123", "movie"));
        assertFalse((Boolean) invoke("canFetchTmdbLocalizedDetails", new Class[]{String.class, String.class}, "", "movie"));
        assertTrue(invokeString("buildTmdbLocalizedUrl", "123", "movie", "fr-FR").contains("language=fr-FR"));
        assertTrue(((Map<?, ?>) invoke("buildTmdbHeaders", new Class[]{String.class}, "token-123")).containsKey("Authorization"));
        assertEquals("https://image.tmdb.org/t/p/w500/poster.png", extractPosterCover());
    }

    @Test
    void tmdbFetchHelpers_coverBearerTokenLocalizedFetchAndEpisodeMerge() throws Exception {
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setTmdbReadAccessToken(" bearer-token ");

        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);

            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(
                    Mockito.contains("/movie/123?language=fr-FR"),
                    Mockito.anyMap(),
                    Mockito.eq("GET")
            )).thenReturn(new com.uiptv.util.HttpUtil.HttpResult(
                    com.uiptv.util.HttpUtil.STATUS_OK,
                    """
                    {"name":"Nom Localise","overview":"Resume","vote_average":7.1,"release_date":"2024-05-01",
                     "poster_path":"/poster.jpg","genres":[{"name":"Drama"}]}
                    """,
                    Map.of(), Map.of()
            ));
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(
                    Mockito.contains("/tv/321/season/1?language=fr-FR"),
                    Mockito.anyMap(),
                    Mockito.eq("GET")
            )).thenReturn(new com.uiptv.util.HttpUtil.HttpResult(
                    com.uiptv.util.HttpUtil.STATUS_OK,
                    """
                    {"episodes":[
                      {"episode_number":2,"name":"Episode 2 Local","overview":"Localized plot","air_date":"2024-02-02","still_path":"/still2.jpg"}
                    ]}
                    """,
                    Map.of(), Map.of()
            ));

            JSONObject localized = (JSONObject) invoke("fetchTmdbLocalizedDetails",
                    new Class[]{String.class, String.class, String.class}, "123", "movie", "fr-FR");
            assertEquals("Nom Localise", localized.getString("name"));
            assertEquals("Drama", localized.getString("genre"));

            assertEquals("123", invoke("resolveTmdbMediaId",
                    new Class[]{JSONObject.class, JSONObject.class},
                    new JSONObject().put("tmdbMediaId", "123"), new JSONObject()));
            assertTrue((Boolean) invoke("isSuccessfulTmdbResponse",
                    new Class[]{com.uiptv.util.HttpUtil.HttpResult.class},
                    new com.uiptv.util.HttpUtil.HttpResult(200, "{}", Map.of(), Map.of())));

            JSONArray episodesMeta = new JSONArray()
                    .put(new JSONObject().put("season", "1").put("episodeNum", "2").put("title", "Episode 2"));
            invoke("enrichEpisodesMetaWithTmdb", new Class[]{JSONArray.class, String.class, String.class}, episodesMeta, "321", "fr-FR");
            JSONObject merged = episodesMeta.getJSONObject(0);
            assertEquals("Localized plot", merged.getString("plot"));
            assertEquals("2024-02-02", merged.getString("releaseDate"));
            assertTrue(merged.getString("logo").contains("still2.jpg"));
        }
    }

    @Test
    void findBestEffortDetails_mergesSuggestionImdbCinemetaAndTvMazeMetadata() {
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setEnableThumbnails(true);

        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);

            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(
                    Mockito.anyString(),
                    Mockito.anyMap(),
                    Mockito.eq("GET")
            )).thenAnswer(invocation -> new com.uiptv.util.HttpUtil.HttpResult(
                    com.uiptv.util.HttpUtil.STATUS_OK,
                    metadataBodyFor(invocation.getArgument(0, String.class)),
                    Map.of(),
                    Map.of()
            ));

            JSONObject details = service.findBestEffortDetails("Example Show Season 1", "", List.of("Example Show 2024"));

            assertEquals("IMDb Name", details.getString("name"));
            assertEquals("https://www.imdb.com/title/tt1234567/", details.getString("imdbUrl"));
            assertEquals("IMDb Plot", details.getString("plot"));
            assertEquals("Actor One, Actor Two", details.getString("cast"));
            assertEquals("Director One", details.getString("director"));
            assertEquals("8.4", details.getString("rating"));
            assertEquals("2024-01-01", details.getString("releaseDate"));
            assertTrue(details.has("episodesMeta"));
            JSONObject episode = details.getJSONArray("episodesMeta").getJSONObject(0);
            assertEquals("Episode 1 - Pilot", episode.getString("title"));
            assertEquals("TVMaze summary", episode.getString("plot"));
            assertEquals("2024-02-01", episode.getString("releaseDate"));
        }
    }

    @Test
    void findBestEffortDetails_returnsEmptyWhenThumbnailsDisabled() {
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setEnableThumbnails(false);

        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);

            assertTrue(service.findBestEffortDetails("Anything", "tt1234567").isEmpty());
            assertTrue(service.findBestEffortDetails("Anything", "tt1234567", List.of("hint")).isEmpty());
            assertTrue(service.findBestEffortMovieDetails("Anything", "tt1234567").isEmpty());
            assertTrue(service.findBestEffortMovieDetails("Anything", "tt1234567", List.of("hint")).isEmpty());
        }
    }

    @Test
    void findBestEffortDetails_usesConsistentPreferredIdWhenSearchHasNoCandidate() {
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setEnableThumbnails(true);

        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.anyString(), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(new com.uiptv.util.HttpUtil.HttpResult(404, "", Map.of(), Map.of()));

            JSONObject details = service.findBestEffortMovieDetails("", "tt7654321");

            assertEquals("tt7654321", details.getString("tmdb"));
            assertEquals("https://www.imdb.com/title/tt7654321/", details.getString("imdbUrl"));

            JSONObject seriesDetails = service.findBestEffortDetails("", "tt7654321");
            JSONObject movieDetailsWithHints = service.findBestEffortMovieDetails("", "tt7654321", List.of("hint"));
            assertEquals("tt7654321", seriesDetails.getString("tmdb"));
            assertEquals("tt7654321", movieDetailsWithHints.getString("tmdb"));
        }
    }

    @Test
    void suggestionAndCandidateHelpers_coverEmptyMalformedAndFallbackBranches() throws Exception {
        assertTrue(((JSONObject) invoke("searchBestCandidate", new Class[]{List.class}, (Object) null)).isEmpty());
        assertTrue(((JSONObject) invoke("searchBestCandidate", new Class[]{List.class}, List.of(""))).isEmpty());
        assertNull(invoke("findCandidateMatch", new Class[]{String.class, String.class}, "primary", ""));

        JSONArray candidates = new JSONArray()
                .put(new JSONObject().put("id", "").put("l", "No Id"))
                .put(new JSONObject().put("id", "tt2").put("l", "Desired Show").put("q", "episode"))
                .put(new JSONObject().put("id", "tt1").put("l", "Desired Show").put("q", "TV Series"));
        JSONObject best = (JSONObject) invoke("findBestInSuggestions", new Class[]{JSONArray.class, String.class}, candidates, "Desired Show");
        assertEquals("tt1", best.getString("tmdb"));

        JSONObject mapped = (JSONObject) invoke("mapSuggestionCandidate", new Class[]{JSONObject.class},
                new JSONObject().put("id", "tt9").put("l", "Mapped").put("q", "movie"));
        assertEquals("Mapped", mapped.getString("name"));
        assertFalse(mapped.has("cover"));

        try (MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.contains("/x/"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(new com.uiptv.util.HttpUtil.HttpResult(200, "{\"d\":[]}", Map.of(), Map.of()));
            assertNotNull(invoke("querySuggestions", new Class[]{String.class}, "!bad"));

            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.anyString(), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenThrow(new RuntimeException("network"));
            assertNull(invoke("querySuggestions", new Class[]{String.class}, "broken"));
            assertEquals("", invokeString("httpGet", "https://broken.test"));
        }
    }

    @Test
    void metadataMappers_coverNullsLimitsAndNoopBranches() throws Exception {
        JSONObject details = new JSONObject().put("episodesMeta", new JSONArray().put(new JSONObject().put("title", "Existing")));
        JSONObject meta = new JSONObject().put("episodesMeta", new JSONArray().put(new JSONObject().put("title", "Incoming")));
        invoke("mergeCinemetaMetadata", new Class[]{JSONObject.class, JSONObject.class, boolean.class}, details, meta, false);
        assertEquals("Existing", details.getJSONArray("episodesMeta").getJSONObject(0).getString("title"));

        invoke("mergeCinemetaMetadata", new Class[]{JSONObject.class, JSONObject.class, boolean.class}, details, meta, true);
        assertEquals("Incoming", details.getJSONArray("episodesMeta").getJSONObject(0).getString("title"));

        JSONObject genre = new JSONObject();
        invoke("applyImdbGenre", new Class[]{JSONObject.class, Object.class}, genre, "Comedy");
        assertEquals("Comedy", genre.getString("genre"));

        assertEquals("", invoke("joinPersonNames", new Class[]{JSONArray.class}, (Object) null));
        JSONArray manyPeople = new JSONArray();
        for (int i = 0; i < 10; i++) {
            manyPeople.put(new JSONObject().put("name", "P" + i));
        }
        assertEquals(8, invokeString("joinPersonNames", manyPeople).split(", ").length);
        assertEquals("", invoke("joinStringArray", new Class[]{JSONArray.class, int.class}, null, 3));
        assertEquals("A, B", invoke("joinStringArray", new Class[]{JSONArray.class, int.class}, new JSONArray().put("A").put("B").put("C"), 2));

        assertEquals("", invokeString("extractJsonLd", "<html></html>"));
        assertEquals(0.0d, (Double) invoke("titleSimilarity", new Class[]{String.class, String.class}, "", "title"));
        assertEquals(1.0d, (Double) invoke("titleSimilarity", new Class[]{String.class, String.class}, "Same", "Same"));
        assertEquals("", invoke("firstNonBlank", new Class[]{String[].class}, (Object) null));

        @SuppressWarnings("unchecked")
        Map<String, JSONObject> emptyIndex = (Map<String, JSONObject>) invoke("indexEpisodesBySeasonEpisode", new Class[]{JSONArray.class},
                new JSONArray().put(JSONObject.NULL).put(new JSONObject().put("season", "x").put("episodeNum", "y")));
        assertTrue(emptyIndex.isEmpty());
        invoke("mergeLocalizedTmdbEpisode", new Class[]{Map.class, String.class, JSONObject.class}, emptyIndex, "1", null);
        invoke("mergeLocalizedTmdbEpisode", new Class[]{Map.class, String.class, JSONObject.class}, emptyIndex, "bad", new JSONObject().put("episode_number", 1));
        invoke("mergeLocalizedTmdbEpisode", new Class[]{Map.class, String.class, JSONObject.class}, emptyIndex, "1", new JSONObject().put("episode_number", 1));

        assertTrue(((JSONArray) invoke("fetchTmdbSeasonEpisodes", new Class[]{String.class, String.class, String.class}, "", "1", "en-US")).isEmpty());
        assertTrue(((JSONObject) invoke("mapTmdbEpisodeMeta", new Class[]{JSONObject.class}, (Object) null)).isEmpty());
    }

    @Test
    void tmdbFetchHelpers_coverFailureAndTokenBranches() throws Exception {
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);

        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(null);
            assertEquals("", invokeString("resolveConfiguredTmdbBearerToken"));
            assertTrue(((JSONObject) invoke("fetchTmdbLocalizedDetails", new Class[]{String.class, String.class, String.class}, "123", "movie", "en-US")).isEmpty());
            assertTrue(((JSONArray) invoke("fetchTmdbSeasonEpisodes", new Class[]{String.class, String.class, String.class}, "123", "1", "en-US")).isEmpty());

            com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
            configuration.setTmdbReadAccessToken("token");
            Mockito.when(configurationService.read()).thenReturn(configuration);
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.contains("/movie/500"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(new com.uiptv.util.HttpUtil.HttpResult(500, "{}", Map.of(), Map.of()));
            assertTrue(((JSONObject) invoke("fetchTmdbLocalizedDetails", new Class[]{String.class, String.class, String.class}, "500", "movie", "en-US")).isEmpty());

            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.contains("/season/2"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(new com.uiptv.util.HttpUtil.HttpResult(200, "{}", Map.of(), Map.of()));
            assertTrue(((JSONArray) invoke("fetchTmdbSeasonEpisodes", new Class[]{String.class, String.class, String.class}, "123", "2", "en-US")).isEmpty());

            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.contains("/season/3"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenThrow(new RuntimeException("network"));
            assertTrue(((JSONArray) invoke("fetchTmdbSeasonEpisodes", new Class[]{String.class, String.class, String.class}, "123", "3", "en-US")).isEmpty());
        }
    }

    @Test
    void remoteMetadataHelpers_coverEmptyMalformedAndLocalizationBranches() throws Exception {
        try (MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.anyString(), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenAnswer(invocation -> new com.uiptv.util.HttpUtil.HttpResult(200,
                            branchMetadataBodyFor(invocation.getArgument(0, String.class)), Map.of(), Map.of()));

            assertTrue(((JSONObject) invoke("fetchImdbTitleDetails", new Class[]{String.class}, "blank")).isEmpty());
            assertTrue(((JSONObject) invoke("fetchImdbTitleDetails", new Class[]{String.class}, "nojson")).isEmpty());
            JSONObject imdb = (JSONObject) invoke("fetchImdbTitleDetails", new Class[]{String.class}, "genrestring");
            assertEquals("String Genre Movie", imdb.getString("name"));
            assertEquals("Comedy", imdb.getString("genre"));

            assertTrue(((JSONObject) invoke("fetchCinemetaSeriesDetails", new Class[]{String.class}, "empty")).isEmpty());
            assertTrue(((JSONObject) invoke("fetchCinemetaSeriesDetails", new Class[]{String.class}, "metanull")).isEmpty());
            JSONObject series = (JSONObject) invoke("fetchCinemetaSeriesDetails", new Class[]{String.class}, "videosnull");
            assertEquals("Video Null Show", series.getString("name"));
            assertTrue(series.getJSONArray("episodesMeta").isEmpty());

            assertTrue(((JSONObject) invoke("fetchCinemetaMovieDetails", new Class[]{String.class}, "empty")).isEmpty());
            assertTrue(((JSONObject) invoke("fetchCinemetaMovieDetails", new Class[]{String.class}, "metanull")).isEmpty());
            JSONObject movie = (JSONObject) invoke("fetchCinemetaMovieDetails", new Class[]{String.class}, "releasedonly");
            assertEquals("2024-03-04", movie.getString("releaseDate"));

            assertTrue(((JSONArray) invoke("fetchTvMazeEpisodes", new Class[]{String.class, String.class}, "tt0", "")).isEmpty());
            assertTrue(((JSONArray) invoke("fetchTvMazeEpisodes", new Class[]{String.class, String.class}, "tt0", "Empty Search")).isEmpty());
            assertEquals(-1, invoke("resolveTvMazeShowId", new Class[]{String.class, String.class}, "tt0", ""));
            assertEquals(-1, invoke("resolveTvMazeShowId", new Class[]{String.class, String.class}, "tt0", "Bad Json"));
            assertEquals(77, invoke("resolveTvMazeShowId", new Class[]{String.class, String.class}, "tt-missing", "Best Match"));
        }

        I18n.setLocale("fr-FR");
        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setTmdbReadAccessToken("token");
        try (MockedStatic<ConfigurationService> configurationStatic = Mockito.mockStatic(ConfigurationService.class);
             MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
            configurationStatic.when(ConfigurationService::getInstance).thenReturn(configurationService);
            Mockito.when(configurationService.read()).thenReturn(configuration);
            httpUtilStatic.when(() -> com.uiptv.util.HttpUtil.sendRequest(Mockito.anyString(), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenAnswer(invocation -> new com.uiptv.util.HttpUtil.HttpResult(200,
                            branchMetadataBodyFor(invocation.getArgument(0, String.class)), Map.of(), Map.of()));

            JSONObject details = new JSONObject().put("episodesMeta", new JSONArray()
                    .put(new JSONObject().put("season", "1").put("episodeNum", "1").put("title", "Old")));
            JSONObject primary = new JSONObject().put("tmdbMediaId", "999");
            JSONObject secondary = new JSONObject();

            invoke("applyTmdbLocalization", new Class[]{JSONObject.class, JSONObject.class, JSONObject.class, boolean.class},
                    details, primary, secondary, false);

            assertEquals("Nom TV", details.getString("name"));
            assertEquals("Episode FR", details.getJSONArray("episodesMeta").getJSONObject(0).getString("title"));
        } finally {
            I18n.setLocale(I18n.DEFAULT_LANGUAGE_TAG);
        }
    }

    private String metadataBodyFor(String url) {
        if (url.contains("v2.sg.media-imdb.com/suggestion")) {
            return """
                    {"d":[
                      {"id":"tt1234567","l":"Example Show","q":"TV Series","y":"2024","s":"Actor One, Actor Two",
                       "i":{"imageUrl":"https://img/suggestion.jpg"}}
                    ]}
                    """;
        }
        if (url.contains("www.imdb.com/title/tt1234567")) {
            return """
                    <html><script type="application/ld+json">
                    {"name":"IMDb Name","image":"https://img/imdb.jpg","description":"IMDb Plot","datePublished":"2024-01-01",
                     "aggregateRating":{"ratingValue":"8.4"},"genre":["Drama","Mystery"],
                     "actor":[{"name":"Actor One"},{"name":"Actor Two"}],"director":[{"name":"Director One"}]}
                    </script></html>
                    """;
        }
        if (url.contains("v3-cinemeta.strem.io/meta/series/tt1234567.json")) {
            return """
                    {"meta":{"name":"Cinemeta Show","poster":"https://img/series.jpg","description":"Series Plot",
                     "imdb_id":"tt1234567","moviedb_id":321,"genres":["Drama"],"cast":["Actor One"],"director":["Director One"],
                     "releaseInfo":"2024","imdbRating":"8.2",
                     "videos":[{"title":"Episode 1 - Pilot","overview":"","thumbnail":"https://img/ep.jpg",
                       "released":"2024-02-01","season":1,"episode":1}]}}
                    """;
        }
        if (url.contains("v3-cinemeta.strem.io/meta/movie/tt1234567.json")) {
            return """
                    {"meta":{"name":"Cinemeta Movie","poster":"https://img/movie.jpg","description":"Movie Plot",
                     "imdb_id":"tt1234567","moviedb_id":654,"genre":"Drama","cast":"Actor One","director":"Director One",
                     "released":"2024-01-01T00:00:00.000Z","imdbRating":"8.1"}}
                    """;
        }
        if (url.contains("api.tvmaze.com/search/shows")) {
            return """
                    [{"show":{"id":55,"name":"Example Show","type":"Scripted","externals":{"imdb":"tt1234567"}}}]
                    """;
        }
        if (url.contains("api.tvmaze.com/shows/55/episodes")) {
            return """
                    [{"season":1,"number":1,"name":"Pilot","summary":"<p>TVMaze summary</p>","airdate":"2024-02-01"}]
                    """;
        }
        return "";
    }

    private String branchMetadataBodyFor(String url) {
        if (url.contains("blank") || url.contains("empty")) {
            return "";
        }
        if (url.contains("nojson")) {
            return "<html></html>";
        }
        if (url.contains("genrestring")) {
            return """
                    <script type="application/ld+json">
                    {"name":"String Genre Movie","genre":"Comedy","actor":null,"director":null}
                    </script>
                    """;
        }
        if (url.contains("metanull")) {
            return "{}";
        }
        if (url.contains("videosnull")) {
            return """
                    {"meta":{"name":"Video Null Show","poster":"","description":"","imdb_id":"","genre":"Drama",
                     "cast":"Cast","director":"Director","released":"","imdbRating":"","videos":[null]}}
                    """;
        }
        if (url.contains("releasedonly")) {
            return """
                    {"meta":{"name":"Released Movie","poster":"","description":"","imdb_id":"","genre":"Drama",
                     "cast":"Cast","director":"Director","released":"2024-03-04T00:00:00.000Z","imdbRating":""}}
                    """;
        }
        if (url.contains("Empty+Search")) {
            return "[]";
        }
        if (url.contains("Bad+Json")) {
            return "{bad";
        }
        if (url.contains("Best+Match")) {
            return """
                    [{"show":null},{"show":{"id":77,"name":"Best Match","type":"Scripted","externals":{"imdb":"other"}}}]
                    """;
        }
        if (url.contains("/movie/999")) {
            return """
                    {"name":"Nom FR","overview":"Resume FR","vote_average":7.0,"release_date":"2024-01-02",
                     "poster_path":"/fr.jpg","genres":[null,{"name":"Drame"}]}
                    """;
        }
        if (url.contains("/tv/999/season/1")) {
            return """
                    {"episodes":[{"episode_number":1,"name":"Episode FR","overview":"Resume episode","air_date":"2024-02-03","still_path":""}]}
                    """;
        }
        if (url.contains("/tv/999")) {
            return """
                    {"name":"Nom TV","overview":"Resume TV","vote_average":6.0,"first_air_date":"2023-01-02",
                     "poster_path":"","genres":[]}
                    """;
        }
        if (url.contains("shows/77/episodes")) {
            return "[]";
        }
        return "";
    }

    private String extractPosterCover() throws Exception {
        JSONObject result = new JSONObject();
        invoke("putTmdbPoster", new Class[]{JSONObject.class, String.class}, result, "/poster.png");
        return result.getString("cover");
    }

    private String invokeString(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        Object value = invoke(name, types, args);
        return value == null ? null : value.toString();
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ImdbMetadataService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
}
