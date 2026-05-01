package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.service.CategoryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.CategoryType.ALL;

public class M3uCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        List<Category> categories = normalizeCategoriesByTitle(CategoryService.getInstance().get(account, false, logger)).categories();
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + categories.size());

        Map<String, List<Channel>> channelsMap = loadM3uChannelsByCategory(categories, account, logger);
        int totalChannels = channelsMap.values().stream().mapToInt(List::size).sum();

        if (totalChannels == 0) {
            log(logger, "No channels found in any category. Keeping existing cache.");
            return;
        }
        log(logger, "Found Channels " + totalChannels + ". Found 0 Orphaned channels.");

        // Filter categories: only keep those with channels and apply M3U-specific rules
        List<Category> categoriesToSave = filterCategoriesForM3u(categories, channelsMap, logger);
        
        clearCache(account);
        CategoryDb.get().saveAll(categoriesToSave, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        for (Category savedCat : savedCategories) {
            List<Channel> channels = channelsMap.get(savedCat.getTitle());
            if (channels != null && !channels.isEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.getDbId(), account);
            }
        }
        log(logger, savedCategories.size() + " Categories & " + totalChannels + " Channels saved Successfully \u2713");
    }

    protected Map<String, List<Channel>> loadM3uChannelsByCategory(List<Category> categories, Account account, LoggerCallback logger) {
        Map<String, List<Channel>> channelsByCategory = new LinkedHashMap<>();
        try {
            List<PlaylistEntry> entries = loadM3uEntries(account);
            boolean hasOtherCategories = loadM3uCategories(account).size() >= 2;
            List<Channel> allChannels = new ArrayList<>();
            List<Channel> uncategorizedChannels = new ArrayList<>();
            Map<String, List<Channel>> groupedChannels = new HashMap<>();

            for (PlaylistEntry entry : entries) {
                Channel channel = toChannel(entry);
                allChannels.add(channel);

                String groupTitle = entry.getGroupTitle() == null ? "" : entry.getGroupTitle().trim();
                if (groupTitle.isEmpty() || CategoryType.UNCATEGORIZED.displayName().equalsIgnoreCase(groupTitle)) {
                    if (hasOtherCategories) {
                        uncategorizedChannels.add(channel);
                    }
                    continue;
                }
                groupedChannels.computeIfAbsent(groupTitle, ignored -> new ArrayList<>()).add(channel);
            }

            for (Category category : categories) {
                if (category == null || category.getTitle() == null) {
                    continue;
                }
                String categoryTitle = category.getTitle();
                if (CategoryType.ALL.displayName().equalsIgnoreCase(categoryTitle)) {
                    if (!allChannels.isEmpty()) {
                        channelsByCategory.put(categoryTitle, allChannels);
                    }
                    continue;
                }
                if (CategoryType.UNCATEGORIZED.displayName().equalsIgnoreCase(categoryTitle)) {
                    if (!uncategorizedChannels.isEmpty()) {
                        channelsByCategory.put(categoryTitle, uncategorizedChannels);
                    }
                    continue;
                }
                List<Channel> matched = groupedChannels.get(categoryTitle);
                if (matched != null && !matched.isEmpty()) {
                    channelsByCategory.put(categoryTitle, matched);
                }
            }
        } catch (Exception e) {
            log(logger, "Failed to load M3U channels: " + e.getMessage());
        }
        return channelsByCategory;
    }

    /**
     * Filter categories for M3U playlists according to these rules:
     * 1. Remove any non-All category that has no channels
     * 2. If exactly one non-All category existed originally (in input), only keep "All" and merge its channels
     * 3. If all categories were filtered out, save everything to "All"
     * 4. Only keep "Uncategorized" if it has channels AND other categories exist
     * 5. Always keep "All"
     */
    private List<Category> filterCategoriesForM3u(List<Category> categories, Map<String, List<Channel>> channelsMap, LoggerCallback logger) {
        Category allCategory = extractAllCategory(categories);
        List<Category> nonAllCategories = extractNonAllCategories(categories);
        
        // Handle single non-All category (treat as All)
        if (nonAllCategories.size() == 1) {
            return handleSingleNonAllCategory(nonAllCategories.get(0), allCategory, channelsMap, logger);
        }
        
        // Filter empty non-All categories
        List<Category> nonAllWithChannels = filterEmptyCategoriesForM3u(nonAllCategories, channelsMap, logger);
        
        // If all filtered out and no All, create one with accumulated channels
        if (nonAllWithChannels.isEmpty() && allCategory == null && !channelsMap.isEmpty()) {
            return createAllCategoryWithAccumulatedChannels(channelsMap, logger);
        }
        
        // Standard processing: return All + non-empty non-All categories
        return buildFinalCategoryList(allCategory, nonAllWithChannels, channelsMap);
    }

    private Category extractAllCategory(List<Category> categories) {
        for (Category cat : categories) {
            if (isAllCategory(cat)) {
                return cat;
            }
        }
        return null;
    }

    private List<Category> extractNonAllCategories(List<Category> categories) {
        List<Category> result = new ArrayList<>();
        for (Category cat : categories) {
            if (!isAllCategory(cat)) {
                result.add(cat);
            }
        }
        return result;
    }

    private List<Category> filterEmptyCategoriesForM3u(List<Category> categories, Map<String, List<Channel>> channelsMap, LoggerCallback logger) {
        List<Category> result = new ArrayList<>();
        for (Category cat : categories) {
            String catTitle = cat.getTitle();
            if (channelsMap.containsKey(catTitle) && !channelsMap.get(catTitle).isEmpty()) {
                result.add(cat);
            } else {
                log(logger, "Filtering out empty category: " + catTitle);
            }
        }
        return result;
    }

    private List<Category> handleSingleNonAllCategory(Category singleCategory, Category allCategory, Map<String, List<Channel>> channelsMap, LoggerCallback logger) {
        log(logger, "Single non-All category detected. Treating as '" + ALL.displayName() + "' - ignoring category name '" + singleCategory.getTitle() + "'");
        
        // Merge channels from single category into All
        List<Channel> removedCategoryChannels = channelsMap.remove(singleCategory.getTitle());
        if (removedCategoryChannels != null && !removedCategoryChannels.isEmpty()) {
            mergeChannelsIntoAll(channelsMap, removedCategoryChannels);
        }
        
        // Return All category (create if needed)
        List<Category> result = new ArrayList<>();
        if (allCategory != null) {
            result.add(allCategory);
        } else {
            result.add(new Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0));
        }
        return result;
    }

    private void mergeChannelsIntoAll(Map<String, List<Channel>> channelsMap, List<Channel> channelsToAdd) {
        String allCategoryKey = ALL.displayName();
        List<Channel> allCategoryChannels = channelsMap.get(allCategoryKey);
        if (allCategoryChannels == null) {
            channelsMap.put(allCategoryKey, new ArrayList<>(channelsToAdd));
        } else {
            channelsMap.put(allCategoryKey, mergeChannelsCaseInsensitive(allCategoryChannels, channelsToAdd));
        }
    }

    private List<Category> createAllCategoryWithAccumulatedChannels(Map<String, List<Channel>> channelsMap, LoggerCallback logger) {
        log(logger, "All categories filtered out. Creating All category with accumulated channels.");
        List<Channel> allChannels = new ArrayList<>();
        for (List<Channel> channelList : channelsMap.values()) {
            if (channelList != null && !channelList.isEmpty()) {
                allChannels = mergeChannelsCaseInsensitive(allChannels, channelList);
            }
        }
        channelsMap.clear();
        channelsMap.put(ALL.displayName(), allChannels);
        
        List<Category> result = new ArrayList<>();
        result.add(new Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0));
        return result;
    }

    private List<Category> buildFinalCategoryList(Category allCategory, List<Category> nonAllWithChannels, Map<String, List<Channel>> channelsMap) {
        List<Category> result = new ArrayList<>();
        
        if (allCategory != null) {
            result.add(allCategory);
        }
        
        for (Category cat : nonAllWithChannels) {
            // Only keep Uncategorized if it has channels
            if (isUncategorizedCategory(cat)) {
                if (channelsMap.containsKey(cat.getTitle()) && !channelsMap.get(cat.getTitle()).isEmpty()) {
                    result.add(cat);
                }
            } else {
                result.add(cat);
            }
        }
        
        return result;
    }

    private boolean isAllCategory(Category category) {
        if (category == null || category.getTitle() == null) {
            return false;
        }
        return CategoryType.isAll(category.getTitle());
    }

    private boolean isUncategorizedCategory(Category category) {
        if (category == null || category.getTitle() == null) {
            return false;
        }
        return CategoryType.isUncategorized(category.getTitle());
    }
}
