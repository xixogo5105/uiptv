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
import com.uiptv.shared.PlaylistEntry
import com.uiptv.util.AccountType
import com.uiptv.util.StringUtils
import java.net.MalformedURLException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import com.uiptv.model.CategoryType.ALL

open class M3uCacheReloader(
    categoryServiceProvider: () -> CategoryService,
    configurationServiceProvider: () -> ConfigurationService
) : AbstractAccountCacheReloader(categoryServiceProvider, configurationServiceProvider) {
    override fun reloadCache(account: Account, logger: LoggerCallback?) {
        val categories = normalizeCategoriesByTitle(loadFreshCategories(account, logger)).categories()
        if (categories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.")
            return
        }
        log(logger, "Found Categories ${categories.size}")
        val channelsMap = loadM3uChannelsByCategory(categories, account, logger).toMutableMap()
        val totalChannels = channelsMap.values.sumOf { it.size }
        if (totalChannels == 0) {
            log(logger, "No channels found in any category. Keeping existing cache.")
            return
        }
        log(logger, "Found Channels $totalChannels. Found 0 Orphaned channels.")
        val categoriesToSave = filterCategoriesForM3u(categories, channelsMap, logger)
        clearCache(account)
        CategoryDb.get().saveAll(categoriesToSave, account)
        val savedCategories = CategoryDb.get().getCategories(account)
        savedCategories.forEach { savedCat ->
            val channels = channelsMap[savedCat.title]
            if (!channels.isNullOrEmpty()) {
                ChannelDb.get().saveAll(channels, savedCat.dbId.orEmpty(), account)
            }
        }
        log(logger, "${savedCategories.size} Categories & $totalChannels Channels saved Successfully ✓")
    }

    private fun loadFreshCategories(account: Account, logger: LoggerCallback?): List<Category> {
        if (!canReadFreshCategoriesFromSource(account)) {
            return categoryService().get(account, false, logger)
        }
        return try {
            val categories = LinkedHashSet<Category>()
            loadM3uCategories(account).forEach { entry ->
                categories.add(Category(entry.id, entry.groupTitle, entry.groupTitle, false, 0))
            }
            ArrayList(categories)
        } catch (e: MalformedURLException) {
            log(logger, "Failed to load fresh M3U categories: ${e.message}")
            categoryService().get(account, false, logger)
        }
    }

    private fun canReadFreshCategoriesFromSource(account: Account?): Boolean {
        if (account == null) return false
        val source = account.m3u8Path
        if (StringUtils.isBlank(source)) return false
        if (account.type == AccountType.M3U8_LOCAL) {
            return try {
                val path = Path.of(source)
                Files.isRegularFile(path) && Files.isReadable(path)
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    protected open fun loadM3uChannelsByCategory(categories: List<Category>, account: Account, logger: LoggerCallback?): Map<String, List<Channel>> {
        val channelsByCategory = LinkedHashMap<String, List<Channel>>()
        try {
            val buckets = buildM3uChannelBuckets(account)
            categories.forEach { addCategoryChannels(it, channelsByCategory, buckets) }
        } catch (e: MalformedURLException) {
            log(logger, "Failed to load M3U channels: ${e.message}")
        }
        return channelsByCategory
    }

    @Throws(MalformedURLException::class)
    private fun buildM3uChannelBuckets(account: Account): M3uChannelBuckets {
        val entries = loadM3uEntries(account)
        val hasOtherCategories = loadM3uCategories(account).size >= 2
        val allChannels = ArrayList<Channel>()
        val uncategorizedChannels = ArrayList<Channel>()
        val groupedChannels = HashMap<String, MutableList<Channel>>()
        entries.forEach { entry ->
            val channel = toChannel(entry)
            allChannels.add(channel)
            addChannelToBucket(entry, channel, groupedChannels, uncategorizedChannels, hasOtherCategories)
        }
        return M3uChannelBuckets(allChannels, uncategorizedChannels, groupedChannels)
    }

    private fun addChannelToBucket(entry: PlaylistEntry, channel: Channel, groupedChannels: MutableMap<String, MutableList<Channel>>, uncategorizedChannels: MutableList<Channel>, hasOtherCategories: Boolean) {
        val groupTitle = normalizedGroupTitle(entry)
        if (isUncategorizedGroup(groupTitle)) {
            if (hasOtherCategories) uncategorizedChannels.add(channel)
            return
        }
        groupedChannels.computeIfAbsent(groupTitle) { ArrayList() }.add(channel)
    }

    private fun addCategoryChannels(category: Category, channelsByCategory: MutableMap<String, List<Channel>>, buckets: M3uChannelBuckets) {
        val categoryTitle = category.title ?: return
        val matchedChannels = resolveChannelsForCategory(categoryTitle, buckets)
        if (!matchedChannels.isNullOrEmpty()) channelsByCategory[categoryTitle] = matchedChannels
    }

    private fun resolveChannelsForCategory(categoryTitle: String, buckets: M3uChannelBuckets): List<Channel>? =
        when {
            CategoryType.ALL.displayName().equals(categoryTitle, true) -> buckets.allChannels
            CategoryType.UNCATEGORIZED.displayName().equals(categoryTitle, true) -> buckets.uncategorizedChannels
            else -> buckets.groupedChannels[categoryTitle]
        }

    private fun normalizedGroupTitle(entry: PlaylistEntry): String = entry.groupTitle?.trim().orEmpty()

    private fun isUncategorizedGroup(groupTitle: String): Boolean =
        groupTitle.isEmpty() || CategoryType.UNCATEGORIZED.displayName().equals(groupTitle, true)

    private fun filterCategoriesForM3u(categories: List<Category>, channelsMap: MutableMap<String, List<Channel>>, logger: LoggerCallback?): List<Category> {
        val allCategory = categories.firstOrNull { isAllCategory(it) }
        val nonAllCategories = categories.filterNot { isAllCategory(it) }
        if (nonAllCategories.size == 1) {
            return handleSingleNonAllCategory(nonAllCategories.first(), allCategory, channelsMap, logger)
        }
        val nonAllWithChannels = filterEmptyCategoriesForM3u(nonAllCategories, channelsMap, logger)
        if (nonAllWithChannels.isEmpty() && allCategory == null && channelsMap.isNotEmpty()) {
            return createAllCategoryWithAccumulatedChannels(channelsMap, logger)
        }
        return buildFinalCategoryList(allCategory, nonAllWithChannels, channelsMap)
    }

    private fun filterEmptyCategoriesForM3u(categories: List<Category>, channelsMap: Map<String, List<Channel>>, logger: LoggerCallback?): List<Category> {
        val result = ArrayList<Category>()
        categories.forEach { cat ->
            val catTitle = cat.title.orEmpty()
            if (channelsMap.containsKey(catTitle) && !channelsMap[catTitle].isNullOrEmpty()) {
                result.add(cat)
            } else {
                log(logger, "Filtering out empty category: $catTitle")
            }
        }
        return result
    }

    private fun handleSingleNonAllCategory(singleCategory: Category, allCategory: Category?, channelsMap: MutableMap<String, List<Channel>>, logger: LoggerCallback?): List<Category> {
        log(logger, "Single non-All category detected. Treating as '${ALL.displayName()}' - ignoring category name '${singleCategory.title}'")
        val removedCategoryChannels = channelsMap.remove(singleCategory.title)
        if (!removedCategoryChannels.isNullOrEmpty()) {
            mergeChannelsIntoAll(channelsMap, removedCategoryChannels)
        }
        return listOf(allCategory ?: Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0))
    }

    private fun mergeChannelsIntoAll(channelsMap: MutableMap<String, List<Channel>>, channelsToAdd: List<Channel>) {
        val allCategoryKey = ALL.displayName()
        val allCategoryChannels = channelsMap[allCategoryKey]
        channelsMap[allCategoryKey] = if (allCategoryChannels == null) ArrayList(channelsToAdd) else mergeChannelsCaseInsensitive(allCategoryChannels, channelsToAdd)
    }

    private fun createAllCategoryWithAccumulatedChannels(channelsMap: MutableMap<String, List<Channel>>, logger: LoggerCallback?): List<Category> {
        log(logger, "All categories filtered out. Creating All category with accumulated channels.")
        var allChannels: List<Channel> = ArrayList()
        channelsMap.values.forEach { channelList ->
            if (!channelList.isNullOrEmpty()) {
                allChannels = mergeChannelsCaseInsensitive(allChannels, channelList)
            }
        }
        channelsMap.clear()
        channelsMap[ALL.displayName()] = allChannels
        return listOf(Category(ALL.identifier(), ALL.displayName(), ALL.displayName(), false, 0))
    }

    private fun buildFinalCategoryList(allCategory: Category?, nonAllWithChannels: List<Category>, channelsMap: Map<String, List<Channel>>): List<Category> {
        val result = ArrayList<Category>()
        if (allCategory != null) result.add(allCategory)
        nonAllWithChannels.forEach { cat ->
            if (isUncategorizedCategory(cat)) {
                if (channelsMap.containsKey(cat.title) && !channelsMap[cat.title].isNullOrEmpty()) result.add(cat)
            } else {
                result.add(cat)
            }
        }
        return result
    }

    private fun isAllCategory(category: Category?): Boolean = category?.title?.let(CategoryType::isAll) == true

    private fun isUncategorizedCategory(category: Category?): Boolean = category?.title?.let(CategoryType::isUncategorized) == true

    private data class M3uChannelBuckets(
        val allChannels: List<Channel>,
        val uncategorizedChannels: List<Channel>,
        val groupedChannels: Map<String, List<Channel>>
    )
}
