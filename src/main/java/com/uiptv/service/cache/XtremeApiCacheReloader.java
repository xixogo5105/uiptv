package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;
import com.uiptv.ui.XtremeParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isNotBlank;

public class XtremeApiCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        if (isVodOrSeriesAction(account)) {
            reloadVodOrSeriesCategories(account, logger);
            return;
        }

        List<Category> categories = loadLiveCategories(account, logger);
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + categories.size());

        if (account.getAction() == itv && reloadLiveWithGlobalLookup(account, categories, logger)) {
            cacheVodAndSeriesCategoriesOnly(account, logger);
            return;
        }

        CategoryFetchResult fetchResult = fetchChannelsByCategory(account, categories, logger);
        if (fetchResult.failedCategories == categories.size()) {
            throw new IllegalStateException("All category channel requests failed.");
        }
        if (fetchResult.totalChannels == 0) {
            if (fetchResult.failedCategories > 0) {
                throw new IllegalStateException("No usable channels loaded after category fetch failures.");
            }
            log(logger, "No channels found in any category. Keeping existing cache.");
            return;
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
        cacheVodAndSeriesCategoriesOnly(account, logger);
    }

    private boolean reloadLiveWithGlobalLookup(Account account, List<Category> categories, LoggerCallback logger) {
        List<Channel> allChannels = fetchAllChannelsOrLog(account, logger);
        if (allChannels == null || allChannels.isEmpty()) {
            return false;
        }

        if (!hasCategoryAssignments(allChannels)) {
            log(logger, "Global Xtreme channel lookup returned uncategorized rows only. Falling back to category fetch.");
            return false;
        }

        ChannelGrouping grouping = groupChannelsByKnownCategory(allChannels, categories);

        log(logger, "Found Channels " + allChannels.size() + ". Found " + grouping.orphaned.size() + " Orphaned channels.");
        clearCache(account);

        CategoryDb.get().saveAll(categoriesWithUncategorizedIfNeeded(categories, grouping.orphaned), account);
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
        return true;
    }

    private boolean isVodOrSeriesAction(Account account) {
        return account.getAction() == vod || account.getAction() == series;
    }

    private void reloadVodOrSeriesCategories(Account account, LoggerCallback logger) {
        List<Category> categories = CategoryService.getInstance().get(account, false, logger);
        saveVodOrSeriesCategories(account, categories);
        log(logger, "Found Categories " + categories.size());
        log(logger, categories.size() + " Categories saved Successfully \u2713");
    }

    private List<Category> loadLiveCategories(Account account, LoggerCallback logger) {
        return CategoryService.getInstance().get(account, false, logger).stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .toList();
    }

    private CategoryFetchResult fetchChannelsByCategory(Account account, List<Category> categories, LoggerCallback logger) {
        Map<String, List<Channel>> channelsMap = new HashMap<>();
        int totalChannels = 0;
        int failedCategories = 0;
        for (Category category : categories) {
            try {
                List<Channel> channels = XtremeParser.parseChannels(category.getCategoryId(), account);
                if (!channels.isEmpty()) {
                    channelsMap.put(category.getCategoryId(), channels);
                    totalChannels += channels.size();
                }
            } catch (Exception e) {
                failedCategories++;
                log(logger, "Category fetch failed (" + category.getTitle() + "): " + describeFailure(e));
            }
        }
        return new CategoryFetchResult(channelsMap, totalChannels, failedCategories);
    }

    private List<Channel> fetchAllChannelsOrLog(Account account, LoggerCallback logger) {
        try {
            List<Channel> allChannels = XtremeParser.parseAllChannels(account);
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

    private ChannelGrouping groupChannelsByKnownCategory(List<Channel> allChannels, List<Category> categories) {
        Map<String, Category> categoryByApiId = categories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (a, b) -> a));
        Map<String, List<Channel>> matchedByCategory = new HashMap<>();
        List<Channel> orphaned = new ArrayList<>();
        for (Channel channel : allChannels) {
            String categoryId = channel.getCategoryId();
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

    private record CategoryFetchResult(Map<String, List<Channel>> channelsByCategory, int totalChannels, int failedCategories) {
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
