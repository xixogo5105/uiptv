package com.uiptv.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
