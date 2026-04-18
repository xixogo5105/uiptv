package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.model.CategoryType.ALL;

public class M3uCacheReloader extends AbstractAccountCacheReloader {
    @Override
    public void reloadCache(Account account, LoggerCallback logger) {
        List<Category> categories = CategoryService.getInstance().get(account, false, logger);
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.");
            return;
        }
        log(logger, "Found Categories " + categories.size());

        Map<String, List<Channel>> channelsMap = new HashMap<>();
        int totalChannels = 0;
        for (Category category : categories) {
            try {
                List<Channel> channels = m3u8Channels(category.getTitle(), account);
                if (!channels.isEmpty()) {
                    channelsMap.put(category.getTitle(), channels);
                    totalChannels += channels.size();
                }
            } catch (Exception _) {
                // Best-effort category fetch: keep loading the remaining categories.
            }
        }

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

    /**
     * Filter categories for M3U playlists according to these rules:
     * 1. Remove any non-All category that has no channels
     * 2. If exactly one non-All category existed originally (in input), only keep "All" and merge its channels
     * 3. If all categories were filtered out, save everything to "All"
     * 4. Only keep "Uncategorized" if it has channels AND other categories exist
     * 5. Always keep "All"
     */
    private List<Category> filterCategoriesForM3u(List<Category> categories, Map<String, List<Channel>> channelsMap, LoggerCallback logger) {
        List<Category> filtered = new ArrayList<>();
        Category allCategory = null;
        List<Category> originalNonAllCategories = new ArrayList<>();

        // First pass: separate All from other categories and count original non-All categories
        for (Category cat : categories) {
            if (isAllCategory(cat)) {
                allCategory = cat;
            } else {
                originalNonAllCategories.add(cat);
            }
        }

        // Check if we originally had exactly one non-All category
        boolean hasSingleNonAllOriginal = originalNonAllCategories.size() == 1;
        
        // Second pass: filter empty non-All categories
        List<Category> nonAllWithChannels = new ArrayList<>();
        for (Category cat : originalNonAllCategories) {
            String catTitle = cat.getTitle();
            if (channelsMap.containsKey(catTitle) && !channelsMap.get(catTitle).isEmpty()) {
                nonAllWithChannels.add(cat);
            } else {
                log(logger, "Filtering out empty category: " + catTitle);
            }
        }

        // Rule 1: If originally had exactly one non-All category, treat it as All (even if now filtered to empty)
        if (hasSingleNonAllOriginal && !originalNonAllCategories.isEmpty()) {
            String removedCategoryName = originalNonAllCategories.get(0).getTitle();
            log(logger, "Single non-All category detected. Treating as '" + ALL.displayName() + "' - ignoring category name '" + removedCategoryName + "'");
            
            // Merge channels from the removed category into the All category
            List<Channel> removedCategoryChannels = channelsMap.remove(removedCategoryName);
            if (removedCategoryChannels != null && !removedCategoryChannels.isEmpty()) {
                String allCategoryKey = ALL.displayName();
                List<Channel> allCategoryChannels = channelsMap.get(allCategoryKey);
                if (allCategoryChannels == null) {
                    // No existing channels for All, create new list
                    channelsMap.put(allCategoryKey, new ArrayList<>(removedCategoryChannels));
                } else {
                    // Convert to mutable if needed and add removed channels
                    if (!(allCategoryChannels instanceof ArrayList)) {
                        allCategoryChannels = new ArrayList<>(allCategoryChannels);
                        channelsMap.put(allCategoryKey, allCategoryChannels);
                    }
                    allCategoryChannels.addAll(removedCategoryChannels);
                }
            }
            
            // Add the All category (create if not found in input)
            if (allCategory != null) {
                filtered.add(allCategory);
            } else {
                // Create All category if it didn't exist in input
                allCategory = new Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0);
                filtered.add(allCategory);
            }
            return filtered;
        }

        // If all non-All categories were filtered out and no All category exists,
        // create All with accumulated channels
        if (nonAllWithChannels.isEmpty() && allCategory == null && !channelsMap.isEmpty()) {
            log(logger, "All categories filtered out. Creating All category with accumulated channels.");
            // Accumulate all channels into the All category
            List<Channel> allChannels = new ArrayList<>();
            for (List<Channel> channelList : channelsMap.values()) {
                if (channelList != null && !channelList.isEmpty()) {
                    allChannels.addAll(channelList);
                }
            }
            channelsMap.clear();
            channelsMap.put(ALL.displayName(), allChannels);
            
            Category allCat = new Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0);
            filtered.add(allCat);
            return filtered;
        }

        // Multiple or no non-All categories: standard processing
        if (allCategory != null) {
            filtered.add(allCategory);
        }

        for (Category cat : nonAllWithChannels) {
            // Rule 2: Only keep Uncategorized if it has channels
            if (isUncategorizedCategory(cat)) {
                if (channelsMap.containsKey(cat.getTitle()) && !channelsMap.get(cat.getTitle()).isEmpty()) {
                    filtered.add(cat);
                }
            } else {
                filtered.add(cat);
            }
        }

        return filtered;
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
