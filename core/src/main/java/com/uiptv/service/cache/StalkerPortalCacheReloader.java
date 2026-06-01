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
import com.uiptv.util.FetchAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class StalkerPortalCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        boolean applyCategoryCensoring = shouldApplyCategoryCensoring();
        boolean applyChannelCensoring = shouldApplyChannelCensoring();
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            log(logger, "Handshake failed for: " + account.getAccountName());
            return;
        }

        if (account.getAction() == itv) {
            CensoringSummary summary = reloadLive(account, logger, applyCategoryCensoring, applyChannelCensoring)
                    .add(cacheVodAndSeriesCategoriesOnly(account, logger));
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
        List<Channel> rawChannels = loadStalkerLiveChannels(account, liveCategories, visibleCategoryIds, logger);
        if (rawChannels.isEmpty()) {
            return liveCategories.summary();
        }

        CensoringSummary summary = liveCategories.summary();
        List<Channel> allChannels = applyChannelCensoring(rawChannels, applyChannelCensoring);
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

    private LiveCategories loadVisibleLiveCategories(Account account, LoggerCallback logger, boolean applyCategoryCensoring) {
        List<Category> rawCategories = loadOfficialLiveCategories(account);
        CategoryNormalization categoryNormalization = normalizeCategoriesByTitle(rawCategories);
        List<Category> allCategories = categoryNormalization.categories();
        CensoringSummary summary = CensoringSummary.empty();
        if (allCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return LiveCategories.stop(rawCategories, categoryNormalization, List.of(), summary);
        }

        List<Category> categories = applyCategoryCensoring(allCategories, applyCategoryCensoring);
        summary = summary.addCategories(censoredItemCount(allCategories.size(), categories.size(), applyCategoryCensoring));
        if (categories.isEmpty()) {
            handleEmptyLiveCategories(account, allCategories.size(), categories, applyCategoryCensoring, logger);
            return LiveCategories.stop(rawCategories, categoryNormalization, categories, summary);
        }
        log(logger, "Found Categories " + categories.size());
        return LiveCategories.continueWith(rawCategories, categoryNormalization, categories, summary);
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
        List<Channel> rawChannels = parseGlobalLiveChannels(account, logger);
        if (!rawChannels.isEmpty()) {
            return rawChannels;
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

    private List<Channel> fetchAllStalkerChannels(Account account) {
        List<Map<String, String>> attempts = List.of(
                getAllChannelsParams(null, null),
                getAllChannelsParams(0, 99999),
                getAllChannelsParams(1, 99999)
        );

        for (Map<String, String> params : attempts) {
            String json = FetchAPI.fetch(params, account);
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

    private List<Channel> fetchAllChannelsByCategoryLastResort(Account account, List<Category> categories,
                                                               CategoryNormalization categoryNormalization,
                                                               LoggerCallback logger) {
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category == null || isBlank(category.getCategoryId())) {
                continue;
            }

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
        return new ArrayList<>(uniqueChannels.values());
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

    private List<Category> loadOfficialLiveCategories(Account account) {
        String jsonCategories = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
        return CategoryService.getInstance().parseCategories(jsonCategories, false).stream()
                .filter(c -> !CategoryType.ALL.displayName().equalsIgnoreCase(c.getTitle()))
                .toList();
    }

    private List<Channel> parseGlobalLiveChannels(Account account, LoggerCallback logger) {
        try {
            return fetchAllStalkerChannels(account);
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
                                  boolean stopReload) {
        static LiveCategories stop(List<Category> rawCategories, CategoryNormalization categoryNormalization,
                                   List<Category> categories, CensoringSummary summary) {
            return new LiveCategories(rawCategories, categoryNormalization, categories, summary, true);
        }

        static LiveCategories continueWith(List<Category> rawCategories, CategoryNormalization categoryNormalization,
                                           List<Category> categories, CensoringSummary summary) {
            return new LiveCategories(rawCategories, categoryNormalization, categories, summary, false);
        }
    }
}
