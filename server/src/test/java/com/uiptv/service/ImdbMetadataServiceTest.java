package com.uiptv.service;

import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uiptv.util.json.JsonAccessKt.parseJsonArray;
import static com.uiptv.util.json.JsonAccessKt.parseJsonObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImdbMetadataServiceTest extends DbBackedTest {

    private final ImdbMetadataService service = ImdbMetadataService.INSTANCE;

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
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> localized = jsonMap(
                "name", "Localized Name",
                "plot", "Localized Plot",
                "genre", "Drama",
                "releaseDate", "2025-01-01",
                "cover", "https://img/cover.jpg",
                "rating", "7.5"
        );
        invoke("applyLocalizedTmdbFields", new Class[]{Map.class, Map.class}, result, localized);
        assertEquals("Localized Name", result.get("name"));
        assertEquals("Drama", result.get("genre"));

        JsonObject tmdbEpisode = parseObj("""
                {"name":"Episode 7","overview":"Episode plot","air_date":"2024-02-03","still_path":"/still.png"}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> mappedEpisode = (Map<String, Object>) invoke("mapTmdbEpisodeMeta", new Class[]{JsonObject.class}, tmdbEpisode);
        assertEquals("", mappedEpisode.get("title"));
        assertEquals("Episode plot", mappedEpisode.get("plot"));
        assertTrue(mappedEpisode.get("logo").toString().contains("/still.png"));

        JsonArray genres = parseArr("""
                [{"name":"Drama"},{"name":"Comedy"}]
                """);
        @SuppressWarnings("unchecked")
        List<String> genreNames = (List<String>) invoke("extractTmdbGenreNames", new Class[]{JsonArray.class}, genres);
        assertEquals(List.of("Drama", "Comedy"), genreNames);

        JsonArray values = parseArr("[\"A\", \"\", \"B\"]");
        assertEquals("A, B", invokeString("joinNonBlankArray", values));
        assertEquals("A, B", invoke("joinStringArray", new Class[]{JsonArray.class, int.class}, values, 3).toString());
    }

    @Test
    void tmdbAndTvMazeHelpers_coverIndexingAndMatchingLogic() throws Exception {
        JsonArray tvMazeEpisodes = parseArr("""
                [
                  {"season":1,"number":2,"name":"Pilot","summary":"<p>Summary</p>","airdate":"2024-01-10"},
                  {"season":2,"number":1,"name":"Return"}
                ]
                """);
        Object index = invoke("buildTvMazeEpisodeIndex", new Class[]{JsonArray.class}, tvMazeEpisodes);
        Map<String, Object> seasonEpisodeRow = jsonMap("season", "1", "episodeNum", "2", "title", "Other");
        Map<String, Object> titleRow = jsonMap("season", "", "episodeNum", "", "title", "Return");

        JsonObject matchedBySeasonEpisode = (JsonObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), Map.class}, index, seasonEpisodeRow);
        JsonObject matchedByTitle = (JsonObject) invoke("matchTvMazeEpisode", new Class[]{index.getClass(), Map.class}, index, titleRow);
        assertEquals("Pilot", matchedBySeasonEpisode.get("name").toString().replace("\"", ""));
        assertEquals("Return", matchedByTitle.get("name").toString().replace("\"", ""));

        invoke("applyTvMazeEpisodeMeta", new Class[]{Map.class, JsonObject.class}, seasonEpisodeRow, matchedBySeasonEpisode);
        assertEquals("Summary", seasonEpisodeRow.get("plot"));
        assertEquals("2024-01-10", seasonEpisodeRow.get("releaseDate"));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(seasonEpisodeRow);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> bySeasonEpisode = (Map<String, Map<String, Object>>) invoke("indexEpisodesBySeasonEpisode", new Class[]{List.class}, rows);
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

        Map<String, Object> result = new LinkedHashMap<>();
        invoke("applyImdbGenre", new Class[]{Map.class, Object.class}, result, parseArr("[\"Drama\", \"Comedy\"]"));
        assertEquals("Drama, Comedy", result.get("genre"));

        JsonArray people = parseArr("""
                [{"name":"A"},{"name":"B"}]
                """);
        assertEquals("A, B", invoke("joinPersonNames", new Class[]{JsonArray.class}, people).toString());

        Map<String, Object> target = jsonMap("name", "Existing");
        Map<String, Object> source = jsonMap("name", "Replacement", "plot", "Plot");
        invoke("mergeIfPresent", new Class[]{Map.class, Map.class, String.class}, target, source, "plot");
        invoke("mergeMissing", new Class[]{Map.class, Map.class, String.class}, target, source, "name");
        invoke("replaceIfPresent", new Class[]{Map.class, Map.class, String.class}, target, source, "name");
        assertEquals("Replacement", target.get("name"));
        assertEquals("Plot", target.get("plot"));

        List<Map<String, Object>> withPlot = new ArrayList<>();
        withPlot.add(jsonMap("plot", "filled"));
        assertTrue((Boolean) invoke("hasAnyEpisodePlot", new Class[]{List.class}, withPlot));
        List<Map<String, Object>> withoutPlot = new ArrayList<>();
        withoutPlot.add(jsonMap("plot", ""));
        assertFalse((Boolean) invoke("hasAnyEpisodePlot", new Class[]{List.class}, withoutPlot));
        assertTrue(invoke("extractJsonLd", new Class[]{String.class}, "<script type=\"application/ld+json\">{}</script>").toString().contains("{}"));

        Map<String, Object> localized = new LinkedHashMap<>();
        invoke("populateTmdbLocalizedDetails", new Class[]{Map.class, JsonObject.class}, localized,
                parseObj("""
                        {
                          "name":"Localized",
                          "overview":"Plot",
                          "vote_average":8.5,
                          "release_date":"2024-01-01",
                          "poster_path":"/poster.png",
                          "genres":[{"name":"Drama"}]
                        }
                        """));
        assertEquals("Localized", localized.get("name"));
        assertEquals("Drama", localized.get("genre"));
        assertTrue(localized.get("cover").toString().contains("poster.png"));
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
        ConfigurationService.INSTANCE.save(configuration);

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

            @SuppressWarnings("unchecked")
            Map<String, Object> localized = (Map<String, Object>) invoke("fetchTmdbLocalizedDetails",
                    new Class[]{String.class, String.class, String.class}, "123", "movie", "fr-FR");
            assertEquals("Nom Localise", localized.get("name"));
            assertEquals("Drama", localized.get("genre"));

            assertEquals("123", invoke("resolveTmdbMediaId",
                    new Class[]{Map.class, Map.class},
                    jsonMap("tmdbMediaId", "123"), new LinkedHashMap<>()));
            assertTrue((Boolean) invoke("isSuccessfulTmdbResponse",
                    new Class[]{com.uiptv.util.HttpUtil.HttpResult.class},
                    new com.uiptv.util.HttpUtil.HttpResult(200, "{}", Map.of(), Map.of())));

            List<Map<String, Object>> episodesMeta = new ArrayList<>();
            episodesMeta.add(jsonMap("season", "1", "episodeNum", "2", "title", "Episode 2"));
            invoke("enrichEpisodesMetaWithTmdb", new Class[]{List.class, String.class, String.class}, episodesMeta, "321", "fr-FR");
            Map<String, Object> merged = episodesMeta.get(0);
            assertEquals("Localized plot", merged.get("plot"));
            assertEquals("2024-02-02", merged.get("releaseDate"));
            assertTrue(merged.get("logo").toString().contains("still2.jpg"));
        }
    }

    private String extractPosterCover() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        invoke("putTmdbPoster", new Class[]{Map.class, String.class}, result, "/poster.png");
        return result.get("cover").toString();
    }

    private static JsonObject parseObj(String raw) {
        return parseJsonObject(raw);
    }

    private static JsonArray parseArr(String raw) {
        return parseJsonArray(raw);
    }

    private static Map<String, Object> jsonMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
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
