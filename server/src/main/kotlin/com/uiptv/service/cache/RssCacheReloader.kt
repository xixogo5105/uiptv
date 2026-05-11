package com.uiptv.service.cache

import com.uiptv.api.LoggerCallback
import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.service.CategoryService
import com.uiptv.service.ConfigurationService

open class RssCacheReloader(
    categoryServiceProvider: () -> CategoryService = { CategoryService.getInstance() },
    configurationServiceProvider: () -> ConfigurationService = { ConfigurationService.getInstance() }
) : AbstractAccountCacheReloader(categoryServiceProvider, configurationServiceProvider) {
    override fun reloadCache(account: Account, logger: LoggerCallback?) {
        val categories = categoryService().get(account, false, logger)
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.")
            return
        }
        log(logger, "Found Categories ${categories.size}")

        val channelsMap = HashMap<String, List<Channel>>()
        var totalChannels = 0
        for (category in categories) {
            try {
                val channels = rssChannels(category.title.orEmpty(), account)
                if (channels.isNotEmpty()) {
                    channelsMap[category.title.orEmpty()] = channels
                    totalChannels += channels.size
                }
            } catch (_: Exception) {
                // Best-effort category fetch: keep loading the remaining categories.
            }
        }
        if (totalChannels == 0) {
            log(logger, "No channels found in any category. Keeping existing cache.")
            return
        }
        log(logger, "Found Channels $totalChannels. Found 0 Orphaned channels.")
        clearCache(account)
        CategoryDb.get().saveAll(categories, account)
        val savedCategories = CategoryDb.get().getCategories(account)
        savedCategories.forEach { savedCat ->
            val channels = channelsMap[savedCat.title.orEmpty()]
            if (!channels.isNullOrEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.dbId.orEmpty(), account)
            }
        }
        log(logger, "${savedCategories.size} Categories & $totalChannels Channels saved Successfully ✓")
    }
}
