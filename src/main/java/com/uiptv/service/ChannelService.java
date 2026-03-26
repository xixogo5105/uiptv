package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Pagination;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.ui.RssParser;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.ServerUtils;
import com.uiptv.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.FetchAPI.nullSafeString;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

@Slf4j
@SuppressWarnings("java:S6548")
public class ChannelService {
    private static final String FIELD_CENSORED = "censored";
    private static final String FIELD_STATUS = "status";
    private static final int MAX_PAGES_WITHOUT_PAGINATION = 200;
    private static final long STALKER_BASE_DELAY_MS = Long.getLong("uiptv.stalker.page.delay.ms", 800L);
    private static final long STALKER_MAX_DELAY_MS = Long.getLong("uiptv.stalker.page.maxDelay.ms", 8000L);
    private static final long STALKER_JITTER_MS = Long.getLong("uiptv.stalker.page.jitter.ms", 200L);
    private static final int STALKER_MAX_RETRIES_PER_PAGE = Integer.getInteger("uiptv.stalker.page.maxRetries", 2);
    private static final Map<String, RequestThrottle> STALKER_THROTTLES = new java.util.concurrent.ConcurrentHashMap<>();
    private final CacheService cacheService;
    private final ContentFilterService contentFilterService;
    private final LogoResolverService logoResolverService;

    private ChannelService() {
        this.cacheService = new CacheServiceImpl();
        this.contentFilterService = ContentFilterService.getInstance();
        this.logoResolverService = LogoResolverService.getInstance();
    }

    public static Map<String, String> getChannelOrSeriesParams(String category, int pageNumber, Account.AccountAction accountAction, String movieId, String seriesId) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", accountAction.name());
        params.put("action", "get_ordered_list");
        params.put("genre", category);
        params.put("force_ch_link_check", "");
        params.put("fav", "0");
        params.put("sortby", "added");
        if (accountAction == series) {
            params.put("movie_id", isBlank(movieId) ? "0" : movieId);
            params.put("category", category);
            params.put("season_id", isBlank(seriesId) ? "0" : seriesId);
            params.put("episode_id", "0");
        }
        params.put("hd", "1");
        params.put("p", String.valueOf(pageNumber));
        params.put("per_page", "999");
        params.put("max_count", "0");
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    private static class SingletonHelper {
        private static final ChannelService INSTANCE = new ChannelService();
    }

    public static ChannelService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public List<Channel> get(String categoryId, Account account, String dbId) throws IOException {
        return get(categoryId, account, dbId, null, null, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger) throws IOException {
        return get(categoryId, account, dbId, logger, null, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger, Consumer<List<Channel>> callback) throws IOException {
        return get(categoryId, account, dbId, logger, callback, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) throws IOException {
        return get(categoryId, account, dbId, logger, callback, isCancelled, null);
    }

    public List<Channel> get(String categoryId, Account account, String dbId, LoggerCallback logger, Consumer<List<Channel>> callback,
                             Supplier<Boolean> isCancelled, Consumer<PageProgress> progressCallback) throws IOException {
        if (NOT_LIVE_TV_CHANNELS.contains(account.getAction())) {
            return getNonLiveChannels(categoryId, account, dbId, logger, callback, isCancelled, progressCallback);
        }
        if (account.getType() == AccountType.RSS_FEED) {
            return publishChannels(maybeFilterChannels(rssChannels(categoryId, account), true), callback);
        }
        List<Channel> channels = loadCachedLiveChannels(categoryId, dbId, account, logger);
        if (account.getAction() == itv && !channels.isEmpty()) {
            List<Channel> visibleChannels = maybeFilterChannels(dedupeChannels(channels), true);
            publishChannels(visibleChannels, callback);
            resolveChannelLogosAsync(visibleChannels, callback, () -> isCancelled != null && isCancelled.get());
            return visibleChannels;
        }
        channels.forEach(this::resolveLogoIfNeeded);
        if (account.getType() == STALKER_PORTAL && account.getAction() == itv && channels.isEmpty()) {
            fetchAndCacheMissingLiveChannels(categoryId, account, dbId, callback, isCancelled, logger, channels);
        }
        List<Channel> result = maybeFilterChannels(dedupeChannels(channels), true);
        return publishChannels(result, callback);
    }

    private List<Channel> loadCachedLiveChannels(String categoryId, String dbId, Account account, LoggerCallback logger) throws IOException {
        List<Channel> channels = resolveCachedLiveChannels(categoryId, dbId, account);
        if (!channels.isEmpty()) {
            return channels;
        }
        if (cacheService.getChannelCountForAccount(account.getDbId()) != 0) {
            return channels;
        }
        cacheService.reloadCache(account, logger != null ? logger : log::info);
        return resolveCachedLiveChannels(categoryId, dbId, account);
    }

    private List<Channel> getNonLiveChannels(String categoryId, Account account, String dbId, LoggerCallback logger,
                                             Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled,
                                             Consumer<PageProgress> progressCallback) {
        if (account.getType() == STALKER_PORTAL) {
            ensureStalkerSession(account, logger);
        }
        if (shouldUseVodSeriesDbCache(account)) {
            return getCachedVodSeriesChannels(categoryId, account, dbId, logger, callback, isCancelled, progressCallback);
        }
        return getVodOrSeries(categoryId, account, callback, isCancelled, logger, progressCallback);
    }

    private List<Channel> getCachedVodSeriesChannels(String categoryId, Account account, String dbId, LoggerCallback logger,
                                                     Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled,
                                                     Consumer<PageProgress> progressCallback) {
        List<Channel> cachedChannels = getVodSeriesFromDbCache(account, dbId);
        if (!cachedChannels.isEmpty() && isVodSeriesChannelsFresh(account, dbId)) {
            log(logger, "Loaded channels from local cache for category " + categoryId + ".");
            if (progressCallback != null) {
                progressCallback.accept(new PageProgress(cachedChannels.size(), cachedChannels.size(), 1, 1));
            }
            return publishChannels(maybeFilterChannels(dedupeChannels(cachedChannels), true), callback);
        }
        log(logger, "No fresh cache found for category " + categoryId + ". Fetching from portal...");
        boolean streamingCallback = callback != null && account.getType() == STALKER_PORTAL;
        List<Channel> fetchedChannels = fetchVodSeriesFromProviderAllPages(categoryId, account, isCancelled, logger, callback, progressCallback);
        boolean cancelled = Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.get());
        if (!fetchedChannels.isEmpty() && !cancelled) {
            try {
                saveVodSeriesToDbCache(account, dbId, fetchedChannels);
                log(logger, "Channel list complete for category " + categoryId + ". Saved " + fetchedChannels.size() + " channels to local cache.");
            } catch (Exception e) {
                log(logger, "Failed to save " + fetchedChannels.size() + " channels to local cache for category " + categoryId + ". Error: " + e.getMessage());
            }
        } else if (cancelled) {
            log(logger, "Channel fetch cancelled before cache save for category " + categoryId + ".");
        }
        List<Channel> resolved = !fetchedChannels.isEmpty() ? fetchedChannels : cachedChannels;
        return publishChannels(maybeFilterChannels(dedupeChannels(resolved), true), streamingCallback ? null : callback);
    }

    private void fetchAndCacheMissingLiveChannels(String categoryId, Account account, String dbId, Consumer<List<Channel>> callback,
                                                  Supplier<Boolean> isCancelled, LoggerCallback logger, List<Channel> channels) {
        log(logger, "No cached live channels for category " + categoryId + ". Fetching from portal...");
        List<Channel> fetchedChannels = getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, false, logger);
        if (!fetchedChannels.isEmpty()) {
            ChannelDb.get().saveAll(fetchedChannels, dbId, account);
            channels.addAll(fetchedChannels);
            log(logger, "Saved " + fetchedChannels.size() + " live channels to local cache.");
        }
    }

    private List<Channel> publishChannels(List<Channel> channels, Consumer<List<Channel>> callback) {
        if (callback != null) {
            callback.accept(channels);
        }
        return channels;
    }

    private void resolveChannelLogosAsync(List<Channel> channels, Consumer<List<Channel>> callback, BooleanSupplier isCancelled) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        Thread logoThread = new Thread(() -> {
            boolean updated = false;
            for (Channel channel : channels) {
                if (Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.getAsBoolean())) {
                    return;
                }
                String before = channel == null ? null : channel.getLogo();
                resolveLogoIfNeeded(channel);
                if (!Objects.equals(before, channel == null ? null : channel.getLogo())) {
                    updated = true;
                }
            }
            if (updated && callback != null && (isCancelled == null || !isCancelled.getAsBoolean())) {
                callback.accept(channels);
            }
        }, "channel-logo-resolver");
        logoThread.setDaemon(true);
        logoThread.start();
    }

    private List<Channel> resolveCachedLiveChannels(String categoryId, String dbCategoryId, Account account) {
        if (!isAllCategoryForLocalCachedProvider(categoryId, account)) {
            return dedupeChannels(ChannelDb.get().getChannels(dbCategoryId));
        }

        // Primary path: if "All" itself is cached, use it directly.
        List<Channel> directAll = dedupeChannels(ChannelDb.get().getChannels(dbCategoryId));
        if (!directAll.isEmpty()) {
            return directAll;
        }

        // Fallback for legacy/stale caches where channels may still be under non-All categories.
        List<Channel> merged = new ArrayList<>();
        for (Category category : CategoryDb.get().getCategories(account)) {
            if (category == null || isBlank(category.getDbId())) {
                continue;
            }
            merged.addAll(ChannelDb.get().getChannels(category.getDbId()));
        }
        return dedupeChannels(merged);
    }

    private boolean isAllCategoryForLocalCachedProvider(String categoryId, Account account) {
        return "All".equalsIgnoreCase(categoryId)
                && account != null
                && account.getType() != STALKER_PORTAL
                && account.getType() != XTREME_API;
    }

    @SuppressWarnings("java:S4276")
    private List<Channel> getVodOrSeries(String categoryId, Account account, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled,
                                         LoggerCallback logger, Consumer<PageProgress> progressCallback) {
        List<Channel> cachedChannels = new ArrayList<>(getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, true, logger, progressCallback));
        return maybeFilterChannels(cachedChannels, true);
    }

    private boolean shouldUseVodSeriesDbCache(Account account) {
        return (account.getAction() == vod || account.getAction() == series)
                && (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API);
    }

    private List<Channel> getVodSeriesFromDbCache(Account account, String dbCategoryId) {
        List<Channel> channels;
        if (account.getAction() == vod) {
            channels = VodChannelDb.get().getChannels(account, dbCategoryId);
        } else if (account.getAction() == series) {
            channels = SeriesChannelDb.get().getChannels(account, dbCategoryId);
        } else {
            channels = Collections.emptyList();
        }
        channels = dedupeChannels(channels);
        channels.forEach(channel -> {
            channel.setLogo(normalizeLogoUrl(account, channel.getLogo()));
            if (isBlank(channel.getLogo())) {
                channel.setLogo(normalizeLogoUrl(account, extractLogoFromExtraJson(channel.getExtraJson())));
            }
        });
        channels.forEach(this::resolveLogoIfNeeded);
        return channels;
    }

    private boolean isVodSeriesChannelsFresh(Account account, String dbCategoryId) {
        long cacheTtlMs = ConfigurationService.getInstance().getCacheExpiryMs();
        if (account.getAction() == vod) {
            return VodChannelDb.get().isFresh(account, dbCategoryId, cacheTtlMs);
        }
        if (account.getAction() == series) {
            return SeriesChannelDb.get().isFresh(account, dbCategoryId, cacheTtlMs);
        }
        return false;
    }

    private void saveVodSeriesToDbCache(Account account, String dbCategoryId, List<Channel> channels) {
        if (account.getAction() == vod) {
            VodChannelDb.get().saveAll(channels, dbCategoryId, account);
            return;
        }
        if (account.getAction() == series) {
            SeriesChannelDb.get().saveAll(channels, dbCategoryId, account);
        }
    }

    @SuppressWarnings("java:S4276")
    private List<Channel> fetchVodSeriesFromProviderAllPages(String categoryId, Account account, Supplier<Boolean> isCancelled, LoggerCallback logger,
                                                             Consumer<List<Channel>> callback, Consumer<PageProgress> progressCallback) {
        List<Channel> channels;
        if (account.getType() == XTREME_API) {
            channels = dedupeChannels(XtremeParser.parseChannels(categoryId, account));
            if (progressCallback != null) {
                progressCallback.accept(new PageProgress(channels.size(), channels.size(), 1, 1));
            }
        } else {
            // Stream pages to the UI as they arrive; full list is still returned for caching.
            channels = getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, true, logger, progressCallback);
        }
        channels.forEach(this::resolveLogoIfNeeded);
        return channels;
    }

    private List<Channel> rssChannels(String category, Account account) {
        Set<Channel> channels = new LinkedHashSet<>();
        List<PlaylistEntry> rssEntries = RssParser.parse(account.getM3u8Path());
        rssEntries.stream().filter(e -> category.equalsIgnoreCase("All") || e.getGroupTitle().equalsIgnoreCase(category) || e.getId().equalsIgnoreCase(category)).forEach(entry -> {
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            resolveLogoIfNeeded(c);
            channels.add(c);
        });
        return channels.stream().toList();
    }

    public void reloadCache(Account account, LoggerCallback logger) throws IOException {
        cacheService.reloadCache(account, logger);
    }

    public int getChannelCountForAccount(String accountId) {
        return cacheService.getChannelCountForAccount(accountId);
    }

    public List<Channel> getCachedLiveChannelsByDbCategoryId(String dbCategoryId) {
        return dedupeChannels(ChannelDb.get().getChannels(dbCategoryId));
    }

    public boolean hasCachedLiveChannelsByDbCategoryId(String dbCategoryId) {
        return !getCachedLiveChannelsByDbCategoryId(dbCategoryId).isEmpty();
    }

    public Channel getChannelByChannelIdAndAccount(String channelId, String accountId) {
        return getChannelByChannelIdAndAccount(channelId, accountId, true);
    }

    public Channel getChannelByChannelIdAndAccount(String channelId, String accountId, boolean resolveLogo) {
        if (StringUtils.isBlank(channelId) || StringUtils.isBlank(accountId)) {
            return null;
        }
        Channel channel = ChannelDb.get().getChannelByChannelIdAndAccount(channelId, accountId);
        if (channel != null && resolveLogo) {
            resolveLogoIfNeeded(channel);
        }
        return channel;
    }

    public List<Channel> getChannelsByChannelIdsAndAccount(Collection<String> channelIds, String accountId) {
        return getChannelsByChannelIdsAndAccount(channelIds, accountId, true);
    }

    public List<Channel> getChannelsByChannelIdsAndAccount(Collection<String> channelIds, String accountId, boolean resolveLogo) {
        if (channelIds == null || channelIds.isEmpty() || StringUtils.isBlank(accountId)) {
            return List.of();
        }
        List<Channel> channels = ChannelDb.get().getChannelsByChannelIdsAndAccount(channelIds, accountId);
        if (resolveLogo) {
            channels.forEach(this::resolveLogoIfNeeded);
        }
        return channels;
    }

    public Channel findCachedLiveChannel(Account account, String channelId, String channelName) {
        if (account == null || isBlank(account.getDbId())) {
            return null;
        }
        Channel byId = getChannelByChannelIdAndAccount(channelId, account.getDbId());
        if (byId != null) {
            return byId;
        }
        if (isBlank(channelName)) {
            return null;
        }
        String targetName = channelName.trim();
        for (Category category : CategoryDb.get().getCategories(account)) {
            if (category == null || isBlank(category.getDbId())) {
                continue;
            }
            Channel byName = ChannelDb.get().getChannels(category.getDbId()).stream()
                    .filter(c -> c != null && isNotBlank(c.getName()) && targetName.equalsIgnoreCase(c.getName().trim()))
                    .findFirst()
                    .orElse(null);
            if (byName != null) {
                resolveLogoIfNeeded(byName);
                return byName;
            }
        }
        return null;
    }

    public Channel findCachedVodChannel(Account account, String categoryHint, String channelId, String channelName) {
        return findCachedVodOrSeriesChannel(account, categoryHint, channelId, channelName, false);
    }

    public Channel findCachedSeriesChannel(Account account, String categoryHint, String channelId, String channelName) {
        return findCachedVodOrSeriesChannel(account, categoryHint, channelId, channelName, true);
    }

    private Channel findCachedVodOrSeriesChannel(Account account, String categoryHint, String channelId, String channelName, boolean seriesLookup) {
        if (account == null || isBlank(account.getDbId())) {
            return null;
        }
        String dbCategoryId = resolveCategoryDbId(account, categoryHint);
        if (isBlank(dbCategoryId)) {
            return null;
        }
        List<Channel> channels = seriesLookup
                ? SeriesChannelDb.get().getChannels(account, dbCategoryId)
                : VodChannelDb.get().getChannels(account, dbCategoryId);
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        if (isNotBlank(channelId)) {
            Channel byId = channels.stream()
                    .filter(c -> c != null && isNotBlank(c.getChannelId()) && channelId.trim().equals(c.getChannelId().trim()))
                    .findFirst()
                    .orElse(null);
            if (byId != null) {
                resolveLogoIfNeeded(byId);
                return byId;
            }
        }
        if (isNotBlank(channelName)) {
            Channel byName = channels.stream()
                    .filter(c -> c != null && isNotBlank(c.getName()) && channelName.trim().equalsIgnoreCase(c.getName().trim()))
                    .findFirst()
                    .orElse(null);
            if (byName != null) {
                resolveLogoIfNeeded(byName);
                return byName;
            }
        }
        return null;
    }

    private String resolveCategoryDbId(Account account, String categoryHint) {
        if (account == null || isBlank(account.getDbId()) || isBlank(categoryHint)) {
            return "";
        }
        String hint = categoryHint.trim();
        List<Category> categories = CategoryDb.get().getCategories(account);
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        Category byDbId = categories.stream()
                .filter(c -> c != null && isNotBlank(c.getDbId()) && hint.equals(c.getDbId().trim()))
                .findFirst()
                .orElse(null);
        if (byDbId != null) {
            return byDbId.getDbId();
        }
        Category byTitle = categories.stream()
                .filter(c -> c != null && isNotBlank(c.getTitle()) && hint.equalsIgnoreCase(c.getTitle().trim()))
                .findFirst()
                .orElse(null);
        if (byTitle != null) {
            return byTitle.getDbId();
        }
        Category byProviderCategoryId = categories.stream()
                .filter(c -> c != null && isNotBlank(c.getCategoryId()) && hint.equals(c.getCategoryId().trim()))
                .findFirst()
                .orElse(null);
        return byProviderCategoryId == null ? "" : byProviderCategoryId.getDbId();
    }

    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true);
    }

    @SuppressWarnings("java:S107")
    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, LoggerCallback logger) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true, logger, null);
    }

    @SuppressWarnings("java:S107")
    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, censor, null, null);
    }

    @SuppressWarnings("java:S107")
    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled, boolean censor, LoggerCallback logger) {
        return getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, censor, logger, null);
    }

    @SuppressWarnings("java:S107")
    public List<Channel> getStalkerPortalChOrSeries(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback,
                                                    Supplier<Boolean> isCancelled, boolean censor, LoggerCallback logger,
                                                    Consumer<PageProgress> progressCallback) {
        log(logger, "Starting portal fetch for category " + category + ".");
        // Different portals are inconsistent on first page index (0 vs 1); try both.
        List<Channel> channelsFromPageZero = fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 0, logger, progressCallback);
        if (!channelsFromPageZero.isEmpty()) {
            log(logger, "Portal fetch finished with " + channelsFromPageZero.size() + " channels.");
            return channelsFromPageZero;
        }
        log(logger, "No channels on page 0. Retrying from page 1...");
        List<Channel> fallback = dedupeChannels(fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 1, logger, progressCallback));
        log(logger, "Portal fetch finished with " + fallback.size() + " channels.");
        return fallback;
    }

    @SuppressWarnings("java:S107")
    private List<Channel> fetchPagedStalkerChannels(String category, Account account, String movieId, String seriesId, Consumer<List<Channel>> callback,
                                                    Supplier<Boolean> isCancelled, boolean censor, int startPage, LoggerCallback logger,
                                                    Consumer<PageProgress> progressCallback) {
        List<Channel> channelList = new ArrayList<>();
        ensureStalkerAccountSession(account, logger);
        RequestThrottle throttle = resolveStalkerThrottle(account);
        StalkerPageRequest request = new StalkerPageRequest(category, account, movieId, seriesId, censor);
        PageFetchResult firstPage = fetchInitialPage(request, startPage, isCancelled, logger, throttle);
        if (firstPage == null) {
            return channelList;
        }
        if (isEmptyChannelPage(firstPage)) {
            log(logger, "Page " + startPage + " returned no channels.");
            return channelList;
        }

        appendFetchedPage(channelList, firstPage, startPage, callback, logger);
        PageAccumulator accumulator = new PageAccumulator();
        accumulator.update(firstPage);
        emitProgress(progressCallback, accumulator.fetchedItems, accumulator.totalItems, startPage + 1, accumulator.pageCount);

        PaginationPlan plan = new PaginationPlan(startPage, callback, isCancelled, logger, progressCallback, throttle, accumulator);
        paginateAdditionalPages(channelList, request, plan, firstPage);
        emitProgress(progressCallback, accumulator.fetchedItems, accumulator.totalItems > 0 ? accumulator.totalItems : accumulator.fetchedItems,
                accumulator.pageCount > 0 ? accumulator.pageCount : Math.max(1, (startPage + 1)), accumulator.pageCount);
        return dedupeChannels(channelList);
    }

    private PageFetchResult fetchInitialPage(StalkerPageRequest request, int startPage, Supplier<Boolean> isCancelled,
                                             LoggerCallback logger, RequestThrottle throttle) {
        PageAttempt attempt = fetchPageWithRetries(request, startPage, isCancelled, logger, throttle, true);
        if (attempt.page() == null) {
            return null;
        }
        return retryEmptyFirstPage(request, startPage, logger, attempt.page(), throttle);
    }

    private void paginateAdditionalPages(List<Channel> channelList, StalkerPageRequest request, PaginationPlan plan,
                                         PageFetchResult firstPage) {
        int maxAdditionalPages = resolveMaxAdditionalPages(firstPage);
        boolean stopPagination = false;
        for (int pageNumber = plan.startPage() + 1; pageNumber <= plan.startPage() + maxAdditionalPages && !stopPagination; pageNumber++) {
            PageAttempt attempt = fetchPageWithRetries(request, pageNumber, plan.isCancelled(), plan.logger(), plan.throttle());
            if (attempt.cancelled()) {
                stopPagination = true;
            } else {
                PageFetchResult page = attempt.page();
                if (page == null) {
                    stopPagination = true;
                } else if (isEmptyChannelPage(page)) {
                    log(plan.logger(), "Page " + pageNumber + " returned no channels. Stopping pagination.");
                    stopPagination = true;
                } else {
                    appendFetchedPage(channelList, page, pageNumber, plan.callback(), plan.logger());
                    plan.accumulator().update(page);
                    emitProgress(plan.progressCallback(), plan.accumulator().fetchedItems, plan.accumulator().totalItems,
                            pageNumber + 1, plan.accumulator().pageCount);
                }
            }
        }
    }

    private PageAttempt fetchPageWithRetries(StalkerPageRequest request, int pageNumber, Supplier<Boolean> isCancelled,
                                             LoggerCallback logger, RequestThrottle throttle) {
        return fetchPageWithRetries(request, pageNumber, isCancelled, logger, throttle, false);
    }

    private PageAttempt fetchPageWithRetries(StalkerPageRequest request, int pageNumber, Supplier<Boolean> isCancelled,
                                             LoggerCallback logger, RequestThrottle throttle, boolean ignoreCancellation) {
        for (int attempt = 0; attempt <= STALKER_MAX_RETRIES_PER_PAGE; attempt++) {
            if (!ignoreCancellation && isPageFetchCancelled(isCancelled)) {
                logFetchCancelled(logger, pageNumber);
                return PageAttempt.cancelledAttempt();
            }
            throttle.awaitPermit();
            try {
                PageFetchResult page = fetchStalkerPage(request.category(), request.account(), request.movieId(),
                        request.seriesId(), request.censor(), pageNumber, logger);
                long nextDelay = throttle.onSuccess();
                logNextPageDelay(logger, nextDelay);
                return PageAttempt.success(page);
            } catch (Exception e) {
                long nextDelay = throttle.onFailure();
                logFetchFailure(logger, pageNumber, attempt + 1, nextDelay, e);
                if (attempt >= STALKER_MAX_RETRIES_PER_PAGE) {
                    logGivingUp(logger, pageNumber, attempt + 1);
                }
            }
        }
        return PageAttempt.failed();
    }

    private int resolveMaxAdditionalPages(PageFetchResult firstPage) {
        if (firstPage.pagination == null) {
            return MAX_PAGES_WITHOUT_PAGINATION;
        }
        return Math.max(firstPage.pagination.getPageCount() + 1, 2);
    }

    private void logNextPageDelay(LoggerCallback logger, long nextDelay) {
        log(logger, "Waiting " + nextDelay + "ms before next page fetch.");
    }

    private void logFetchFailure(LoggerCallback logger, int pageNumber, int attempt, long nextDelay, Exception e) {
        log(logger, "Failed to fetch page " + pageNumber + " (attempt " + attempt + "). Backing off " + nextDelay + "ms. Error: " + e.getMessage());
    }

    private void logGivingUp(LoggerCallback logger, int pageNumber, int attempts) {
        log(logger, "Giving up on page " + pageNumber + " after " + attempts + " attempts.");
    }

    private void logFetchCancelled(LoggerCallback logger, int pageNumber) {
        log(logger, "Portal fetch cancelled at page " + pageNumber + ".");
    }

    private void appendFetchedPage(List<Channel> channelList, PageFetchResult page, int pageNumber, Consumer<List<Channel>> callback, LoggerCallback logger) {
        channelList.addAll(page.channels);
        log(logger, "Fetched " + page.channels.size() + " channels from page " + pageNumber + ".");
        if (callback != null) {
            callback.accept(page.channels);
        }
    }

    private void ensureStalkerAccountSession(Account account, LoggerCallback logger) {
        if (account.getType() == STALKER_PORTAL) {
            ensureStalkerSession(account, logger);
        }
    }

    private PageFetchResult retryEmptyFirstPage(StalkerPageRequest request, int startPage, LoggerCallback logger,
                                                PageFetchResult firstPage, RequestThrottle throttle) {
        if (!isEmptyChannelPage(firstPage) || request.account().getType() != STALKER_PORTAL) {
            return firstPage;
        }
        log(logger, "No channels returned. Refreshing Stalker session and retrying page " + startPage + " once...");
        HandshakeService.getInstance().hardTokenRefresh(request.account());
        if (throttle != null) {
            throttle.awaitPermit();
        }
        try {
            PageFetchResult page = fetchStalkerPage(request.category(), request.account(), request.movieId(),
                    request.seriesId(), request.censor(), startPage, logger);
            if (throttle != null) {
                long nextDelay = throttle.onSuccess();
                logNextPageDelay(logger, nextDelay);
            }
            return page;
        } catch (Exception e) {
            if (throttle != null) {
                long nextDelay = throttle.onFailure();
                log(logger, "Failed to retry page " + startPage + ". Backing off " + nextDelay + "ms. Error: " + e.getMessage());
            }
            return firstPage;
        }
    }

    private record StalkerPageRequest(String category, Account account, String movieId, String seriesId, boolean censor) {
    }

    private record PaginationPlan(int startPage, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled,
                                  LoggerCallback logger, Consumer<PageProgress> progressCallback, RequestThrottle throttle,
                                  PageAccumulator accumulator) {
    }

    private boolean isEmptyChannelPage(PageFetchResult page) {
        return page.channels == null || page.channels.isEmpty();
    }

    private PageFetchResult fetchStalkerPage(String category, Account account, String movieId, String seriesId,
                                             boolean censor, int pageNumber, LoggerCallback logger) {
        log(logger, "Fetching page " + pageNumber + " for category " + category + "...");
        String json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.getAction(), movieId, seriesId), account);
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        List<Channel> channels = account.getAction() == itv
                ? ChannelService.getInstance().parseItvChannels(json, censor)
                : ChannelService.getInstance().parseVodChannels(account, json, censor);
        return new PageFetchResult(channels, pagination);
    }

    @SuppressWarnings("java:S4276")
    private boolean isPageFetchCancelled(Supplier<Boolean> isCancelled) {
        return Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.get());
    }

    private RequestThrottle resolveStalkerThrottle(Account account) {
        String portalHost = "";
        if (account != null && !isBlank(account.getServerPortalUrl())) {
            try {
                URI uri = URI.create(account.getServerPortalUrl());
                portalHost = uri.getHost() == null ? account.getServerPortalUrl() : uri.getHost();
            } catch (Exception _) {
                portalHost = account.getServerPortalUrl();
            }
        }
        String key = (account == null ? "" : account.getDbId()) + "|" + portalHost + "|" + (account == null ? "" : account.getAction());
        return STALKER_THROTTLES.computeIfAbsent(key, _ -> new RequestThrottle(STALKER_BASE_DELAY_MS, STALKER_MAX_DELAY_MS, STALKER_JITTER_MS));
    }

    static final class RequestThrottle {
        private final long baseDelayMs;
        private final long maxDelayMs;
        private final long jitterMs;
        private long nextAllowedAtMs;
        private int failures;

        RequestThrottle(long baseDelayMs, long maxDelayMs, long jitterMs) {
            this.baseDelayMs = Math.max(0, baseDelayMs);
            this.maxDelayMs = Math.max(this.baseDelayMs, maxDelayMs);
            this.jitterMs = Math.max(0, jitterMs);
        }

        void awaitPermit() {
            long delay;
            synchronized (this) {
                long now = System.currentTimeMillis();
                delay = nextAllowedAtMs - now;
            }
            if (delay <= 0) {
                return;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized long onSuccess() {
            failures = 0;
            return scheduleNext(baseDelayMs);
        }

        synchronized long onFailure() {
            failures = Math.min(failures + 1, 6);
            long backoff = baseDelayMs * (1L << failures);
            return scheduleNext(Math.min(backoff, maxDelayMs));
        }

        private long scheduleNext(long baseDelay) {
            long jitter = jitterMs == 0 ? 0 : java.util.concurrent.ThreadLocalRandom.current().nextLong(-jitterMs, jitterMs + 1);
            long delay = Math.max(0, baseDelay + jitter);
            nextAllowedAtMs = System.currentTimeMillis() + delay;
            return delay;
        }
    }

    private record PageFetchResult(List<Channel> channels, Pagination pagination) {
    }

    private record PageAttempt(PageFetchResult page, boolean cancelled) {
        static PageAttempt success(PageFetchResult page) {
            return new PageAttempt(page, false);
        }

        static PageAttempt failed() {
            return new PageAttempt(null, false);
        }

        static PageAttempt cancelledAttempt() {
            return new PageAttempt(null, true);
        }
    }

    private static final class PageAccumulator {
        private int fetchedItems;
        private int totalItems;
        private int pageCount;

        private void update(PageFetchResult page) {
            if (page == null) {
                return;
            }
            fetchedItems += page.channels.size();
            if (page.pagination != null) {
                totalItems = Math.max(totalItems, page.pagination.getMaxPageItems());
                pageCount = Math.max(pageCount, page.pagination.getPageCount());
            }
        }
    }

    public record PageProgress(int fetchedItems, int totalItems, int pageNumber, int pageCount) {
    }

    private void emitProgress(Consumer<PageProgress> progressCallback, int fetchedItems, int totalItems, int pageNumber, int pageCount) {
        if (progressCallback == null) {
            return;
        }
        progressCallback.accept(new PageProgress(Math.max(0, fetchedItems), Math.max(0, totalItems), Math.max(1, pageNumber), Math.max(0, pageCount)));
    }

    private void ensureStalkerSession(Account account, LoggerCallback logger) {
        if (account == null || account.getType() != STALKER_PORTAL) {
            return;
        }
        if (account.isConnected() && isNotBlank(account.getServerPortalUrl())) {
            return;
        }
        log(logger, "Ensuring Stalker session...");
        HandshakeService.getInstance().connect(account);
    }

    private void log(LoggerCallback logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
        log.info(message);
    }

    public List<Channel> getSeries(String categoryId, String movieId, Account account, Consumer<List<Channel>> callback, Supplier<Boolean> isCancelled) {
        // This method does not seem to be part of the caching logic, so it can stay here.
        // If it needs to be cached, it should be moved to CacheServiceImpl.
        return maybeFilterChannels(getStalkerPortalChOrSeries(categoryId, account, movieId, "0", callback, isCancelled), true);
    }

    public String readToJson(Category category, Account account) throws IOException {
        String categoryIdToUse;
        if (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API) {
            categoryIdToUse = category.getCategoryId();
        } else {
            categoryIdToUse = category.getTitle();
        }
        return ServerUtils.objectToJson(get(categoryIdToUse, account, category.getDbId()));
    }

    public Pagination parsePagination(String json, LoggerCallback logger) {
        try {
            JSONObject js = new JSONObject(json);
            JSONObject pagination = js.optJSONObject("pagination");
            if (pagination == null) {
                pagination = js.optJSONObject("js");
            }
            if (pagination != null) {
                if (logger != null) {
                    logger.log("total_items " + nullSafeInteger(pagination, "total_items"));
                    logger.log("max_page_items " + nullSafeInteger(pagination, "max_page_items"));
                }
                return new Pagination(nullSafeInteger(pagination, "total_items"), nullSafeInteger(pagination, "max_page_items"));
            }
        } catch (Exception _) {
            showError("Error while processing response data");
        }
        return null;
    }

    public List<Channel> parseItvChannels(String json, boolean censor) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js", root);
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), jsonChannel.getString("name"), jsonChannel.getString("number"), jsonChannel.getString("cmd"), jsonChannel.getString("cmd_1"), jsonChannel.getString("cmd_2"), jsonChannel.getString("cmd_3"), normalizeLogoUrl(null, jsonChannel.getString("logo")), nullSafeInteger(jsonChannel, FIELD_CENSORED), nullSafeInteger(jsonChannel, FIELD_STATUS), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                channel.setCategoryId(nullSafeString(jsonChannel, "tv_genre_id"));
                channel.setExtraJson(jsonChannel.toString());
                resolveLogoIfNeeded(channel);
                channelList.add(channel);
            }
            return maybeFilterChannels(dedupeChannels(channelList), censor);

        } catch (Exception _) {
            showError("Error while processing itv response data");
        }
        return Collections.emptyList();
    }

    public List<Channel> parseVodChannels(Account account, String json, boolean censor) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js", root);
            JSONArray list = js.getJSONArray("data");
            List<Channel> channelList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonChannel = list.getJSONObject(i);
                String name = nullSafeString(jsonChannel, "name");
                if (isBlank(name)) {
                    name = nullSafeString(jsonChannel, "o_name");
                }
                String number = nullSafeString(jsonChannel, "id");
                String cmd = nullSafeString(jsonChannel, "cmd");
                String categoryId = nullSafeString(jsonChannel, "tv_genre_id");
                String preferredLogo = preferredVodLogo(jsonChannel);

                if (account.getAction() == series && isNotBlank(cmd)) {
                    JSONArray seriesArray = jsonChannel.getJSONArray("series");
                    if (seriesArray != null) {
                        for (int j = 0; j < seriesArray.length(); j++) {
                            Channel channel = new Channel(String.valueOf(seriesArray.get(j)), name + " - Episode " + seriesArray.get(j), number, cmd, null, null, null, normalizeLogoUrl(account, preferredLogo), nullSafeInteger(jsonChannel, FIELD_CENSORED), nullSafeInteger(jsonChannel, FIELD_STATUS), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                            channel.setCategoryId(categoryId);
                            channel.setExtraJson(jsonChannel.toString());
                            resolveLogoIfNeeded(channel);
                            channelList.add(channel);
                        }
                    }
                } else {
                    Channel channel = new Channel(String.valueOf(jsonChannel.get("id")), name, number, cmd, null, null, null, normalizeLogoUrl(account, preferredLogo), nullSafeInteger(jsonChannel, FIELD_CENSORED), nullSafeInteger(jsonChannel, FIELD_STATUS), nullSafeInteger(jsonChannel, "hd"), null, null, null, null, null);
                    channel.setCategoryId(categoryId);
                    channel.setExtraJson(jsonChannel.toString());
                    resolveLogoIfNeeded(channel);
                    channelList.add(channel);
                }
            }
            List<Channel> censoredChannelList = maybeFilterChannels(dedupeChannels(channelList), censor);
            Collections.sort(censoredChannelList, Comparator.comparing(Channel::getCompareSeason).thenComparing(Channel::getCompareEpisode));
            return censoredChannelList;
        } catch (Exception _) {
            showError("Error while processing vod response data");
        }
        return Collections.emptyList();
    }

    private String preferredVodLogo(JSONObject jsonChannel) {
        if (jsonChannel == null) {
            return "";
        }
        String logo = nullSafeString(jsonChannel, "screenshot_uri");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "stream_icon");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "cover");
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "movie_image");
        return logo;
    }

    private String extractLogoFromExtraJson(String extraJson) {
        if (isBlank(extraJson)) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(extraJson);
            String logo = nullSafeString(json, "screenshot_uri");
            if (isBlank(logo)) logo = nullSafeString(json, "stream_icon");
            if (isBlank(logo)) logo = nullSafeString(json, "cover");
            if (isBlank(logo)) logo = nullSafeString(json, "movie_image");
            return logo == null ? "" : logo;
        } catch (Exception _) {
            return "";
        }
    }

    private String normalizeLogoUrl(Account account, String logo) {
        if (isBlank(logo)) {
            return "";
        }
        String value = trimWrappedLogo(logo);
        if (isBlank(value)) {
            return "";
        }
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return value;
        }
        PortalAddress portalAddress = resolvePortalAddress(account);
        if (value.startsWith("//")) {
            return portalAddress.scheme + ":" + value;
        }
        if (value.startsWith("/") && !isBlank(portalAddress.host)) {
            return portalAddress.origin() + value;
        }
        return value;
    }

    private String trimWrappedLogo(String logo) {
        String value = logo.trim().replace("\\/", "/");
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private PortalAddress resolvePortalAddress(Account account) {
        String portal = account != null ? account.getServerPortalUrl() : "";
        String scheme = "https";
        String host = "";
        int port = -1;
        try {
            if (!isBlank(portal)) {
                URI uri = URI.create(portal.trim());
                if (!isBlank(uri.getScheme())) scheme = uri.getScheme();
                if (!isBlank(uri.getHost())) host = uri.getHost();
                port = uri.getPort();
            }
        } catch (Exception _) {
            // Invalid portal URLs should fall back to the default scheme/host values.
        }
        return new PortalAddress(scheme, host, port);
    }

    private record PortalAddress(String scheme, String host, int port) {
        private String origin() {
            return scheme + "://" + host + (port > 0 ? ":" + port : "");
        }
    }

    public List<Channel> censor(List<Channel> channelList) {
        return contentFilterService.filterChannels(channelList);
    }

    private List<Channel> maybeFilterChannels(List<Channel> channels, boolean applyFilter) {
        return applyFilter ? contentFilterService.filterChannels(channels) : channels;
    }

    private List<Channel> dedupeChannels(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return channels == null ? Collections.emptyList() : channels;
        }
        LinkedHashMap<String, Channel> unique = new LinkedHashMap<>();
        for (Channel c : channels) {
            if (c == null) continue;
            String key = String.join("|",
                    StringUtils.isBlank(c.getChannelId()) ? "" : c.getChannelId().trim(),
                    StringUtils.isBlank(c.getCmd()) ? "" : c.getCmd().trim(),
                    StringUtils.isBlank(c.getName()) ? "" : c.getName().trim().toLowerCase()
            );
            unique.putIfAbsent(key, c);
        }
        return new ArrayList<>(unique.values());
    }

    private void resolveLogoIfNeeded(Channel channel) {
        if (channel == null) {
            return;
        }
        String currentLogo = channel.getLogo();
        boolean hasAbsoluteLogo = isNotBlank(currentLogo)
                && (currentLogo.startsWith("http://") || currentLogo.startsWith("https://"));
        if (hasAbsoluteLogo) {
            return;
        }
        String resolved = logoResolverService.resolve(channel.getName(), currentLogo);
        if (isNotBlank(resolved)) {
            channel.setLogo(resolved);
        }
    }

}
