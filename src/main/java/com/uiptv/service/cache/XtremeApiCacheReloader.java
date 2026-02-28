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
        if (account.getAction() == vod || account.getAction() == series) {
            List<Category> categories = CategoryService.getInstance().get(account, false, logger);
            saveVodOrSeriesCategories(account, categories);
            log(logger, "Found Categories " + categories.size());
            log(logger, categories.size() + " Categories saved Successfully \u2713");
            return;
        }

        List<Category> categories = CategoryService.getInstance().get(account, false, logger)
                .stream()
                .filter(c -> !"All".equalsIgnoreCase(c.getTitle()))
                .toList();
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + categories.size());

        if (account.getAction() == itv && reloadLiveWithGlobalLookup(account, categories, logger)) {
            cacheVodAndSeriesCategoriesOnly(account, logger);
            return;
        }

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
                log(logger, "Category fetch failed (" + category.getTitle() + "): " + shortReason(e));
            }
        }

        if (failedCategories == categories.size()) {
            throw new RuntimeException("All category channel requests failed.");
        }
        if (totalChannels == 0) {
            if (failedCategories > 0) {
                throw new RuntimeException("No usable channels loaded after category fetch failures.");
            }
            log(logger, "No channels found in any category. Keeping existing cache.");
            return;
        }
        log(logger, "Found Channels " + totalChannels + ". Found 0 Orphaned channels.");

        clearCache(account);
        CategoryDb.get().saveAll(categories, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        for (Category savedCat : savedCategories) {
            List<Channel> channels = channelsMap.get(savedCat.getCategoryId());
            if (channels != null && !channels.isEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.getDbId(), account);
            }
        }
        log(logger, savedCategories.size() + " Categories & " + totalChannels + " Channels saved Successfully \u2713");
        cacheVodAndSeriesCategoriesOnly(account, logger);
    }

    private boolean reloadLiveWithGlobalLookup(Account account, List<Category> categories, LoggerCallback logger) {
        List<Channel> allChannels;
        try {
            allChannels = XtremeParser.parseAllChannels(account);
        } catch (Exception e) {
            log(logger, "Global Xtreme channel lookup failed. Falling back to category fetch.");
            return false;
        }

        if (allChannels.isEmpty()) {
            log(logger, "Global Xtreme channel lookup returned no channels. Falling back to category fetch.");
            return false;
        }

        boolean hasCategoryAssignments = allChannels.stream().anyMatch(c -> isNotBlank(c.getCategoryId()));
        if (!hasCategoryAssignments) {
            log(logger, "Global Xtreme channel lookup returned uncategorized rows only. Falling back to category fetch.");
            return false;
        }

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

        log(logger, "Found Channels " + allChannels.size() + ". Found " + orphaned.size() + " Orphaned channels.");
        clearCache(account);

        List<Category> categoriesToSave = new ArrayList<>(categories);
        if (!orphaned.isEmpty()) {
            boolean hasUncategorized = categories.stream()
                    .anyMatch(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()));
            if (!hasUncategorized) {
                categoriesToSave.add(new Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0));
            }
        }

        CategoryDb.get().saveAll(categoriesToSave, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        Map<String, Category> savedByApiId = savedCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c, (a, b) -> a));

        for (Map.Entry<String, List<Channel>> entry : matchedByCategory.entrySet()) {
            Category category = savedByApiId.get(entry.getKey());
            if (category != null && category.getDbId() != null) {
                ChannelDb.get().saveAll(entry.getValue(), category.getDbId(), account);
            }
        }

        if (!orphaned.isEmpty()) {
            Category uncategorized = savedByApiId.values().stream()
                    .filter(c -> UNCATEGORIZED_ID.equals(c.getCategoryId()) || UNCATEGORIZED_NAME.equalsIgnoreCase(c.getTitle()))
                    .findFirst()
                    .orElse(null);
            if (uncategorized != null && uncategorized.getDbId() != null) {
                ChannelDb.get().saveAll(orphaned, uncategorized.getDbId(), account);
            }
        }

        log(logger, savedCategories.size() + " Categories & " + allChannels.size() + " Channels saved Successfully \u2713");
        return true;
    }

    private String shortReason(Exception e) {
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
