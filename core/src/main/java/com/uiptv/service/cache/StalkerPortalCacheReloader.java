package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.HandshakeService;
import com.uiptv.shared.Pagination;
import com.uiptv.util.AppLog;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONObject;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class StalkerPortalCacheReloader extends AbstractAccountCacheReloader {

    /**
     * Per-reload wall-clock budget. Reloaders are created per request by
     * {@link AccountCacheReloaderFactory}, so this field is scoped to a single account reload and
     * never shared across accounts.
     */
    private ReloadBudget budget = ReloadBudget.start();
    private long startNanos = System.nanoTime();
    /** Set when get_all_channels failed at the transport level, not just with an empty payload. */
    private boolean globalChannelsUnreachable = false;

    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        budget = ReloadBudget.start();
        startNanos = System.nanoTime();
        globalChannelsUnreachable = false;

        boolean applyCategoryCensoring = shouldApplyCategoryCensoring(account);
        boolean applyChannelCensoring = shouldApplyChannelCensoring(account);
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            log(logger, "Handshake failed for: " + account.getAccountName());
            return;
        }

        if (account.getAction() == itv) {
            CensoringSummary summary = reloadLive(account, logger, applyCategoryCensoring, applyChannelCensoring);
            if (!budget.expired()) {
                summary = summary.add(cacheVodAndSeriesCategoriesOnly(account, logger));
            } else {
                logBudgetExhausted("VOD/Series cache", logger);
            }
            logCensoringSummary(logger, summary);
            return;
        }

        if (account.getAction() == vod || account.getAction() == series) {
            List<Category> rawCategories = CategoryService.getInstance().get(account, false, logger);
            if (rawCategories == null) {
                rawCategories = List.of();
            }
            List<Category> categories = applyCategoryCensoring(rawCategories, applyCategoryCensoring);
            saveVodOrSeriesCategories(account, categories);
            log(logger, "Found Categories " + categories.size());
            log(logger, categories.size() + " Categories & 0 Channels saved Successfully \u2713");
            logCensoringSummary(logger, CensoringSummary.empty()
                    .addCategories(censoredItemCount(rawCategories.size(), categories.size(), applyCategoryCensoring)));
        }
    }

    private CensoringSummary reloadLive(Account account, LoggerCallback logger,
                                        boolean applyCategoryCensoring, boolean applyChannelCensoring) {
        LiveCategories liveCategories = loadVisibleLiveCategories(account, logger, applyCategoryCensoring);
        if (liveCategories.stopReload()) {
            return liveCategories.summary();
        }

        List<Category> officialCategories = liveCategories.categories();
        Set<String> knownCategoryIds = categoryIds(liveCategories.categoryNormalization().categories());
        Set<String> visibleCategoryIds = categoryIds(officialCategories);
        // When categories had to be derived from the channel payload we already hold those
        // channels; reuse them instead of issuing a second get_all_channels round trip.
        List<Channel> prefetched = liveCategories.prefetchedChannels();
        List<Channel> rawChannels = (prefetched != null && !prefetched.isEmpty())
                ? prefetched
                : loadStalkerLiveChannels(account, liveCategories, visibleCategoryIds, logger);
        if (rawChannels.isEmpty()) {
            return liveCategories.summary();
        }

        CensoringSummary summary = liveCategories.summary();
        List<Channel> allChannels = applyChannelCensoring(rawChannels, applyChannelCensoring);

        if (liveCategories.derivedFromChannels()) {
            // Categories derived from tv_genre_id carry no title when the portal omits one, so
            // title-based category censoring cannot match them. Fall back to the channels: a
            // derived category whose channels were all removed by channel censoring is itself
            // censored and must not be published.
            DerivedCategoryCascade cascade = dropFullyCensoredDerivedCategories(officialCategories,
                    rawChannels, allChannels, liveCategories.categoryNormalization());
            officialCategories = cascade.categories();
            summary = summary.addCategories(cascade.removedCount());
            if (cascade.removedCount() > 0) {
                log(logger, "Removed " + cascade.removedCount()
                        + " derived categories whose channels were all filtered out");
            }
            if (officialCategories.isEmpty()) {
                logKeepingExistingCacheAfterFullCensoring(logger, "categories");
                return summary;
            }
            knownCategoryIds = categoryIds(officialCategories);
            visibleCategoryIds = categoryIds(officialCategories);
        }

        allChannels = retainChannelsForVisibleCategories(allChannels, knownCategoryIds, visibleCategoryIds,
                liveCategories.categoryNormalization().canonicalCategoryIdByOriginalId());
        summary = summary.addChannels(censoredItemCount(rawChannels.size(), allChannels.size(),
                applyCategoryCensoring || applyChannelCensoring));
        if (handleEmptyLiveChannels(account, officialCategories, rawChannels, allChannels,
                applyCategoryCensoring || applyChannelCensoring, logger)) {
            return summary;
        }

        saveStalkerLiveCache(account, officialCategories, allChannels, liveCategories.categoryNormalization(), logger);
        return summary;
    }

    /**
     * Removes derived categories whose every channel was removed by channel censoring.
     * <p>
     * A category is only a candidate when it had channels to begin with: a synthetic
     * Uncategorized entry is not present in any channel's {@code tv_genre_id} and must survive.
     */
    private DerivedCategoryCascade dropFullyCensoredDerivedCategories(List<Category> categories,
                                                                     List<Channel> rawChannels,
                                                                     List<Channel> survivingChannels,
                                                                     CategoryNormalization normalization) {
        if (categories == null || categories.isEmpty()) {
            return new DerivedCategoryCascade(categories == null ? List.of() : categories, 0);
        }

        Set<String> categoryIdsBefore = collectCategoryIds(rawChannels, normalization);
        Set<String> categoryIdsAfter = collectCategoryIds(survivingChannels, normalization);

        List<Category> kept = new ArrayList<>();
        int removed = 0;
        for (Category category : categories) {
            if (category == null) {
                continue;
            }
            String categoryId = canonicalCategoryId(category.getCategoryId(),
                    normalization.canonicalCategoryIdByOriginalId());
            boolean hadChannels = categoryIdsBefore.contains(categoryId);
            boolean hasChannels = categoryIdsAfter.contains(categoryId);
            if (hadChannels && !hasChannels) {
                removed++;
                continue;
            }
            kept.add(category);
        }
        return new DerivedCategoryCascade(kept, removed);
    }

    private Set<String> collectCategoryIds(List<Channel> channels, CategoryNormalization normalization) {
        Set<String> ids = new HashSet<>();
        if (channels == null) {
            return ids;
        }
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            String categoryId = canonicalCategoryId(channel.getCategoryId(),
                    normalization.canonicalCategoryIdByOriginalId());
            if (isNotBlank(categoryId)) {
                ids.add(categoryId);
            }
        }
        return ids;
    }

    private LiveCategories loadVisibleLiveCategories(Account account, LoggerCallback logger, boolean applyCategoryCensoring) {
        List<Category> rawCategories = loadOfficialLiveCategories(account, logger);
        List<Channel> prefetchedChannels = null;
        boolean derivedFromChannels = false;

        if (rawCategories.isEmpty()) {
            // get_genres returned nothing usable. Stalker's get_all_channels still carries a
            // tv_genre_id on every channel, so the category list can be derived from the channel
            // payload rather than aborting the whole live reload and leaving the cache empty.
            log(logger, "get_genres returned no categories. Deriving categories from get_all_channels...");
            prefetchedChannels = parseGlobalLiveChannels(account, logger);
            List<Category> derived = deriveCategoriesFromChannels(prefetchedChannels);
            if (derived.isEmpty()) {
                if (prefetchedChannels.isEmpty()) {
                    log(logger, "No categories found. Keeping existing cache.");
                    return LiveCategories.stop(rawCategories, normalizeCategoriesByTitle(rawCategories),
                            List.of(), CensoringSummary.empty(), null, false);
                }
                // The portal names no genres at all. Publishing raw provider ids is not acceptable,
                // so keep every channel under a single Uncategorized category rather than
                // discarding a fetch that already succeeded.
                log(logger, "Portal returned no category names. Grouping " + prefetchedChannels.size()
                        + " channels under " + UNCATEGORIZED_NAME + ".");
                derived = List.of(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
            } else {
                int unnamed = countUnnamedGenreIds(prefetchedChannels, derived);
                if (unnamed > 0) {
                    log(logger, unnamed + " genre ids had no category name; their channels will be grouped as "
                            + UNCATEGORIZED_NAME);
                }
            }
            log(logger, "Derived " + derived.size() + " categories from " + prefetchedChannels.size() + " channels");
            rawCategories = derived;
            derivedFromChannels = true;
        }

        CategoryNormalization categoryNormalization = normalizeCategoriesByTitle(rawCategories);
        List<Category> allCategories = categoryNormalization.categories();
        CensoringSummary summary = CensoringSummary.empty();
        if (allCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return LiveCategories.stop(rawCategories, categoryNormalization, List.of(), summary,
                    prefetchedChannels, derivedFromChannels);
        }

        List<Category> categories = applyCategoryCensoring(allCategories, applyCategoryCensoring);
        summary = summary.addCategories(censoredItemCount(allCategories.size(), categories.size(), applyCategoryCensoring));
        if (categories.isEmpty()) {
            handleEmptyLiveCategories(account, allCategories.size(), categories, applyCategoryCensoring, logger);
            return LiveCategories.stop(rawCategories, categoryNormalization, categories, summary,
                    prefetchedChannels, derivedFromChannels);
        }
        log(logger, "Found Categories " + categories.size());
        return LiveCategories.continueWith(rawCategories, categoryNormalization, categories, summary,
                prefetchedChannels, derivedFromChannels);
    }

    /**
     * Builds categories from the distinct {@code tv_genre_id} values present in a
     * {@code get_all_channels} payload.
     * <p>
     * Only genre ids the portal actually names are published as categories. A genre id with no
     * resolvable name is deliberately skipped rather than shown as a raw provider id; its channels
     * then fall through the normal orphan path and land in the Uncategorized category, so no
     * channel is lost and the category list stays readable.
     */
    private List<Category> deriveCategoriesFromChannels(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        Map<String, String> titleByGenreId = new LinkedHashMap<>();
        for (Channel channel : channels) {
            if (channel == null || isBlank(channel.getCategoryId())) {
                continue;
            }
            String genreId = channel.getCategoryId().trim();
            String title = resolveGenreTitle(channel);
            if (isNotBlank(title)) {
                titleByGenreId.putIfAbsent(genreId, title.trim());
            }
        }
        List<Category> derived = new ArrayList<>();
        for (Map.Entry<String, String> entry : titleByGenreId.entrySet()) {
            derived.add(new Category(entry.getKey(), entry.getValue(), null, false, 0));
        }
        return derived;
    }

    private String resolveGenreTitle(Channel channel) {
        for (String key : List.of("category", "category_name", "genre", "genre_name", "tv_genre")) {
            String title = genreTitleFromChannelJson(channel, key);
            if (isNotBlank(title)) {
                return title;
            }
        }
        return null;
    }

    private String genreTitleFromChannelJson(Channel channel, String key) {
        if (channel == null || isBlank(channel.getExtraJson())) {
            return null;
        }
        try {
            Object value = new JSONObject(channel.getExtraJson()).opt(key);
            if (value == null || JSONObject.NULL.equals(value)) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
        } catch (Exception _) {
            return null;
        }
    }

    /** Counts distinct genre ids seen in the channel payload that produced no named category. */
    private int countUnnamedGenreIds(List<Channel> channels, List<Category> derived) {
        if (channels == null || channels.isEmpty()) {
            return 0;
        }
        Set<String> named = new HashSet<>();
        for (Category category : derived) {
            if (category != null && isNotBlank(category.getCategoryId())) {
                named.add(category.getCategoryId().trim());
            }
        }
        Set<String> seen = new HashSet<>();
        for (Channel channel : channels) {
            if (channel == null || isBlank(channel.getCategoryId())) {
                continue;
            }
            seen.add(channel.getCategoryId().trim());
        }
        int unnamed = 0;
        for (String genreId : seen) {
            if (!named.contains(genreId)) {
                unnamed++;
            }
        }
        return unnamed;
    }

    private void handleEmptyLiveCategories(Account account, int rawCategoryCount, List<Category> categories,
                                           boolean applyCategoryCensoring, LoggerCallback logger) {
        if (wasEverythingRemovedByActiveCensoring(rawCategoryCount, categories.size(), applyCategoryCensoring)) {
            logKeepingExistingCacheAfterFullCensoring(logger, "categories");
            return;
        }
        log(logger, "Found Categories 0");
        saveLiveCacheWithNoChannels(account, categories, logger,
                "All categories removed by active censoring. Clearing existing cache.");
    }

    private List<Channel> loadStalkerLiveChannels(Account account, LiveCategories liveCategories,
                                                  Set<String> visibleCategoryIds, LoggerCallback logger) {
        globalChannelsUnreachable = false;
        List<Channel> rawChannels = parseGlobalLiveChannels(account, logger);
        if (!rawChannels.isEmpty()) {
            return rawChannels;
        }

        if (globalChannelsUnreachable) {
            // The endpoint is not serving at all. Paging every category would issue several
            // requests per category, each paying the same full response timeout, so skip it.
            log(logger, "Skipping last-resort category-by-category fetch: the portal endpoint did not respond.");
            log(logger, "No channels found. Keeping existing cache.");
            return List.of();
        }

        log(logger, "Global Stalker get_all_channels failed. Trying last-resort category-by-category fetch.");
        List<Category> fallbackCategories = categoriesMatchingVisibleIds(liveCategories.rawCategories(), visibleCategoryIds,
                liveCategories.categoryNormalization());
        List<Channel> fallbackChannels = fetchAllChannelsByCategoryLastResort(account, fallbackCategories,
                liveCategories.categoryNormalization(), logger);
        if (fallbackChannels.isEmpty()) {
            log(logger, "No channels found. Keeping existing cache.");
        } else {
            log(logger, "Last-resort fetch succeeded. Collected " + fallbackChannels.size() + " channels.");
        }
        return fallbackChannels;
    }

    private boolean handleEmptyLiveChannels(Account account, List<Category> categories, List<Channel> rawChannels,
                                            List<Channel> allChannels, boolean applyCensoring, LoggerCallback logger) {
        if (!allChannels.isEmpty()) {
            return false;
        }
        if (wasEverythingRemovedByActiveCensoring(rawChannels.size(), allChannels.size(), applyCensoring)) {
            logKeepingExistingCacheAfterFullCensoring(logger, "channels");
        } else {
            saveLiveCacheWithNoChannels(account, categories, logger,
                    "All channels removed by active censoring. Clearing existing cache.");
        }
        return true;
    }

    private void saveStalkerLiveCache(Account account, List<Category> officialCategories, List<Channel> allChannels,
                                      CategoryNormalization categoryNormalization, LoggerCallback logger) {
        ChannelGrouping grouping = groupChannelsByCategory(allChannels, officialCategories,
                categoryNormalization.canonicalCategoryIdByOriginalId());
        log(logger, "Found Channels " + allChannels.size() + ". Found " + grouping.orphanedChannels.size() + " Orphaned channels.");

        clearCache(account);

        CategoryDb.get().saveAll(categoriesWithUncategorizedIfNeeded(officialCategories, grouping.orphanedChannels), account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        Map<String, Category> savedCategoryMap = savedCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));

        for (Map.Entry<String, List<Channel>> entry : grouping.matchedChannelsByCatId.entrySet()) {
            Category category = savedCategoryMap.get(entry.getKey());
            if (category != null && category.getDbId() != null) {
                ChannelDb.get().saveAll(entry.getValue(), category.getDbId(), account);
            }
        }

        if (!grouping.orphanedChannels.isEmpty()) {
            Category uncategorizedCategory = findUncategorizedCategory(savedCategoryMap);
            if (uncategorizedCategory != null && uncategorizedCategory.getDbId() != null) {
                ChannelDb.get().saveAll(grouping.orphanedChannels, uncategorizedCategory.getDbId(), account);
            }
        }

        log(logger, savedCategories.size() + " Categories & " + allChannels.size() + " Channels saved Successfully \u2713");
    }

    private List<Category> categoriesMatchingVisibleIds(List<Category> rawCategories, Set<String> visibleCategoryIds,
                                                        CategoryNormalization categoryNormalization) {
        if (rawCategories == null || rawCategories.isEmpty()) {
            return List.of();
        }
        return rawCategories.stream()
                .filter(category -> category != null
                        && (isBlank(category.getCategoryId())
                        || visibleCategoryIds.contains(canonicalCategoryId(category.getCategoryId(),
                        categoryNormalization.canonicalCategoryIdByOriginalId()))))
                .toList();
    }

    private void saveLiveCacheWithNoChannels(Account account, List<Category> categories, LoggerCallback logger, String reason) {
        log(logger, reason);
        clearCache(account);
        if (categories != null && !categories.isEmpty()) {
            CategoryDb.get().saveAll(categories, account);
        }
        log(logger, "Found Channels 0. Found 0 Orphaned channels.");
        log(logger, (categories == null ? 0 : categories.size()) + " Categories & 0 Channels saved Successfully \u2713");
    }

    private List<Channel> fetchAllStalkerChannels(Account account, LoggerCallback logger) {
        List<Map<String, String>> attempts = List.of(
                getAllChannelsParams(null, null),
                getAllChannelsParams(0, 99999),
                getAllChannelsParams(1, 99999)
        );

        for (Map<String, String> params : attempts) {
            if (budget.expired()) {
                logBudgetExhausted("get_all_channels", logger);
                return Collections.emptyList();
            }
            FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(params, account, HttpUtil.RequestOptions.defaults());
            if (result.transportFailure()) {
                // A transport-level failure means the endpoint is not serving. The remaining
                // parameter shapes would fail the same way, each burning a full response timeout,
                // so stop here rather than paying the timeout twice more.
                logTransportFailure("get_all_channels", result, logger);
                if (result.statusCode() < 0) {
                    globalChannelsUnreachable = true;
                    return Collections.emptyList();
                }
            }
            String json = result.body();
            if (isBlank(json)) {
                continue;
            }
            try {
                List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);
                if (!channels.isEmpty()) {
                    return channels;
                }
            } catch (Exception _) {
                // Ignore non-usable JSON variants and keep trying the remaining fallback shapes.
            }
        }
        return Collections.emptyList();
    }

    private void logBudgetExhausted(String operation, LoggerCallback logger) {
        log(logger, "Reload time budget exhausted during " + operation
                + " after " + elapsedSeconds() + "s. Stopping this account and keeping the existing cache.");
    }

    private long elapsedSeconds() {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000_000L);
    }

    private void logTransportFailure(String operation, FetchAPI.FetchResult result, LoggerCallback logger) {
        String message = "Stalker " + operation + " request failed (status=" + result.statusCode() + "): "
                + result.failureReason()
                + (isNotBlank(result.endpoint()) ? " [" + result.endpoint() + "]" : "");
        // Surface in the per-account reload panel as well as the global log: "no channels loaded"
        // is otherwise indistinguishable from a provider that is simply empty or unreachable.
        log(logger, message);
        AppLog.addWarningLog(StalkerPortalCacheReloader.class, message);
    }

    private List<Channel> fetchAllChannelsByCategoryLastResort(Account account, List<Category> categories,
                                                               CategoryNormalization categoryNormalization,
                                                               LoggerCallback logger) {
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>(uniqueChannels.values());
        }

        int limit = lastResortCategoryLimit();
        int processed = 0;
        int skipped = 0;
        boolean hitCategoryLimit = false;
        for (Category category : categories) {
            if (category == null || isBlank(category.getCategoryId())) {
                continue;
            }
            if (processed >= limit) {
                skipped++;
                hitCategoryLimit = true;
                continue;
            }
            // Each category costs several requests, so honour the account budget between them.
            if (budget.expired()) {
                logBudgetExhausted("last-resort category fetch", logger);
                skipped += countRemainingNamedCategories(categories, processed);
                break;
            }
            processed++;

            List<Channel> channelsForCategory = fetchStalkerCategoryChannelsLastResort(account, category.getCategoryId(), logger);
            for (Channel channel : channelsForCategory) {
                if (channel == null || isBlank(channel.getChannelId())) {
                    continue;
                }
                String canonicalCategoryId = canonicalCategoryId(category.getCategoryId(), categoryNormalization.canonicalCategoryIdByOriginalId());
                if (isBlank(channel.getCategoryId())) {
                    channel.setCategoryId(canonicalCategoryId);
                } else {
                    channel.setCategoryId(canonicalCategoryId(channel.getCategoryId(), categoryNormalization.canonicalCategoryIdByOriginalId()));
                }
                uniqueChannels.putIfAbsent(normalizeCaseInsensitiveKey(channel.getChannelId()), channel);
            }
        }
        if (skipped > 0) {
            log(logger, "Last-resort fetch covered " + processed + " of " + (processed + skipped)
                    + " categories; " + skipped + " were not fetched"
                    + (hitCategoryLimit ? " (reached the safety limit of " + limit + ")" : " (time budget reached)")
                    + ". Those categories will be cached empty.");
        }
        return new ArrayList<>(uniqueChannels.values());
    }

    private int countRemainingNamedCategories(List<Category> categories, int alreadyProcessed) {
        int named = 0;
        for (Category category : categories) {
            if (category != null && isNotBlank(category.getCategoryId())) {
                named++;
            }
        }
        return Math.max(0, named - alreadyProcessed);
    }

    /**
     * Runaway guard for the last-resort fan-out, not the primary bound.
     * <p>
     * The per-account {@link ReloadBudget} is what actually limits this loop: against a stalling
     * endpoint a single category can already cost several response timeouts, so the budget expires
     * long before any category count is reached. This limit only matters if the budget is disabled
     * ({@code uiptv.reload.budget.seconds=0}), so it is set high enough that a responsive portal
     * with many categories is never truncated.
     */
    private static int lastResortCategoryLimit() {
        int configured = Integer.getInteger("uiptv.reload.lastresort.category.limit", 500);
        return configured > 0 ? configured : Integer.MAX_VALUE;
    }

    private List<Channel> fetchStalkerCategoryChannelsLastResort(Account account, String categoryId, LoggerCallback logger) {
        List<Channel> channels = fetchStalkerCategoryChannelsFromPage(account, categoryId, 0, logger);
        if (!channels.isEmpty()) {
            return channels;
        }
        return fetchStalkerCategoryChannelsFromPage(account, categoryId, 1, logger);
    }

    @SuppressWarnings("java:S135")
    private List<Channel> fetchStalkerCategoryChannelsFromPage(Account account, String categoryId, int startPage, LoggerCallback logger) {
        List<Channel> aggregated = new ArrayList<>();
        int maxAdditionalPages = 2;

        for (int page = startPage; page <= startPage + maxAdditionalPages; page++) {
            String json = FetchAPI.fetch(ChannelService.getChannelOrSeriesParams(categoryId, page, itv, null, null), account);
            if (isBlank(json)) {
                break;
            }

            try {
                if (page == startPage) {
                    maxAdditionalPages = resolveMaxAdditionalPages(json, maxAdditionalPages);
                }

                List<Channel> pageChannels = ChannelService.getInstance().parseItvChannels(json, false);
                if (pageChannels.isEmpty()) {
                    break;
                }
                aggregated.addAll(pageChannels);
            } catch (Exception e) {
                log(logger, "Last-resort fetch failed for category " + categoryId + " at page " + page + ": " + e.getMessage());
                break;
            }
        }

        return dedupeChannels(aggregated);
    }

    private List<Category> loadOfficialLiveCategories(Account account, LoggerCallback logger) {
        FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(getCategoryParams(account.getAction()), account,
                HttpUtil.RequestOptions.defaults());
        if (result.transportFailure()) {
            logTransportFailure("get_genres", result, logger);
        }
        String jsonCategories = result.body();
        return CategoryService.getInstance().parseCategories(jsonCategories, false).stream()
                .filter(c -> !CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle()))
                .toList();
    }

    private List<Channel> parseGlobalLiveChannels(Account account, LoggerCallback logger) {
        try {
            return fetchAllStalkerChannels(account, logger);
        } catch (Exception e) {
            log(logger, "Failed to parse channels from get_all_channels: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private ChannelGrouping groupChannelsByCategory(List<Channel> allChannels, List<Category> officialCategories,
                                                    Map<String, String> canonicalCategoryIdByOriginalId) {
        Map<String, Category> officialCategoryMap = officialCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (c1, c2) -> c1));
        Map<String, List<Channel>> matchedChannelsByCatId = new HashMap<>();
        List<Channel> orphanedChannels = new ArrayList<>();
        for (Channel channel : allChannels) {
            String categoryId = canonicalCategoryId(channel.getCategoryId(), canonicalCategoryIdByOriginalId);
            if (isNotBlank(categoryId) && officialCategoryMap.containsKey(categoryId)) {
                matchedChannelsByCatId.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(channel);
            } else {
                orphanedChannels.add(channel);
            }
        }
        return new ChannelGrouping(matchedChannelsByCatId, orphanedChannels);
    }

    private List<Category> categoriesWithUncategorizedIfNeeded(List<Category> officialCategories, List<Channel> orphanedChannels) {
        List<Category> categoriesToSave = new ArrayList<>(officialCategories);
        if (!orphanedChannels.isEmpty() && officialCategories.stream().noneMatch(this::isUncategorizedCategory)) {
            categoriesToSave.add(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
        }
        return categoriesToSave;
    }

    private boolean isUncategorizedCategory(Category category) {
        return UNCATEGORIZED_ID.equals(category.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(category.getTitle());
    }

    private Category findUncategorizedCategory(Map<String, Category> savedCategoryMap) {
        return savedCategoryMap.values().stream()
                .filter(this::isUncategorizedCategory)
                .findFirst()
                .orElse(null);
    }

    private int resolveMaxAdditionalPages(String json, int defaultValue) {
        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);
        if (pagination == null) {
            return defaultValue;
        }
        return Math.max(pagination.getPageCount() + 1, 2);
    }

    private List<Channel> dedupeChannels(List<Channel> aggregated) {
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Channel channel : aggregated) {
            if (channel == null || isBlank(channel.getChannelId())) {
                continue;
            }
            uniqueChannels.putIfAbsent(normalizeCaseInsensitiveKey(channel.getChannelId()), channel);
        }
        return new ArrayList<>(uniqueChannels.values());
    }

    private record ChannelGrouping(Map<String, List<Channel>> matchedChannelsByCatId, List<Channel> orphanedChannels) {
    }

    private record LiveCategories(List<Category> rawCategories,
                                  CategoryNormalization categoryNormalization,
                                  List<Category> categories,
                                  CensoringSummary summary,
                                  boolean stopReload,
                                  List<Channel> prefetchedChannels,
                                  boolean derivedFromChannels) {
        static LiveCategories stop(List<Category> rawCategories, CategoryNormalization categoryNormalization,
                                   List<Category> categories, CensoringSummary summary,
                                   List<Channel> prefetchedChannels, boolean derivedFromChannels) {
            return new LiveCategories(rawCategories, categoryNormalization, categories, summary, true,
                    prefetchedChannels, derivedFromChannels);
        }

        static LiveCategories continueWith(List<Category> rawCategories, CategoryNormalization categoryNormalization,
                                           List<Category> categories, CensoringSummary summary,
                                           List<Channel> prefetchedChannels, boolean derivedFromChannels) {
            return new LiveCategories(rawCategories, categoryNormalization, categories, summary, false,
                    prefetchedChannels, derivedFromChannels);
        }
    }

    private record DerivedCategoryCascade(List<Category> categories, int removedCount) {
    }
}
