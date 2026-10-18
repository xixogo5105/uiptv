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

    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        budget = ReloadBudget.start();
        startNanos = System.nanoTime();

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
        List<Channel> channels = parseGlobalLiveChannels(account, logger);
        if (!channels.isEmpty()) return channels;
        log(logger, "Global Stalker lookup failed. Trying category pages.");
        Map<String, Channel> collected = new LinkedHashMap<>();
        for (Category category : liveCategories.rawCategories()) {
            String canonical = canonicalCategoryId(category.getCategoryId(),
                    liveCategories.categoryNormalization().canonicalCategoryIdByOriginalId());
            if (isBlank(category.getCategoryId()) || !visibleCategoryIds.contains(canonical)) continue;
            for (Channel channel : fetchCategoryPages(account, category.getCategoryId())) {
                channel.setCategoryId(canonical);
                collected.putIfAbsent(normalizeCaseInsensitiveKey(channel.getChannelId()), channel);
            }
        }
        if (collected.isEmpty()) throw new IllegalStateException("No usable Stalker channels; keeping existing cache.");
        return new ArrayList<>(collected.values());
    }

    private List<Channel> fetchCategoryPages(Account account, String categoryId) {
        Map<String, Channel> channels = new LinkedHashMap<>();
        long expected = -1;
        for (int page = 0; ; page++) {
            com.uiptv.util.ReloadRequestScope.checkCurrent();
            if (budget.expired()) throw new IllegalStateException("Category fetch time limit reached; keeping existing cache.");
            FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(
                    ChannelService.getChannelOrSeriesParams(categoryId, page, itv, null, null), account,
                    HttpUtil.RequestOptions.defaults());
            if (result.transportFailure() || isBlank(result.body())) {
                throw new IllegalStateException("Stalker category page failed; keeping existing cache.");
            }
            JSONObject root = new JSONObject(result.body());
            JSONObject payload = root.optJSONObject("js", root);
            if (payload.has("total_items")) expected = payload.optLong("total_items", -1);
            // Require a valid page and map each row strictly before replacing any cached data.
            if (payload.optJSONArray("data") == null) {
                throw new IllegalStateException("Invalid Stalker category page; keeping existing cache.");
            }
            List<Channel> rows = new ArrayList<>();
            org.json.JSONArray data = payload.getJSONArray("data");
            for (int index = 0; index < data.length(); index++) {
                rows.add(ChannelService.getInstance().parseItvChannel(data.getJSONObject(index)));
            }
            int before = channels.size();
            for (Channel channel : rows) channels.putIfAbsent(normalizeCaseInsensitiveKey(channel.getChannelId()), channel);
            if (expected >= 0 && channels.size() >= expected) break;
            if (rows.isEmpty()) {
                if (page == 0 && expected != 0) continue; // Some portals start at page one.
                if (expected > channels.size()) throw new IllegalStateException("Incomplete Stalker category; keeping existing cache.");
                break;
            }
            // Portals often treat p=0 as p=1. Allow that alias once, but reject an endless repeated page.
            if (channels.size() == before && page != 1) {
                throw new IllegalStateException("Stalker pagination stopped advancing; keeping existing cache.");
            }
        }
        return new ArrayList<>(channels.values());
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
            FetchAPI.FetchResult result = FetchAPI.fetchWithDiagnostics(params, account, HttpUtil.RequestOptions.streamingCatalogue());
            if (result.transportFailure()) {
                logTransportFailure("get_all_channels", result, logger);
                // A failed connection cannot be repaired by changing pagination parameters.
                // An HTTP response (including an empty 200) can be shape-specific, so retain
                // main's bounded compatibility retries under the same request/time limits.
                if (result.statusCode() < 0) {
                    return Collections.emptyList();
                }
                continue;
            }
            if (result.catalogueStream() != null) {
                var stream = result.catalogueStream();
                boolean complete = false;
                try {
                    List<Channel> channels = new com.uiptv.application.StalkerCatalogueReader().read(
                            new java.io.InputStreamReader(stream.bodyStream(), java.nio.charset.StandardCharsets.UTF_8));
                    complete = true;
                    if (!channels.isEmpty()) return channels;
                } catch (Exception failure) {
                    com.uiptv.util.ReloadRequestScope.checkCurrent();
                    log(logger, "Unable to read bulk channel response: " + failure.getMessage());
                } finally {
                    // Abort before closing a rejected body; closing first may drain the entire
                    // remaining response. This also releases the deadline after JVM errors.
                    try { if (complete) stream.close(); else stream.abort(); }
                    catch (java.io.IOException _) { /* The original failure is the actionable result. */ }
                }
                continue;
            }
            String json = result.body();
            if (isBlank(json)) {
                continue;
            }
            try {
                List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);
                if (!channels.isEmpty()) {
                    // Portal totals may include duplicate or filtered rows. Match the established
                    // bulk contract: usable parsed channels are sufficient; totals are advisory.
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
                + (isNotBlank(result.endpoint()) ? " [" + com.uiptv.util.HttpUtil.sanitizeUrlForLog(result.endpoint()) + "]" : "");
        // Surface in the per-account reload panel as well as the global log: "no channels loaded"
        // is otherwise indistinguishable from a provider that is simply empty or unreachable.
        log(logger, message);
        AppLog.addWarningLog(StalkerPortalCacheReloader.class, message);
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
        } catch (com.uiptv.util.ReloadRequestScope.LimitExceeded e) { throw e;
        } catch (Exception _) {
            log(logger, "Bulk Stalker lookup unavailable. Trying category pages.");
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
