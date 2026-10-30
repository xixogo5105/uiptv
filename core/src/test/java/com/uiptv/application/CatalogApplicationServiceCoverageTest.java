package com.uiptv.application;

import com.uiptv.model.Channel;
import com.uiptv.service.DbBackedTest;
import com.uiptv.model.Category;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.Pagination;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogApplicationServiceCoverageTest extends DbBackedTest {
    private final CatalogApplicationService service = CatalogApplicationService.getInstance();

    @Test
    void publicCatalogEntryPointsRejectMissingQueriesAndAccounts() throws Exception {
        assertTrue(service.listCategories(null, CatalogMode.ITV).isEmpty());
        assertTrue(service.listChannels(null).isEmpty());
        assertTrue(service.listSeriesEpisodes(null).isEmpty());
        assertTrue(service.listWebChannels(null).items().isEmpty());
        assertEquals("VOD", service.getVodDetails(null).name());
        assertTrue(service.getSeriesDetails(null).seasonInfo().isEmpty());
        try (var accounts = mockStatic(com.uiptv.service.AccountService.class)) {
            var accountService = mock(com.uiptv.service.AccountService.class);
            accounts.when(com.uiptv.service.AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("missing")).thenReturn(null);
            assertTrue(service.listCategories("missing", CatalogMode.ITV).isEmpty());
            assertTrue(service.listChannels(new CatalogChannelsQuery("missing", CatalogMode.ITV, "", "")).isEmpty());
            assertTrue(service.listSeriesEpisodes(new CatalogSeriesEpisodesQuery("missing", "", "series")).isEmpty());
            assertTrue(service.listWebChannels(new CatalogWebChannelsQuery("missing", CatalogMode.ITV, "", "", 0, 10, 0, 0)).items().isEmpty());
            assertTrue(service.getSeriesDetails(new CatalogSeriesDetailsQuery("missing", "", "", "")).seasonInfo().isEmpty());
        }
    }

    @Test
    void privateCollectionAndPaginationHelpersCoverEmptyDuplicateAndBoundaryCases() throws Exception {
        CategoryFixture categories = new CategoryFixture();
        assertEquals(List.of(categories.news()), invoke("resolveRequestedCategories", List.class, List.of(categories.all(), categories.news())));
        assertEquals(List.of(categories.all()), invoke("resolveRequestedCategories", List.class, List.of(categories.all())));
        assertTrue(((List<?>) invoke("resolveRequestedCategories", List.class, List.of())).isEmpty());

        Channel first = channel("id", "Name", "cmd");
        Channel duplicate = channel("id", "Name", "cmd");
        Channel different = channel("id", "Other", "cmd");
        List<Channel> deduped = invoke("dedupeChannels", List.class, Arrays.asList(first, null, duplicate, different));
        assertEquals(2, deduped.size());
        assertTrue(((List<?>) invoke("dedupeChannels", List.class, (Object) null)).isEmpty());

        CatalogPagedChannelsResult firstPage = invoke("sliceChannels", CatalogPagedChannelsResult.class,
                List.of(first, different), 0, 1, 1);
        assertEquals(List.of(first), firstPage.items());
        assertTrue(firstPage.hasMore());
        CatalogPagedChannelsResult emptyPage = invoke("sliceChannels", CatalogPagedChannelsResult.class,
                List.of(first), 2, 1, 0);
        assertTrue(emptyPage.items().isEmpty());

        assertTrue((Boolean) invoke("estimateHasMore", boolean.class, new Pagination(10, 4), 1, 0, 4, 4));
        assertFalse((Boolean) invoke("estimateHasMore", boolean.class, new Pagination(8, 4), 2, 0, 4, 4));
        assertTrue((Boolean) invoke("estimateHasMore", boolean.class, null, 0, 0, 4, 4));
        assertFalse((Boolean) invoke("estimateHasMore", boolean.class, null, 0, 0, 3, 4));
    }

    @Test
    void metadataHelpersMatchEpisodesByNumberOrTitleAndFillOnlyMissingFields() throws Exception {
        JSONArray metadata = new JSONArray()
                .put(new JSONObject().put("season", "01").put("episodeNum", "002").put("title", "Pilot")
                        .put("plot", "Plot").put("releaseDate", "2024").put("rating", "8.5").put("logo", "https://img/logo"))
                .put(new JSONObject().put("title", "Second Episode").put("rating", "7.1"))
                .put(JSONObject.NULL);
        @SuppressWarnings("unchecked")
        Map<String, JSONObject> indexed = invoke("indexEpisodesMeta", Map.class, metadata);
        assertTrue(indexed.containsKey("01:002"));
        assertTrue(indexed.containsKey("title:pilot"));
        assertTrue(indexed.containsKey("title:second episode"));

        Channel episode = channel("ep", "Pilot", "cmd");
        episode.setSeason("01");
        episode.setEpisodeNum("002");
        invoke("enrichEpisode", void.class, episode, indexed);
        assertEquals("Plot", episode.getDescription());
        assertEquals("2024", episode.getReleaseDate());
        assertEquals("8.5", episode.getRating());
        assertEquals("https://img/logo", episode.getLogo());

        Channel titleMatch = channel("ep-2", "Second Episode", "cmd");
        invoke("enrichEpisode", void.class, titleMatch, indexed);
        assertEquals("7.1", titleMatch.getRating());
        invoke("enrichEpisode", void.class, channel("none", "None", "cmd"), indexed);
        invoke("enrichEpisode", void.class, null, indexed);
        invoke("enrichEpisode", void.class, episode, Map.of());

        JSONObject target = new JSONObject().put("name", "Existing");
        JSONObject source = new JSONObject().put("name", "Incoming").put("plot", "Plot").put("rating", "9");
        invoke("mergeMetadata", void.class, target, source);
        assertEquals("Existing", target.getString("name"));
        assertEquals("Plot", target.getString("plot"));
        assertEquals("9", target.getString("rating"));
        JSONObject copied = new JSONObject();
        invoke("copyMetadata", void.class, copied, source);
        assertEquals("Incoming", copied.getString("name"));
    }

    @Test
    void episodeConversionAndNameHelpersHandleMissingData() throws Exception {
        assertTrue(((List<?>) invoke("toChannels", List.class, new JSONArray())).isEmpty());
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("https://img/episode");
        info.setPlot("Plot");
        info.setReleaseDate("2020");
        info.setRating("8");
        info.setDuration("45");
        info.setSeason("3");
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setTitle("Episode");
        episode.setCmd("cmd");
        episode.setInfo(info);
        List<Channel> channels = invoke("toChannels", List.class,
                new com.uiptv.shared.EpisodeList() {{ getEpisodes().add(null); getEpisodes().add(episode); }});
        assertEquals(1, channels.size());
        assertEquals("3", channels.getFirst().getSeason());
        assertEquals("Plot", channels.getFirst().getDescription());

        JSONArray jsonEpisodes = new JSONArray().put(new JSONObject(channel("ep", "Episode", "cmd").toJson())).put(JSONObject.NULL).put("not-json");
        assertEquals(1, ((List<?>) invoke("toChannels", List.class, jsonEpisodes)).size());

        JSONObject season = new JSONObject();
        invoke("applyNameYearFallback", void.class, season, "Show Name (2024)");
        assertEquals("Show Name", season.getString("name"));
        assertEquals("2024", season.getString("releaseDate"));
        invoke("applyNameYearFallback", void.class, season, "");
        assertEquals("", invoke("normalize", String.class, "  ")); 
        assertEquals("show name", invoke("normalize", String.class, "Show (Name)"));
        assertEquals("123", invoke("safeNumeric", String.class, "S1E23"));
        assertEquals("", invoke("safeNumeric", String.class, "episode"));
        assertEquals("", invoke("safe", String.class, (Object) null));
        assertEquals("second", invoke("firstNonBlank", String.class, (Object) new String[]{"", "  ", "second"}));
        assertEquals("", invoke("firstNonBlank", String.class, (Object) new String[]{null}));
    }

    @Test
    void fuzzyHintsStripPresentationNoiseAndAvoidDuplicates() throws Exception {
        List<String> hints = new ArrayList<>();
        invoke("addHint", void.class, hints, "Movie Title HD (2024)");
        invoke("addHint", void.class, hints, "Movie Title HD (2024)");
        invoke("addHint", void.class, hints, "x");
        invoke("addHint", void.class, hints, "");
        assertEquals(List.of("Title 2024"), hints);

        JSONArray episodes = new JSONArray().put(new JSONObject().put("name", "Episode S01E02").put("releaseDate", "2024"));
        @SuppressWarnings("unchecked")
        List<String> seriesHints = invoke("buildFuzzyHints", List.class, "Show HD", new JSONObject().put("plot", "A plot"), episodes);
        assertTrue(seriesHints.contains("Show"));
        assertTrue(seriesHints.contains("Episode"));
        assertTrue(seriesHints.contains("2024"));
    }

    private static Channel channel(String id, String name, String cmd) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd(cmd);
        return channel;
    }

    private <T> T invoke(String name, Class<T> type, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        for (Method method : CatalogApplicationService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null) {
                        compatible &= !parameterTypes[i].isPrimitive();
                    } else {
                        compatible &= wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass());
                    }
                }
                if (!compatible) {
                    continue;
                }
                method.setAccessible(true);
                @SuppressWarnings("unchecked") T value = (T) method.invoke(service, args);
                if (type != void.class && value != null && !wrap(type).isInstance(value)) {
                    throw new ClassCastException("Unexpected return type for " + name);
                }
                return value;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    private record CategoryFixture(Category all, Category news) {
        private CategoryFixture() {
            this(new Category("all", "All", "all", false, 0), new Category("news", "News", "news", false, 0));
        }
    }
}
