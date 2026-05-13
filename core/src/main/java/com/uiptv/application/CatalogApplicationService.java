package com.uiptv.application;

import com.uiptv.db.ChannelDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryResolver;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.SeasonInfo;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;
import com.uiptv.util.XtremeApiParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class CatalogApplicationService {
    private static final String ALL_CATEGORY = CategoryType.ALL.displayName();
    private static final String DEFAULT_VOD_NAME = "VOD";
    private static final String KEY_COVER = "cover";
    private static final String KEY_DIRECTOR = "director";
    private static final String KEY_EPISODES = "episodes";
    private static final String KEY_EPISODES_META = "episodesMeta";
    private static final String KEY_GENRE = "genre";
    private static final String KEY_IMDB_URL = "imdbUrl";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";

    private CatalogApplicationService() {
    }

    public static CatalogApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public List<Category> listCategories(String accountId, CatalogMode mode) {
        Account account = resolveAccount(accountId, mode);
        if (account == null) {
            return List.of();
        }
        List<Category> categories = CategoryService.getInstance().get(account);
        return new CategoryResolver().resolveCategories(account, categories);
    }

    public List<Channel> listChannels(CatalogChannelsQuery query) throws IOException {
        Account account = query == null ? null : resolveAccount(query.accountId(), query.mode());
        if (account == null) {
            return List.of();
        }
        String categoryId = safe(query.categoryId());
        String movieId = safe(query.movieId());

        List<Channel> channels;
        if (shouldServeSeriesEpisodes(account, categoryId, movieId)) {
            channels = getStalkerSeriesEpisodes(account, categoryId, movieId);
        } else if (ALL_CATEGORY.equalsIgnoreCase(categoryId)) {
            channels = readAllCategoryChannels(account);
        } else {
            channels = readSingleCategoryChannels(account, categoryId);
        }

        if (account.getAction() == Account.AccountAction.series && isBlank(movieId)) {
            String categoryApiId = ALL_CATEGORY.equalsIgnoreCase(categoryId) ? "" : resolveCategoryApiId(account, categoryId);
            applySeriesRowsWatched(account, categoryApiId, channels);
        }
        return dedupeChannels(channels);
    }

    public List<Channel> listSeriesEpisodes(CatalogSeriesEpisodesQuery query) {
        if (query == null || isBlank(query.seriesId())) {
            return List.of();
        }
        Account account = resolveAccount(query.accountId(), CatalogMode.SERIES);
        if (account == null) {
            return List.of();
        }

        String categoryId = resolveSeriesCategoryId(query.categoryId());
        List<Channel> cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, query.seriesId());
        if (cachedEpisodes.isEmpty() && account.getType() == AccountType.XTREME_API) {
            cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, query.seriesId());
        }

        if (isCachedFresh(account, categoryId, query.seriesId(), cachedEpisodes)) {
            applyWatchedFlag(cachedEpisodes, account, categoryId, query.seriesId());
            return cachedEpisodes;
        }

        List<Channel> episodes = loadProviderEpisodes(account, query.seriesId());
        if (!episodes.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, query.seriesId(), episodes);
        } else if (account.getType() == AccountType.XTREME_API) {
            episodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, query.seriesId());
        }
        applyWatchedFlag(episodes, account, categoryId, query.seriesId());
        return episodes;
    }

    public CatalogPagedChannelsResult listWebChannels(CatalogWebChannelsQuery query) throws IOException {
        Account account = query == null ? null : resolveAccount(query.accountId(), query.mode());
        if (account == null) {
            return new CatalogPagedChannelsResult(List.of(), 0, false, 0);
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        String categoryId = safe(query.categoryId());
        String movieId = safe(query.movieId());
        if (account.getAction() == Account.AccountAction.series && isNotBlank(movieId)) {
            List<Channel> episodes = listSeriesEpisodes(new CatalogSeriesEpisodesQuery(
                    account.getDbId(),
                    categoryId,
                    movieId
            ));
            return sliceChannels(episodes, query.page(), query.pageSize(), query.prefetchPages());
        }
        if (account.getType() == AccountType.STALKER_PORTAL && !ALL_CATEGORY.equalsIgnoreCase(categoryId)) {
            return buildStalkerPagedResponse(account, categoryId, movieId, query.page(), query.pageSize(), query.prefetchPages(), query.apiOffset());
        }

        List<Channel> all = listChannels(new CatalogChannelsQuery(account.getDbId(), query.mode(), categoryId, movieId));
        return sliceChannels(all, query.page(), query.pageSize(), query.prefetchPages());
    }

    public CatalogVodDetailsResult getVodDetails(CatalogVodDetailsQuery query) {
        String requestedName = query == null ? "" : query.vodName();
        String fallbackName = isBlank(requestedName) ? DEFAULT_VOD_NAME : requestedName;
        Account account = query == null ? null : resolveAccount(query.accountId(), CatalogMode.VOD);

        if (account != null && account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        Channel providerChannel = resolveProviderVodChannel(account, query == null ? "" : query.categoryId(), query == null ? "" : query.channelId());

        String name = fallbackName;
        String cover = "";
        String plot = "";
        String cast = "";
        String director = "";
        String genre = "";
        String releaseDate = "";
        String rating = "";
        String tmdb = "";
        String imdbUrl = "";
        String duration = "";

        if (providerChannel != null) {
            name = firstNonBlank(name, providerChannel.getName(), fallbackName);
            cover = firstNonBlank(cover, providerChannel.getLogo());
            plot = firstNonBlank(plot, providerChannel.getDescription());
            releaseDate = firstNonBlank(releaseDate, providerChannel.getReleaseDate());
            rating = firstNonBlank(rating, providerChannel.getRating());
            duration = firstNonBlank(duration, providerChannel.getDuration());
        }

        String queryTitle = isBlank(requestedName) ? name : requestedName;
        JSONObject imdb = ImdbMetadataService.getInstance().findBestEffortMovieDetails(
                queryTitle,
                tmdb,
                buildFuzzyHints(queryTitle, providerChannel, name, plot, releaseDate)
        );

        name = firstNonBlank(name, imdb.optString("name", ""), fallbackName);
        cover = firstNonBlank(cover, imdb.optString("cover", ""));
        plot = firstNonBlank(plot, imdb.optString("plot", ""));
        cast = firstNonBlank(cast, imdb.optString("cast", ""));
        director = firstNonBlank(director, imdb.optString("director", ""));
        genre = firstNonBlank(genre, imdb.optString("genre", ""));
        releaseDate = firstNonBlank(releaseDate, imdb.optString("releaseDate", ""));
        rating = firstNonBlank(rating, imdb.optString("rating", ""));
        tmdb = firstNonBlank(tmdb, imdb.optString("tmdb", ""));
        imdbUrl = firstNonBlank(imdbUrl, imdb.optString("imdbUrl", ""));

        return new CatalogVodDetailsResult(name, cover, plot, cast, director, genre, releaseDate, rating, tmdb, imdbUrl, duration);
    }

    public CatalogSeriesDetailsResult getSeriesDetails(CatalogSeriesDetailsQuery query) {
        Account account = query == null ? null : resolveAccount(query.accountId(), CatalogMode.SERIES);
        if (account == null) {
            return new CatalogSeriesDetailsResult(new JSONObject(), new JSONArray(), new JSONArray());
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        String seriesId = safe(query.seriesId());
        String categoryId = safe(query.categoryId());
        String seriesName = safe(query.seriesName());
        JSONArray episodes = new JSONArray();
        JSONArray episodesMeta = new JSONArray();

        if (isNotBlank(seriesId)) {
            List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
            if (!cached.isEmpty() && SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
                episodes = toJsonArray(cached);
            }
        }

        JSONObject seasonInfo = new JSONObject();
        JSONObject imdbFirst = applyInitialImdbMetadata(seriesName, seasonInfo, episodes);
        if (imdbFirst.optJSONArray(KEY_EPISODES_META) != null) {
            episodesMeta = imdbFirst.optJSONArray(KEY_EPISODES_META);
        }

        SeriesProviderDetails providerDetails = loadProviderSeriesDetails(account, categoryId, seriesId, episodesMeta);
        mergeProviderSeasonInfo(seasonInfo, providerDetails.seasonInfo());
        if (providerDetails.episodes().length() > 0) {
            episodes = providerDetails.episodes();
        }

        JSONObject imdbFallback = applyFallbackImdbMetadata(seriesName, seasonInfo, episodes);
        mergeMetadata(seasonInfo, imdbFallback);
        if ((episodesMeta == null || episodesMeta.isEmpty()) && imdbFallback.optJSONArray(KEY_EPISODES_META) != null) {
            episodesMeta = imdbFallback.optJSONArray(KEY_EPISODES_META);
        }

        episodes = enrichEpisodes(episodes, episodesMeta);
        applyNameYearFallback(seasonInfo, seriesName);
        return new CatalogSeriesDetailsResult(seasonInfo, episodes, episodesMeta == null ? new JSONArray() : episodesMeta);
    }

    private Account resolveAccount(String accountId, CatalogMode mode) {
        Account account = AccountService.getInstance().getById(accountId);
        if (account != null) {
            account.setAction((mode == null ? CatalogMode.ITV : mode).toAccountAction());
        }
        return account;
    }

    private boolean shouldServeSeriesEpisodes(Account account, String categoryId, String movieId) {
        return account.getAction() == Account.AccountAction.series
                && account.getType() == AccountType.STALKER_PORTAL
                && isNotBlank(movieId)
                && !ALL_CATEGORY.equalsIgnoreCase(categoryId);
    }

    private List<Channel> getStalkerSeriesEpisodes(Account account, String categoryId, String movieId) {
        String categoryApiId = resolveCategoryApiId(account, categoryId);
        if (SeriesEpisodeDb.get().isFresh(account, categoryApiId, movieId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(account, categoryApiId, movieId);
            if (!cached.isEmpty()) {
                applyWatchedFlag(cached, account, categoryApiId, movieId);
                return cached;
            }
        }
        List<Channel> episodes = ChannelService.getInstance().getSeries(categoryApiId, movieId, account, null, null);
        if (!episodes.isEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryApiId, movieId, episodes);
        }
        applyWatchedFlag(episodes, account, categoryApiId, movieId);
        return episodes;
    }

    private List<Channel> readSingleCategoryChannels(Account account, String categoryId) throws IOException {
        Category category = resolveCategoryByDbId(account, categoryId);
        if (category == null) {
            return List.of();
        }
        return readChannelsForCategory(account, category);
    }

    private List<Channel> readAllCategoryChannels(Account account) throws IOException {
        List<Channel> merged = new ArrayList<>();
        for (Category category : resolveRequestedCategories(resolveCategoriesForAccount(account))) {
            merged.addAll(readChannelsForCategory(account, category));
        }
        return merged;
    }

    private List<Channel> readChannelsForCategory(Account account, Category category) throws IOException {
        if (category == null || account == null) {
            return List.of();
        }
        String categoryIdToUse;
        if (account.getType() == AccountType.STALKER_PORTAL || account.getType() == AccountType.XTREME_API) {
            categoryIdToUse = category.getCategoryId();
        } else {
            categoryIdToUse = category.getTitle();
        }
        List<Channel> channels = ChannelService.getInstance().get(categoryIdToUse, account, category.getDbId());
        return channels == null ? List.of() : channels;
    }

    private List<Category> resolveRequestedCategories(List<Category> categories) {
        List<Category> nonAllCategories = categories.stream()
                .filter(cat -> !ALL_CATEGORY.equalsIgnoreCase(cat.getTitle()))
                .toList();
        if (!nonAllCategories.isEmpty()) {
            return nonAllCategories;
        }
        return categories.stream()
                .filter(cat -> ALL_CATEGORY.equalsIgnoreCase(cat.getTitle()))
                .findFirst()
                .map(List::of)
                .orElse(List.of());
    }

    private boolean isCachedFresh(Account account, String categoryId, String seriesId, List<Channel> cachedEpisodes) {
        return !cachedEpisodes.isEmpty() && (
                SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())
                        || (account.getType() == AccountType.XTREME_API
                        && SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, ConfigurationService.getInstance().getCacheExpiryMs()))
        );
    }

    private List<Channel> loadProviderEpisodes(Account account, String seriesId) {
        if (account.getType() == AccountType.XTREME_API && isNotBlank(seriesId)) {
            return toChannels(XtremeApiParser.parseEpisodes(seriesId, account));
        }
        return new ArrayList<>();
    }

    private List<Channel> toChannels(EpisodeList episodes) {
        List<Channel> channels = new ArrayList<>();
        if (episodes == null || episodes.getEpisodes() == null) {
            return channels;
        }
        for (Episode episode : episodes.getEpisodes()) {
            Channel channel = toChannel(episode);
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private Channel toChannel(Episode episode) {
        if (episode == null) {
            return null;
        }
        Channel channel = new Channel();
        channel.setChannelId(episode.getId());
        channel.setName(episode.getTitle());
        channel.setCmd(episode.getCmd());
        channel.setExtraJson(episode.toJson());
        channel.setSeason(episode.getSeason());
        channel.setEpisodeNum(episode.getEpisodeNum());
        EpisodeInfo info = episode.getInfo();
        if (info != null) {
            channel.setLogo(info.getMovieImage());
            channel.setDescription(info.getPlot());
            channel.setReleaseDate(info.getReleaseDate());
            channel.setRating(info.getRating());
            channel.setDuration(info.getDuration());
            if (StringUtils.isBlank(channel.getSeason())) {
                channel.setSeason(info.getSeason());
            }
        }
        return channel;
    }

    private void applyWatchedFlag(List<Channel> episodes, Account account, String categoryId, String seriesId) {
        if (episodes == null || episodes.isEmpty() || account == null) {
            return;
        }
        SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), categoryId, seriesId);
        for (Channel channel : episodes) {
            if (channel == null) {
                continue;
            }
            channel.setWatched(SeriesWatchStateService.getInstance().isMatchingEpisode(
                    state,
                    channel.getChannelId(),
                    channel.getSeason(),
                    channel.getEpisodeNum(),
                    channel.getName()
            ));
        }
    }

    private String resolveSeriesCategoryId(String rawCategoryId) {
        if (StringUtils.isBlank(rawCategoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(rawCategoryId);
        if (category != null && StringUtils.isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return rawCategoryId;
    }

    private Channel resolveProviderVodChannel(Account account, String categoryId, String channelId) {
        if (account == null || isBlank(channelId)) {
            return null;
        }
        Channel providerChannel = VodChannelDb.get().getChannelByChannelId(channelId, categoryId, account.getDbId());
        if (providerChannel == null) {
            providerChannel = ChannelDb.get().getChannelById(channelId, categoryId);
        }
        return providerChannel;
    }

    private List<String> buildFuzzyHints(String queryTitle, Channel providerChannel, String name, String plot, String releaseDate) {
        List<String> hints = new ArrayList<>();
        addHint(hints, queryTitle);
        if (providerChannel != null) {
            addHint(hints, providerChannel.getName());
            addHint(hints, providerChannel.getDescription());
            addHint(hints, providerChannel.getReleaseDate());
        }
        addHint(hints, name);
        addHint(hints, plot);
        addHint(hints, releaseDate);
        return hints;
    }

    private void addHint(List<String> hints, String value) {
        if (hints == null || isBlank(value)) {
            return;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b", " ")
                .replaceAll("[\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (isBlank(cleaned) || cleaned.length() < 2 || hints.contains(cleaned)) {
            return;
        }
        hints.add(cleaned);
    }

    private CatalogPagedChannelsResult buildStalkerPagedResponse(Account account, String categoryId, String movieId,
                                                                 int page, int pageSize, int prefetchPages, int requestedApiOffset) {
        String categoryApiId = resolveCategoryApiId(account, categoryId);
        int resolvedApiOffset = requestedApiOffset;

        List<Channel> merged = new ArrayList<>();
        int currentPage = page;
        boolean hasMore = false;

        for (int i = 0; i < prefetchPages; i++) {
            int apiPage = currentPage + resolvedApiOffset;
            StalkerPageResult result = fetchStalkerPage(account, categoryApiId, movieId, apiPage, pageSize);

            if (currentPage == 0 && i == 0 && resolvedApiOffset == 0 && result.items().isEmpty()) {
                StalkerPageResult pageOne = fetchStalkerPage(account, categoryApiId, movieId, 1, pageSize);
                if (!pageOne.items().isEmpty()) {
                    resolvedApiOffset = 1;
                    result = pageOne;
                    apiPage = 1;
                }
            }

            if (result.items().isEmpty()) {
                hasMore = false;
                break;
            }

            merged.addAll(result.items());
            hasMore = estimateHasMore(result.pagination(), apiPage, resolvedApiOffset, result.items().size(), pageSize);
            currentPage++;

            if (!hasMore) {
                break;
            }
        }

        if (account.getAction() == Account.AccountAction.series) {
            if (isNotBlank(movieId)) {
                applyWatchedFlag(merged, account, categoryApiId, movieId);
            } else {
                applySeriesRowsWatched(account, categoryApiId, merged);
            }
        }

        return new CatalogPagedChannelsResult(dedupeChannels(merged), currentPage, hasMore, resolvedApiOffset);
    }

    private CatalogPagedChannelsResult sliceChannels(List<Channel> channels, int page, int pageSize, int prefetchPages) {
        List<Channel> all = channels == null ? List.of() : channels;
        int start = page * pageSize;
        if (start >= all.size()) {
            return new CatalogPagedChannelsResult(List.of(), page + Math.max(prefetchPages, 1), false, 0);
        }
        int end = Math.min(all.size(), start + (pageSize * prefetchPages));
        return new CatalogPagedChannelsResult(new ArrayList<>(all.subList(start, end)), page + Math.max(prefetchPages, 1), end < all.size(), 0);
    }

    private StalkerPageResult fetchStalkerPage(Account account, String categoryApiId, String movieId, int pageNumber, int pageSize) {
        Map<String, String> params = ChannelService.getChannelOrSeriesParams(categoryApiId, pageNumber, account.getAction(), movieId, "0");
        params.put("per_page", String.valueOf(pageSize));

        String json = FetchAPI.fetch(params, account);
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        List<Channel> parsed = account.getAction() == itv
                ? ChannelService.getInstance().parseItvChannels(json, true)
                : ChannelService.getInstance().parseVodChannels(account, json, true);
        return new StalkerPageResult(parsed == null ? List.of() : parsed, pagination);
    }

    private boolean estimateHasMore(Pagination pagination, int apiPage, int apiOffset, int currentSize, int pageSize) {
        if (pagination != null && pagination.getMaxPageItems() > 0 && pagination.getPaginationLimit() > 0) {
            int servedPages = Math.max(1, apiPage - apiOffset + 1);
            int servedItemsEstimate = servedPages * pagination.getPaginationLimit();
            return servedItemsEstimate < pagination.getMaxPageItems();
        }
        return currentSize >= pageSize;
    }

    private void applySeriesRowsWatched(Account account, String fallbackCategoryId, List<Channel> rows) {
        if (rows == null || rows.isEmpty() || account == null) {
            return;
        }
        for (Channel row : rows) {
            if (row == null) continue;
            String rowCategoryId = StringUtils.isBlank(row.getCategoryId()) ? fallbackCategoryId : row.getCategoryId();
            rowCategoryId = normalizeSeriesCategoryId(rowCategoryId);
            row.setWatched(SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), rowCategoryId, row.getChannelId()) != null);
        }
    }

    private String normalizeSeriesCategoryId(String categoryId) {
        if (StringUtils.isBlank(categoryId)) {
            return "";
        }
        Category category = SeriesCategoryDb.get().getById(categoryId);
        if (category != null && isNotBlank(category.getCategoryId())) {
            return category.getCategoryId();
        }
        return categoryId;
    }

    private Category resolveCategoryByDbId(Account account, String categoryId) {
        if (account.getAction() == Account.AccountAction.vod) {
            return VodCategoryDb.get().getById(categoryId);
        }
        if (account.getAction() == Account.AccountAction.series) {
            return SeriesCategoryDb.get().getById(categoryId);
        }
        return CategoryDb.get().getCategoryByDbId(categoryId, account);
    }

    private String resolveCategoryApiId(Account account, String categoryId) {
        Category category = resolveCategoryByDbId(account, categoryId);
        return category != null ? category.getCategoryId() : categoryId;
    }

    private List<Category> resolveCategoriesForAccount(Account account) {
        if (account.getAction() == Account.AccountAction.vod) {
            return VodCategoryDb.get().getCategories(account);
        }
        if (account.getAction() == Account.AccountAction.series) {
            return SeriesCategoryDb.get().getCategories(account);
        }
        return CategoryDb.get().getCategories(account);
    }

    private List<Channel> dedupeChannels(List<Channel> channels) {
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        if (channels == null) {
            return List.of();
        }
        for (Channel c : channels) {
            if (c == null) continue;
            String key = String.join("|",
                    StringUtils.isBlank(c.getChannelId()) ? "" : c.getChannelId().trim(),
                    StringUtils.isBlank(c.getCmd()) ? "" : c.getCmd().trim(),
                    StringUtils.isBlank(c.getName()) ? "" : c.getName().trim().toLowerCase());
            unique.putIfAbsent(key, c);
        }
        return new ArrayList<>(unique.values());
    }

    private SeriesProviderDetails loadProviderSeriesDetails(Account account, String categoryId, String seriesId, JSONArray episodesMeta) {
        if (account.getType() != AccountType.XTREME_API || isBlank(seriesId)) {
            return new SeriesProviderDetails(null, new JSONArray());
        }
        EpisodeList details = XtremeApiParser.parseEpisodes(seriesId, account);
        if (details == null) {
            return new SeriesProviderDetails(null, new JSONArray());
        }
        JSONArray episodesJson = toEpisodesJson(details, indexEpisodesMeta(episodesMeta));
        if (episodesJson.length() > 0) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, toChannels(episodesJson));
        }
        return new SeriesProviderDetails(details.getSeasonInfo(), episodesJson);
    }

    private JSONObject applyInitialImdbMetadata(String seriesName, JSONObject seasonInfo, JSONArray episodes) {
        List<String> fuzzyHints = buildFuzzyHints(seriesName, seasonInfo, episodes);
        JSONObject imdbFirst = ImdbMetadataService.getInstance().findBestEffortDetails(seriesName, "", fuzzyHints);
        copyMetadata(seasonInfo, imdbFirst);
        return imdbFirst;
    }

    private JSONObject applyFallbackImdbMetadata(String seriesName, JSONObject seasonInfo, JSONArray episodes) {
        List<String> fuzzyHints = buildFuzzyHints(firstNonBlank(seasonInfo.optString("name", ""), seriesName), seasonInfo, episodes);
        return ImdbMetadataService.getInstance().findBestEffortDetails(
                firstNonBlank(seasonInfo.optString("name", ""), seriesName),
                seasonInfo.optString("tmdb", ""),
                fuzzyHints
        );
    }

    private void mergeProviderSeasonInfo(JSONObject seasonInfo, SeasonInfo info) {
        if (info == null) {
            return;
        }
        JSONObject provider = new JSONObject(info.toJson());
        mergeMetadata(seasonInfo, provider);
    }

    private JSONArray toEpisodesJson(EpisodeList details, Map<String, JSONObject> episodesMeta) {
        JSONArray episodesJson = new JSONArray();
        if (details == null || details.getEpisodes() == null) {
            return episodesJson;
        }
        for (Episode episode : details.getEpisodes()) {
            Channel channel = toEpisodeChannel(episode, episodesMeta);
            if (channel != null) {
                episodesJson.put(new JSONObject(channel.toJson()));
            }
        }
        return episodesJson;
    }

    private Channel toEpisodeChannel(Episode episode, Map<String, JSONObject> episodesMeta) {
        Channel channel = toChannel(episode);
        if (channel == null) {
            return null;
        }
        enrichEpisode(channel, episodesMeta);
        return channel;
    }

    private JSONArray enrichEpisodes(JSONArray episodes, JSONArray episodesMeta) {
        if (episodes == null) {
            return new JSONArray();
        }
        if (episodes.isEmpty() || episodesMeta == null || episodesMeta.isEmpty()) {
            return episodes;
        }
        Map<String, JSONObject> indexed = indexEpisodesMeta(episodesMeta);
        if (indexed.isEmpty()) {
            return episodes;
        }
        JSONArray enriched = new JSONArray();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject row = episodes.optJSONObject(i);
            if (row == null) {
                continue;
            }
            Channel channel = Channel.fromJson(row.toString());
            if (channel == null) {
                enriched.put(row);
                continue;
            }
            enrichEpisode(channel, indexed);
            enriched.put(new JSONObject(channel.toJson()));
        }
        return enriched;
    }

    private void mergeMetadata(JSONObject target, JSONObject source) {
        mergeMissing(target, source, "name");
        mergeMissing(target, source, KEY_COVER);
        mergeMissing(target, source, "plot");
        mergeMissing(target, source, "cast");
        mergeMissing(target, source, KEY_DIRECTOR);
        mergeMissing(target, source, KEY_GENRE);
        mergeMissing(target, source, KEY_RELEASE_DATE);
        mergeMissing(target, source, KEY_RATING);
        mergeMissing(target, source, "tmdb");
        mergeMissing(target, source, KEY_IMDB_URL);
    }

    private void copyMetadata(JSONObject target, JSONObject source) {
        copyIfPresent(target, source, "name");
        copyIfPresent(target, source, KEY_COVER);
        copyIfPresent(target, source, "plot");
        copyIfPresent(target, source, "cast");
        copyIfPresent(target, source, KEY_DIRECTOR);
        copyIfPresent(target, source, KEY_GENRE);
        copyIfPresent(target, source, KEY_RELEASE_DATE);
        copyIfPresent(target, source, KEY_RATING);
        copyIfPresent(target, source, "tmdb");
        copyIfPresent(target, source, KEY_IMDB_URL);
    }

    private void mergeMissing(JSONObject target, JSONObject source, String key) {
        String existing = target.optString(key, "");
        if (!isBlank(existing)) {
            return;
        }
        String incoming = source.optString(key, "");
        if (!isBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private void copyIfPresent(JSONObject target, JSONObject source, String key) {
        String incoming = source.optString(key, "");
        if (!isBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private Map<String, JSONObject> indexEpisodesMeta(JSONArray episodesMeta) {
        Map<String, JSONObject> indexed = new HashMap<>();
        if (episodesMeta == null) {
            return indexed;
        }
        for (int i = 0; i < episodesMeta.length(); i++) {
            JSONObject row = episodesMeta.optJSONObject(i);
            if (row == null) continue;
            String season = safeNumeric(row.optString("season", ""));
            String episode = safeNumeric(row.optString("episodeNum", ""));
            if (!isBlank(season) && !isBlank(episode)) {
                indexed.put(season + ":" + episode, row);
            }
            String title = normalize(row.optString("title", ""));
            if (!isBlank(title)) {
                indexed.put("title:" + title, row);
            }
        }
        return indexed;
    }

    private void enrichEpisode(Channel channel, Map<String, JSONObject> episodesMeta) {
        if (episodesMeta == null || episodesMeta.isEmpty() || channel == null) {
            return;
        }
        String season = safeNumeric(channel.getSeason());
        String episode = safeNumeric(channel.getEpisodeNum());
        JSONObject meta = null;
        if (!isBlank(season) && !isBlank(episode)) {
            meta = episodesMeta.get(season + ":" + episode);
        }
        if (meta == null) {
            meta = episodesMeta.get("title:" + normalize(channel.getName()));
        }
        if (meta == null) {
            return;
        }
        if (isBlank(channel.getDescription())) {
            channel.setDescription(meta.optString("plot", ""));
        }
        if (isBlank(channel.getReleaseDate())) {
            channel.setReleaseDate(meta.optString(KEY_RELEASE_DATE, ""));
        }
        if (!isBlank(meta.optString("logo", ""))) {
            channel.setLogo(meta.optString("logo", ""));
        }
    }

    private String normalize(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeNumeric(String value) {
        if (isBlank(value)) return "";
        String normalized = value.replaceAll("\\D", "");
        return isBlank(normalized) ? "" : normalized;
    }

    private void applyNameYearFallback(JSONObject seasonInfo, String rawSeriesName) {
        if (isBlank(rawSeriesName)) {
            return;
        }
        String trimmed = rawSeriesName.trim();
        String inferredName = trimmed.replaceAll("\\s*\\((19|20)\\d{2}\\)\\s*$", "").trim();
        String inferredYear = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((19|20)\\d{2}\\)\\s*$").matcher(trimmed);
        if (m.find()) {
            inferredYear = m.group().replaceAll("\\D", "");
        }
        if (isBlank(seasonInfo.optString("name", "")) && !isBlank(inferredName)) {
            seasonInfo.put("name", inferredName);
        }
        if (isBlank(seasonInfo.optString(KEY_RELEASE_DATE, "")) && !isBlank(inferredYear)) {
            seasonInfo.put(KEY_RELEASE_DATE, inferredYear);
        }
    }

    private List<String> buildFuzzyHints(String baseTitle, JSONObject seasonInfo, JSONArray episodes) {
        List<String> hints = new ArrayList<>();
        addSeriesHint(hints, baseTitle);
        if (seasonInfo != null) {
            addSeriesHint(hints, seasonInfo.optString("name", ""));
            addSeriesHint(hints, seasonInfo.optString("plot", ""));
            addSeriesHint(hints, seasonInfo.optString(KEY_RELEASE_DATE, ""));
        }
        if (episodes != null) {
            for (int i = 0; i < Math.min(8, episodes.length()); i++) {
                JSONObject row = episodes.optJSONObject(i);
                if (row == null) continue;
                addSeriesHint(hints, row.optString("name", ""));
                addSeriesHint(hints, row.optString(KEY_RELEASE_DATE, ""));
            }
        }
        return hints;
    }

    private void addSeriesHint(List<String> hints, String value) {
        if (hints == null || isBlank(value)) {
            return;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b", " ")
                .replaceAll("(?i)\\bs\\d{1,2}e\\d{1,3}\\b", " ")
                .replaceAll("[\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (isBlank(cleaned) || cleaned.length() < 2 || hints.contains(cleaned)) {
            return;
        }
        hints.add(cleaned);
    }

    private JSONArray toJsonArray(List<Channel> channels) {
        return new JSONArray(ServerUtils.objectToJson(channels));
    }

    private List<Channel> toChannels(JSONArray episodesJson) {
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < episodesJson.length(); i++) {
            JSONObject obj = episodesJson.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            Channel channel = Channel.fromJson(obj.toString());
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static class SingletonHelper {
        private static final CatalogApplicationService INSTANCE = new CatalogApplicationService();
    }

    private record StalkerPageResult(List<Channel> items, Pagination pagination) {
    }

    private record SeriesProviderDetails(SeasonInfo seasonInfo, JSONArray episodes) {
    }
}
