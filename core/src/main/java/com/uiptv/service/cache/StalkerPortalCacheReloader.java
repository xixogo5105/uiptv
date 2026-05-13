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
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class StalkerPortalCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            log(logger, "Handshake failed for: " + account.getAccountName());
            return;
        }

        if (account.getAction() == itv) {
            reloadLive(account, logger);
            cacheVodAndSeriesCategoriesOnly(account, logger);
            return;
        }

        if (account.getAction() == vod || account.getAction() == series) {
            List<Category> categories = CategoryService.getInstance().get(account, false, logger);
            saveVodOrSeriesCategories(account, categories);
            log(logger, "Found Categories " + categories.size());
            log(logger, categories.size() + " Categories & 0 Channels saved Successfully \u2713");
        }
    }

    private void reloadLive(Account account, LoggerCallback logger) {
        List<Category> rawCategories = loadOfficialLiveCategories(account);
        CategoryNormalization categoryNormalization = normalizeCategoriesByTitle(rawCategories);
        List<Category> officialCategories = categoryNormalization.categories();

        if (officialCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + officialCategories.size());

        List<Channel> allChannels = parseGlobalLiveChannels(account, logger);

        if (allChannels.isEmpty()) {
            log(logger, "Global Stalker get_all_channels failed. Trying last-resort category-by-category fetch.");
            allChannels = fetchAllChannelsByCategoryLastResort(account, rawCategories, categoryNormalization, logger);
            if (allChannels.isEmpty()) {
                log(logger, "No channels found. Keeping existing cache.");
                return;
            }
            log(logger, "Last-resort fetch succeeded. Collected " + allChannels.size() + " channels.");
        }

        ChannelGrouping grouping = groupChannelsByCategory(allChannels, officialCategories, categoryNormalization.canonicalCategoryIdByOriginalId());
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

    private String fetchAllStalkerChannelsJson(Account account) {
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
                    return json;
                }
            } catch (Exception _) {
                // Ignore non-usable JSON variants and keep trying the remaining fallback shapes.
            }
        }
        return "";
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
        String jsonChannels = fetchAllStalkerChannelsJson(account);
        if (isBlank(jsonChannels)) {
            return Collections.emptyList();
        }
        try {
            return ChannelService.getInstance().parseItvChannels(jsonChannels, false);
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
}
