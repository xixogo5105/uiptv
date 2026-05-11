package com.uiptv.service;

import com.uiptv.util.json.KJsonArray;
import com.uiptv.util.json.KJsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImdbMetadataServiceTest extends DbBackedTest {

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
        KJsonObject result = new KJsonObject();
        KJsonObject localized = new KJsonObject()
                .put("name", "Localized Name")
                .put("plot", "Localized Plot")
                .put("genre", "Drama")
                .put("releaseDate", "2025-01-01")
                .put("cover", "https://img/cover.jpg")
                .put("rating", "7.5");
        invoke("applyLocalizedTmdbFields", new Class[]{KJsonObject.class, KJsonObject.class}, result, localized);
        assertEquals("Localized Name", result.getString("name"));
        assertEquals("Drama", result.getString("genre"));

        KJsonObject tmdbEpisode = new KJsonObject()
                .put("name", "Episode 7")
                .put("overview", "Episode plot")
                .put("air_date", "2024-02-03")
                .put("still_path", "/still.png");
        KJsonObject mappedEpisode = (KJsonObject) invoke("mapTmdbEpisodeMeta", new Class[]{KJsonObject.class}, tmdbEpisode);
        assertEquals("", mappedEpisode.getString("title"));
        assertEquals("Episode plot", mappedEpisode.getString("plot"));
        assertTrue(mappedEpisode.getString("logo").contains("/still.png"));

        KJsonArray genres = new KJsonArray()
                .put(new KJsonObject().put("name", "Drama"))
                .put(new KJsonObject().put("name", "Comedy"));
        @SuppressWarnings("unchecked")
        List<String> genreNames = (List<String>) invoke("extractTmdbGenreNames", new Class[]{KJsonArray.class}, genres);
        assertEquals(List.of("Drama", "Comedy"), genreNames);

        KJsonArray values = new KJsonArray().put("A").put("").put("B");
        assertEquals("A, B", invokeString("joinNonBlankArray", values));
        assertEquals("A, B", invoke("joinStringArray", new Class[]{KJsonArray.class, int.class}, values, 3).toString());
    }

    @Test
    void tmdbAndTvMazeHelpers_coverIndexingAndMatchingLogic() throws Exception {
        KJsonArray tvMazeEpisodes = new KJsonArray()
                .put(new KJsonObject().put("season", 1).put("number", 2).put("name", "Pilot").put("summary", "<p>Summary</p>").put("airdate", "2024-01-10"))
                .put(new KJsonObject().put("season", 2).put("number", 1).put("name", "Return"));
        Object index = invoke("buildTvMazeEpisodeIndex", new Class[]{KJsonArray.class}, tvMazeEpisodes);
        KJsonObject seasonEpisodeRow = new KJsonObject().put("season", "1").put("episodeNum", "2").put("title", "Other");
        KJsonObject titleRow = new KJsonObject().put("season", "").put("episodeNum", "").put("title", "Return");

        KJsonObject matchedBySeasonEpisode = (KJsonObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), KJsonObject.class}, index, seasonEpisodeRow);
        KJsonObject matchedByTitle = (KJsonObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), KJsonObject.class}, index, titleRow);
        assertEquals("Pilot", matchedBySeasonEpisode.getString("name"));
        assertEquals("Return", matchedByTitle.getString("name"));

        invoke("applyTvMazeEpisodeMeta", new Class[]{KJsonObject.class, KJsonObject.class}, seasonEpisodeRow, matchedBySeasonEpisode);
        assertEquals("Summary", seasonEpisodeRow.getString("plot"));
        assertEquals("2024-01-10", seasonEpisodeRow.getString("releaseDate"));

        @SuppressWarnings("unchecked")
        Map<String, KJsonObject> bySeasonEpisode = (Map<String, KJsonObject>) invoke("indexEpisodesBySeasonEpisode", new Class[]{KJsonArray.class}, new KJsonArray().put(seasonEpisodeRow));
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

        KJsonObject result = new KJsonObject();
        invoke("applyImdbGenre", new Class[]{KJsonObject.class, Object.class}, result, new KJsonArray().put("Drama").put("Comedy"));
        assertEquals("Drama, Comedy", result.getString("genre"));

        KJsonArray people = new KJsonArray().put(new KJsonObject().put("name", "A")).put(new KJsonObject().put("name", "B"));
        assertEquals("A, B", invoke("joinPersonNames", new Class[]{KJsonArray.class}, people).toString());

        KJsonObject target = new KJsonObject().put("name", "Existing");
        KJsonObject source = new KJsonObject().put("name", "Replacement").put("plot", "Plot");
        invoke("mergeIfPresent", new Class[]{KJsonObject.class, KJsonObject.class, String.class}, target, source, "plot");
        invoke("mergeMissing", new Class[]{KJsonObject.class, KJsonObject.class, String.class}, target, source, "name");
        invoke("replaceIfPresent", new Class[]{KJsonObject.class, KJsonObject.class, String.class}, target, source, "name");
        assertEquals("Replacement", target.getString("name"));
        assertEquals("Plot", target.getString("plot"));

        assertTrue((Boolean) invoke("hasAnyEpisodePlot", new Class[]{KJsonArray.class}, new KJsonArray().put(new KJsonObject().put("plot", "filled"))));
        assertFalse((Boolean) invoke("hasAnyEpisodePlot", new Class[]{KJsonArray.class}, new KJsonArray().put(new KJsonObject().put("plot", ""))));
        assertTrue(invoke("extractJsonLd", new Class[]{String.class}, "<script type=\"application/ld+json\">{}</script>").toString().contains("{}"));

        KJsonObject localized = new KJsonObject();
        invoke("populateTmdbLocalizedDetails", new Class[]{KJsonObject.class, KJsonObject.class}, localized,
                new KJsonObject()
                        .put("name", "Localized")
                        .put("overview", "Plot")
                        .put("vote_average", 8.5)
                        .put("release_date", "2024-01-01")
                        .put("poster_path", "/poster.png")
                        .put("genres", new KJsonArray().put(new KJsonObject().put("name", "Drama"))));
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
        com.uiptv.model.Configuration configuration = new com.uiptv.model.Configuration();
        configuration.setTmdbReadAccessToken(" bearer-token ");
        ConfigurationService.getInstance().save(configuration);

        try (org.mockito.MockedStatic<com.uiptv.util.HttpUtil> httpUtilStatic = Mockito.mockStatic(com.uiptv.util.HttpUtil.class)) {
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

            KJsonObject localized = (KJsonObject) invoke("fetchTmdbLocalizedDetails",
                    new Class[]{String.class, String.class, String.class}, "123", "movie", "fr-FR");
            assertEquals("Nom Localise", localized.getString("name"));
            assertEquals("Drama", localized.getString("genre"));

            assertEquals("123", invoke("resolveTmdbMediaId",
                    new Class[]{KJsonObject.class, KJsonObject.class},
                    new KJsonObject().put("tmdbMediaId", "123"), new KJsonObject()));
            assertTrue((Boolean) invoke("isSuccessfulTmdbResponse",
                    new Class[]{com.uiptv.util.HttpUtil.HttpResult.class},
                    new com.uiptv.util.HttpUtil.HttpResult(200, "{}", Map.of(), Map.of())));

            KJsonArray episodesMeta = new KJsonArray()
                    .put(new KJsonObject().put("season", "1").put("episodeNum", "2").put("title", "Episode 2"));
            invoke("enrichEpisodesMetaWithTmdb", new Class[]{KJsonArray.class, String.class, String.class}, episodesMeta, "321", "fr-FR");
            KJsonObject merged = episodesMeta.getJSONObject(0);
            assertEquals("Localized plot", merged.getString("plot"));
            assertEquals("2024-02-02", merged.getString("releaseDate"));
            assertTrue(merged.getString("logo").contains("still2.jpg"));
        }
    }

    private String extractPosterCover() throws Exception {
        KJsonObject result = new KJsonObject();
        invoke("putTmdbPoster", new Class[]{KJsonObject.class, String.class}, result, "/poster.png");
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
