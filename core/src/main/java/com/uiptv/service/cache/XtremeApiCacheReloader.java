package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;
import com.uiptv.util.XtremeApiParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isNotBlank;

public class XtremeApiCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        boolean applyCategoryCensoring = shouldApplyCategoryCensoring();
        boolean applyChannelCensoring = shouldApplyChannelCensoring();
        CensoringSummary summary;
        if (isVodOrSeriesAction(account)) {
            summary = reloadVodOrSeriesCategories(account, logger, applyCategoryCensoring);
        } else {
            summary = reloadLive(account, logger, applyCategoryCensoring, applyChannelCensoring);
        }
        logCensoringSummary(logger, summary);
    }

    private CensoringSummary reloadLive(Account account, LoggerCallback logger,
                                        boolean applyCategoryCensoring, boolean applyChannelCensoring) {
        List<Category> rawCategories = loadLiveCategories(account, logger);
        CategoryNormalization categoryNormalization = normalizeCategoriesByTitle(rawCategories);
        List<Category> allCategories = categoryNormalization.categories();
        CensoringSummary summary = CensoringSummary.empty();
        if (allCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return summary;
        }

        List<Category> categories = applyCategoryCensoring(allCategories, applyCategoryCensoring);
        summary = summary.addCategories(censoredItemCount(allCategories.size(), categories.size(), applyCategoryCensoring));
        if (categories.isEmpty()) {
            if (wasEverythingRemovedByActiveCensoring(allCategories.size(), categories.size(), applyCategoryCensoring)) {
                logKeepingExistingCacheAfterFullCensoring(logger, "categories");
                return summary;
            }
            log(logger, "Found Categories 0");
            saveLiveCacheWithNoChannels(account, categories, logger,
                    "All categories removed by active censoring. Clearing existing cache.");
            return summary;
        }
        log(logger, "Found Categories " + categories.size());

        Set<String> knownCategoryIds = categoryIds(allCategories);
        Set<String> visibleCategoryIds = categoryIds(categories);
        GlobalLookupContext globalLookupContext = new GlobalLookupContext(categories,
                categoryNormalization.canonicalCategoryIdByOriginalId(), knownCategoryIds, visibleCategoryIds,
                applyCategoryCensoring, applyChannelCensoring);
        LiveReloadResult globalLookupResult = account.getAction() == itv
                ? reloadLiveWithGlobalLookup(account, globalLookupContext, logger)
                : LiveReloadResult.notHandled();
        summary = summary.add(globalLookupResult.censoringSummary());
        if (globalLookupResult.handled()) {
            return summary.add(cacheVodAndSeriesCategoriesOnly(account, logger));
        }

        List<Category> categoriesToFetch = categoriesMatchingVisibleIds(rawCategories, visibleCategoryIds, categoryNormalization);
        CategoryFetchResult fetchResult = fetchChannelsByCategory(account, categoriesToFetch, categoryNormalization,
                applyChannelCensoring, logger);
        summary = summary.addChannels(censoredItemCount(fetchResult.rawChannels, fetchResult.totalChannels,
                applyChannelCensoring));
        if (!categoriesToFetch.isEmpty() && fetchResult.failedCategories >= categoriesToFetch.size()) {
            throw new IllegalStateException("All category channel requests failed.");
        }
        if (fetchResult.totalChannels == 0) {
            if (fetchResult.rawChannels > 0) {
                if (wasEverythingRemovedByActiveCensoring(fetchResult.rawChannels, fetchResult.totalChannels,
                        applyChannelCensoring)) {
                    logKeepingExistingCacheAfterFullCensoring(logger, "channels");
                    return summary.add(cacheVodAndSeriesCategoriesOnly(account, logger));
                }
                saveLiveCacheWithNoChannels(account, categories, logger,
                        "All channels removed by active censoring. Clearing existing cache.");
                return summary.add(cacheVodAndSeriesCategoriesOnly(account, logger));
            }
            if (fetchResult.failedCategories > 0) {
                throw new IllegalStateException("No usable channels loaded after category fetch failures.");
            }
            log(logger, "No channels found in any category. Keeping existing cache.");
            return summary;
        }
        log(logger, "Found Channels " + fetchResult.totalChannels + ". Found 0 Orphaned channels.");

        clearCache(account);
        CategoryDb.get().saveAll(categories, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        for (Category savedCat : savedCategories) {
            List<Channel> channels = fetchResult.channelsByCategory.get(savedCat.getCategoryId());
            if (channels != null && !channels.isEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.getDbId(), account);
            }
        }
        log(logger, savedCategories.size() + " Categories & " + fetchResult.totalChannels + " Channels saved Successfully \u2713");
        return summary.add(cacheVodAndSeriesCategoriesOnly(account, logger));
    }

    private LiveReloadResult reloadLiveWithGlobalLookup(Account account, GlobalLookupContext context,
                                                        LoggerCallback logger) {
        List<Channel> rawChannels = fetchAllChannelsOrLog(account, logger);
        if (rawChannels == null || rawChannels.isEmpty()) {
            return LiveReloadResult.notHandled();
        }

        if (!hasCategoryAssignments(rawChannels)) {
            log(logger, "Global Xtreme channel lookup returned uncategorized rows only. Falling back to category fetch.");
            return LiveReloadResult.notHandled();
        }

        List<Channel> allChannels = applyChannelCensoring(rawChannels, context.applyChannelCensoring());
        allChannels = retainChannelsForVisibleCategories(allChannels, context.knownCategoryIds(), context.visibleCategoryIds(),
                context.canonicalCategoryIdByOriginalId());
        CensoringSummary summary = CensoringSummary.empty()
                .addChannels(censoredItemCount(rawChannels.size(), allChannels.size(),
                        context.applyCategoryCensoring() || context.applyChannelCensoring()));
        if (allChannels.isEmpty()) {
            if (wasEverythingRemovedByActiveCensoring(rawChannels.size(), allChannels.size(),
                    context.applyCategoryCensoring() || context.applyChannelCensoring())) {
                logKeepingExistingCacheAfterFullCensoring(logger, "channels");
                return LiveReloadResult.handled(summary);
            }
            saveLiveCacheWithNoChannels(account, context.categories(), logger,
                    "All channels removed by active censoring. Clearing existing cache.");
            return LiveReloadResult.handled(summary);
        }

        ChannelGrouping grouping = groupChannelsByKnownCategory(allChannels, context.categories(),
                context.canonicalCategoryIdByOriginalId());

        log(logger, "Found Channels " + allChannels.size() + ". Found " + grouping.orphaned.size() + " Orphaned channels.");
        clearCache(account);

        CategoryDb.get().saveAll(categoriesWithUncategorizedIfNeeded(context.categories(), grouping.orphaned), account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        Map<String, Category> savedByApiId = savedCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (a, b) -> a));

        for (Map.Entry<String, List<Channel>> entry : grouping.matchedByCategory.entrySet()) {
            Category category = savedByApiId.get(entry.getKey());
            if (category != null && category.getDbId() != null) {
                ChannelDb.get().saveAll(entry.getValue(), category.getDbId(), account);
            }
        }

        if (!grouping.orphaned.isEmpty()) {
            Category uncategorized = findUncategorizedCategory(savedByApiId);
            if (uncategorized != null && uncategorized.getDbId() != null) {
                ChannelDb.get().saveAll(grouping.orphaned, uncategorized.getDbId(), account);
            }
        }

        log(logger, savedCategories.size() + " Categories & " + allChannels.size() + " Channels saved Successfully \u2713");
        return LiveReloadResult.handled(summary);
    }

    private boolean isVodOrSeriesAction(Account account) {
        return account.getAction() == vod || account.getAction() == series;
    }

    private CensoringSummary reloadVodOrSeriesCategories(Account account, LoggerCallback logger, boolean applyCategoryCensoring) {
        List<Category> rawCategories = CategoryService.getInstance().get(account, false, logger);
        if (rawCategories == null) {
            rawCategories = List.of();
        }
        List<Category> categories = applyCategoryCensoring(rawCategories, applyCategoryCensoring);
        saveVodOrSeriesCategories(account, categories);
        log(logger, "Found Categories " + categories.size());
        log(logger, categories.size() + " Categories saved Successfully \u2713");
        return CensoringSummary.empty()
                .addCategories(censoredItemCount(rawCategories.size(), categories.size(), applyCategoryCensoring));
    }

    private List<Category> loadLiveCategories(Account account, LoggerCallback logger) {
        return CategoryService.getInstance().get(account, false, logger).stream()
                .filter(c -> !CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle()))
                .toList();
    }

    private CategoryFetchResult fetchChannelsByCategory(Account account, List<Category> rawCategories,
                                                        CategoryNormalization categoryNormalization,
                                                        boolean applyChannelCensoring, LoggerCallback logger) {
        Map<String, List<Channel>> channelsMap = new HashMap<>();
        int totalChannels = 0;
        int rawChannels = 0;
        int failedCategories = 0;
        for (Category category : rawCategories) {
            try {
                List<Channel> channels = XtremeApiParser.parseChannels(category.getCategoryId(), account);
                rawChannels += channels.size();
                channels = applyChannelCensoring(channels, applyChannelCensoring);
                if (!channels.isEmpty()) {
                    String canonicalCategoryId = canonicalCategoryId(category.getCategoryId(), categoryNormalization.canonicalCategoryIdByOriginalId());
                    List<Channel> mergedChannels = mergeChannelsCaseInsensitive(channelsMap.get(canonicalCategoryId), channels);
                    totalChannels += mergedChannels.size() - (channelsMap.containsKey(canonicalCategoryId) ? channelsMap.get(canonicalCategoryId).size() : 0);
                    channelsMap.put(canonicalCategoryId, mergedChannels);
                }
            } catch (Exception e) {
                failedCategories++;
                log(logger, "Category fetch failed (" + category.getTitle() + "): " + describeFailure(e));
            }
        }
        return new CategoryFetchResult(channelsMap, totalChannels, rawChannels, failedCategories);
    }

    private List<Category> categoriesMatchingVisibleIds(List<Category> rawCategories, Set<String> visibleCategoryIds,
                                                        CategoryNormalization categoryNormalization) {
        if (rawCategories == null || rawCategories.isEmpty()) {
            return List.of();
        }
        return rawCategories.stream()
                .filter(category -> category != null
                        && (isNotBlank(category.getCategoryId())
                        && visibleCategoryIds.contains(canonicalCategoryId(category.getCategoryId(),
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

    private List<Channel> fetchAllChannelsOrLog(Account account, LoggerCallback logger) {
        try {
            List<Channel> allChannels = XtremeApiParser.parseAllChannels(account);
            if (allChannels.isEmpty()) {
                log(logger, "Global Xtreme channel lookup returned no channels. Falling back to category fetch.");
            }
            return allChannels;
        } catch (Exception _) {
            log(logger, "Global Xtreme channel lookup failed. Falling back to category fetch.");
            return List.of();
        }
    }

    private boolean hasCategoryAssignments(List<Channel> allChannels) {
        return allChannels.stream().anyMatch(c -> isNotBlank(c.getCategoryId()));
    }

    private ChannelGrouping groupChannelsByKnownCategory(List<Channel> allChannels, List<Category> categories,
                                                         Map<String, String> canonicalCategoryIdByOriginalId) {
        Map<String, Category> categoryByApiId = categories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (a, b) -> a));
        Map<String, List<Channel>> matchedByCategory = new HashMap<>();
        List<Channel> orphaned = new ArrayList<>();
        for (Channel channel : allChannels) {
            String categoryId = canonicalCategoryId(channel.getCategoryId(), canonicalCategoryIdByOriginalId);
            if (isNotBlank(categoryId) && categoryByApiId.containsKey(categoryId)) {
                matchedByCategory.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(channel);
            } else {
                orphaned.add(channel);
            }
        }
        return new ChannelGrouping(matchedByCategory, orphaned);
    }

    private List<Category> categoriesWithUncategorizedIfNeeded(List<Category> categories, List<Channel> orphaned) {
        List<Category> categoriesToSave = new ArrayList<>(categories);
        if (!orphaned.isEmpty() && categories.stream().noneMatch(this::isUncategorizedCategory)) {
            categoriesToSave.add(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
        }
        return categoriesToSave;
    }

    private boolean isUncategorizedCategory(Category category) {
        return UNCATEGORIZED_ID.equals(category.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(category.getTitle());
    }

    private Category findUncategorizedCategory(Map<String, Category> savedByApiId) {
        return savedByApiId.values().stream()
                .filter(this::isUncategorizedCategory)
                .findFirst()
                .orElse(null);
    }

    private record CategoryFetchResult(Map<String, List<Channel>> channelsByCategory, int totalChannels, int rawChannels,
                                       int failedCategories) {
    }

    private record LiveReloadResult(boolean handled, CensoringSummary censoringSummary) {
        static LiveReloadResult handled(CensoringSummary summary) {
            return new LiveReloadResult(true, summary == null ? CensoringSummary.empty() : summary);
        }

        static LiveReloadResult notHandled() {
            return new LiveReloadResult(false, CensoringSummary.empty());
        }
    }

    private record GlobalLookupContext(List<Category> categories,
                                       Map<String, String> canonicalCategoryIdByOriginalId,
                                       Set<String> knownCategoryIds,
                                       Set<String> visibleCategoryIds,
                                       boolean applyCategoryCensoring,
                                       boolean applyChannelCensoring) {
    }

    private record ChannelGrouping(Map<String, List<Channel>> matchedByCategory, List<Channel> orphaned) {
    }

    private static String describeFailure(Exception e) {
        if (e == null) {
            return "unknown error";
        }
        String msg = e.getMessage();
        if (msg == null && e.getCause() != null) {
            msg = e.getCause().getMessage();
        }
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg;
    }
}
