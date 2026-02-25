package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RssCacheReloader extends AbstractAccountCacheReloader {
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
                List<Channel> channels = rssChannels(category.getTitle(), account);
                if (!channels.isEmpty()) {
                    channelsMap.put(category.getTitle(), channels);
                    totalChannels += channels.size();
                }
            } catch (Exception ignored) {
            }
        }

        if (totalChannels == 0) {
            log(logger, "No channels found in any category. Keeping existing cache.");
            return;
        }
        log(logger, "Found Channels " + totalChannels + ". Found 0 Orphaned channels.");

        clearCache(account);
        CategoryDb.get().saveAll(categories, account);
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        for (Category savedCat : savedCategories) {
            List<Channel> channels = channelsMap.get(savedCat.getTitle());
            if (channels != null && !channels.isEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.getDbId(), account);
            }
        }
        log(logger, savedCategories.size() + " Categories & " + totalChannels + " Channels saved Successfully \u2713");
    }
}
