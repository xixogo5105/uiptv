package com.uiptv.service.cache

import com.uiptv.api.LoggerCallback
import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.service.CategoryService
import com.uiptv.service.ConfigurationService
import com.uiptv.util.StringUtils
import com.uiptv.util.XtremeApiParser
import com.uiptv.model.Account.AccountAction.itv
import com.uiptv.model.Account.AccountAction.series
import com.uiptv.model.Account.AccountAction.vod

class XtremeApiCacheReloader @JvmOverloads constructor(
    categoryServiceProvider: () -> CategoryService = { CategoryService.getInstance() },
    configurationServiceProvider: () -> ConfigurationService = { ConfigurationService }
) : AbstractAccountCacheReloader(categoryServiceProvider, configurationServiceProvider) {
    override fun reloadCache(account: Account, logger: LoggerCallback?) {
        if (isVodOrSeriesAction(account)) {
            reloadVodOrSeriesCategories(account, logger)
            return
        }
        val rawCategories = loadLiveCategories(account, logger)
        val categoryNormalization = normalizeCategoriesByTitle(rawCategories)
        val categories = categoryNormalization.categories()
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.")
            return
        }
        log(logger, "Found Categories ${categories.size}")
        if (account.action == itv && reloadLiveWithGlobalLookup(account, categories, categoryNormalization.canonicalCategoryIdByOriginalId(), logger)) {
            cacheVodAndSeriesCategoriesOnly(account, logger)
            return
        }
        val fetchResult = fetchChannelsByCategory(account, rawCategories, categoryNormalization, logger)
        if (fetchResult.failedCategories >= rawCategories.size) {
            throw IllegalStateException("All category channel requests failed.")
        }
        if (fetchResult.totalChannels == 0) {
            if (fetchResult.failedCategories > 0) {
                throw IllegalStateException("No usable channels loaded after category fetch failures.")
            }
            log(logger, "No channels found in any category. Keeping existing cache.")
            return
        }
        log(logger, "Found Channels ${fetchResult.totalChannels}. Found 0 Orphaned channels.")
        clearCache(account)
        CategoryDb.get().saveAll(categories, account)
        val savedCategories = CategoryDb.get().getCategories(account)
        savedCategories.forEach { savedCat ->
            val channels = fetchResult.channelsByCategory[savedCat.categoryId]
            if (!channels.isNullOrEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.dbId.orEmpty(), account)
            }
        }
        log(logger, "${savedCategories.size} Categories & ${fetchResult.totalChannels} Channels saved Successfully ✓")
        cacheVodAndSeriesCategoriesOnly(account, logger)
    }

    private fun reloadLiveWithGlobalLookup(account: Account, categories: List<Category>, canonicalCategoryIdByOriginalId: Map<String, String>, logger: LoggerCallback?): Boolean {
        val allChannels = fetchAllChannelsOrLog(account, logger)
        if (allChannels.isEmpty()) return false
        if (!hasCategoryAssignments(allChannels)) {
            log(logger, "Global Xtreme channel lookup returned uncategorized rows only. Falling back to category fetch.")
            return false
        }
        val grouping = groupChannelsByKnownCategory(allChannels, categories, canonicalCategoryIdByOriginalId)
        log(logger, "Found Channels ${allChannels.size}. Found ${grouping.orphaned.size} Orphaned channels.")
        clearCache(account)
        CategoryDb.get().saveAll(categoriesWithUncategorizedIfNeeded(categories, grouping.orphaned), account)
        val savedCategories = CategoryDb.get().getCategories(account)
        val savedByApiId = savedCategories.associateBy { it.categoryId }
        grouping.matchedByCategory.forEach { (key, value) ->
            val category = savedByApiId[key]
            if (category != null && category.dbId != null) {
                ChannelDb.get().saveAll(value, category.dbId.orEmpty(), account)
            }
        }
        if (grouping.orphaned.isNotEmpty()) {
            val uncategorized = findUncategorizedCategory(savedByApiId)
            if (uncategorized != null && uncategorized.dbId != null) {
                ChannelDb.get().saveAll(grouping.orphaned, uncategorized.dbId.orEmpty(), account)
            }
        }
        log(logger, "${savedCategories.size} Categories & ${allChannels.size} Channels saved Successfully ✓")
        return true
    }

    private fun isVodOrSeriesAction(account: Account): Boolean = account.action == vod || account.action == series

    private fun reloadVodOrSeriesCategories(account: Account, logger: LoggerCallback?) {
        val categories = categoryService().get(account, false, logger)
        saveVodOrSeriesCategories(account, categories)
        log(logger, "Found Categories ${categories.size}")
        log(logger, "${categories.size} Categories saved Successfully ✓")
    }

    private fun loadLiveCategories(account: Account, logger: LoggerCallback?): List<Category> =
        categoryService().get(account, false, logger)
            .filterNot { CategoryType.ALL.displayName().equals(it.title, true) }

    private fun fetchChannelsByCategory(account: Account, rawCategories: List<Category>, categoryNormalization: CategoryNormalization, logger: LoggerCallback?): CategoryFetchResult {
        val channelsMap = HashMap<String, List<Channel>>()
        var totalChannels = 0
        var failedCategories = 0
        rawCategories.forEach { category ->
            try {
                val channels = XtremeApiParser.parseChannels(category.categoryId ?: "", account)
                if (channels.isNotEmpty()) {
                    val canonicalCategoryId = canonicalCategoryId(category.categoryId, categoryNormalization.canonicalCategoryIdByOriginalId()).orEmpty()
                    val mergedChannels = mergeChannelsCaseInsensitive(channelsMap[canonicalCategoryId], channels)
                    totalChannels += mergedChannels.size - (channelsMap[canonicalCategoryId]?.size ?: 0)
                    channelsMap[canonicalCategoryId] = mergedChannels
                }
            } catch (e: Exception) {
                failedCategories++
                log(logger, "Category fetch failed (${category.title}): ${describeFailure(e)}")
            }
        }
        return CategoryFetchResult(channelsMap, totalChannels, failedCategories)
    }

    private fun fetchAllChannelsOrLog(account: Account, logger: LoggerCallback?): List<Channel> =
        try {
            XtremeApiParser.parseAllChannels(account).also {
                if (it.isEmpty()) log(logger, "Global Xtreme channel lookup returned no channels. Falling back to category fetch.")
            }
        } catch (_: Exception) {
            log(logger, "Global Xtreme channel lookup failed. Falling back to category fetch.")
            emptyList()
        }

    private fun hasCategoryAssignments(allChannels: List<Channel>): Boolean =
        allChannels.any { StringUtils.isNotBlank(it.categoryId) }

    private fun groupChannelsByKnownCategory(allChannels: List<Channel>, categories: List<Category>, canonicalCategoryIdByOriginalId: Map<String, String>): ChannelGrouping {
        val categoryByApiId = categories.associateBy { it.categoryId }
        val matchedByCategory = HashMap<String, MutableList<Channel>>()
        val orphaned = ArrayList<Channel>()
        allChannels.forEach { channel ->
            val categoryId = canonicalCategoryId(channel.categoryId, canonicalCategoryIdByOriginalId)
            if (StringUtils.isNotBlank(categoryId) && categoryByApiId.containsKey(categoryId)) {
                matchedByCategory.computeIfAbsent(categoryId.orEmpty()) { ArrayList() }.add(channel)
            } else {
                orphaned.add(channel)
            }
        }
        return ChannelGrouping(matchedByCategory, orphaned)
    }

    private fun categoriesWithUncategorizedIfNeeded(categories: List<Category>, orphaned: List<Channel>): List<Category> {
        val categoriesToSave = ArrayList(categories)
        if (orphaned.isNotEmpty() && categories.none(this::isUncategorizedCategory)) {
            categoriesToSave.add(Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0))
        }
        return categoriesToSave
    }

    private fun isUncategorizedCategory(category: Category): Boolean =
        UNCATEGORIZED_ID == category.categoryId || UNCATEGORIZED_NAME.equals(category.title, true)

    private fun findUncategorizedCategory(savedByApiId: Map<String?, Category>): Category? =
        savedByApiId.values.firstOrNull(this::isUncategorizedCategory)

    private data class CategoryFetchResult(
        val channelsByCategory: Map<String, List<Channel>>,
        val totalChannels: Int,
        val failedCategories: Int
    )

    private data class ChannelGrouping(
        val matchedByCategory: Map<String, List<Channel>>,
        val orphaned: List<Channel>
    )

    private fun describeFailure(e: Exception?): String {
        if (e == null) return "unknown error"
        val message = e.message ?: e.cause?.message
        return if (message.isNullOrBlank()) e.javaClass.simpleName else message
    }
}
