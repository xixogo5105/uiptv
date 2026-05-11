package com.uiptv.service.cache

import com.uiptv.api.LoggerCallback
import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.service.CategoryService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.shared.Pagination
import com.uiptv.util.StringUtils
import java.util.Collections
import java.util.LinkedHashMap
import com.uiptv.model.Account.AccountAction.itv
import com.uiptv.model.Account.AccountAction.series
import com.uiptv.model.Account.AccountAction.vod

class StalkerPortalCacheReloader(
    private val handshakeServiceProvider: () -> HandshakeService,
    private val channelServiceProvider: () -> ChannelService,
    categoryServiceProvider: () -> CategoryService,
    configurationServiceProvider: () -> ConfigurationService,
    private val fetchProvider: (Map<String, String>, Account) -> String
) : AbstractAccountCacheReloader(categoryServiceProvider, configurationServiceProvider) {
    override fun reloadCache(account: Account, logger: LoggerCallback?) {
        handshakeService().connect(account)
        if (account.isNotConnected()) {
            log(logger, "Handshake failed for: ${account.accountName}")
            return
        }
        when (account.action) {
            itv -> {
                reloadLive(account, logger)
                cacheVodAndSeriesCategoriesOnly(account, logger)
            }
            vod, series -> {
                val categories = categoryService().get(account, false, logger)
                saveVodOrSeriesCategories(account, categories)
                log(logger, "Found Categories ${categories.size}")
                log(logger, "${categories.size} Categories & 0 Channels saved Successfully ✓")
            }
        }
    }

    private fun reloadLive(account: Account, logger: LoggerCallback?) {
        val rawCategories = loadOfficialLiveCategories(account)
        val categoryNormalization = normalizeCategoriesByTitle(rawCategories)
        val officialCategories = categoryNormalization.categories()
        if (officialCategories.isEmpty()) {
            log(logger, "No categories found. Keeping existing cache.")
            return
        }
        log(logger, "Found Categories ${officialCategories.size}")
        var allChannels = parseGlobalLiveChannels(account, logger)
        if (allChannels.isEmpty()) {
            log(logger, "Global Stalker get_all_channels failed. Trying last-resort category-by-category fetch.")
            allChannels = fetchAllChannelsByCategoryLastResort(account, rawCategories, categoryNormalization, logger)
            if (allChannels.isEmpty()) {
                log(logger, "No channels found. Keeping existing cache.")
                return
            }
            log(logger, "Last-resort fetch succeeded. Collected ${allChannels.size} channels.")
        }
        val grouping = groupChannelsByCategory(allChannels, officialCategories, categoryNormalization.canonicalCategoryIdByOriginalId())
        log(logger, "Found Channels ${allChannels.size}. Found ${grouping.orphanedChannels.size} Orphaned channels.")
        clearCache(account)
        CategoryDb.get().saveAll(categoriesWithUncategorizedIfNeeded(officialCategories, grouping.orphanedChannels), account)
        val savedCategories = CategoryDb.get().getCategories(account)
        val savedCategoryMap = savedCategories.associateBy { it.categoryId }
        grouping.matchedChannelsByCatId.forEach { (key, value) ->
            val category = savedCategoryMap[key]
            if (category != null && category.dbId != null) {
                ChannelDb.get().saveAll(value, category.dbId.orEmpty(), account)
            }
        }
        if (grouping.orphanedChannels.isNotEmpty()) {
            val uncategorizedCategory = findUncategorizedCategory(savedCategoryMap)
            if (uncategorizedCategory != null && uncategorizedCategory.dbId != null) {
                ChannelDb.get().saveAll(grouping.orphanedChannels, uncategorizedCategory.dbId.orEmpty(), account)
            }
        }
        log(logger, "${savedCategories.size} Categories & ${allChannels.size} Channels saved Successfully ✓")
    }

    private fun fetchAllStalkerChannelsJson(account: Account): String {
        val attempts = listOf(getAllChannelsParams(null, null), getAllChannelsParams(0, 99999), getAllChannelsParams(1, 99999))
        for (params in attempts) {
            val json = fetchProvider.invoke(params, account)
            if (StringUtils.isBlank(json)) continue
            try {
                if (channelService().parseItvChannels(json, false).isNotEmpty()) return json
            } catch (_: Exception) {
            }
        }
        return ""
    }

    private fun fetchAllChannelsByCategoryLastResort(account: Account, categories: List<Category>, categoryNormalization: CategoryNormalization, logger: LoggerCallback?): List<Channel> {
        val uniqueChannels = LinkedHashMap<String, Channel>()
        categories.forEach { category ->
            if (StringUtils.isBlank(category.categoryId)) return@forEach
            val channelsForCategory = fetchStalkerCategoryChannelsLastResort(account, category.categoryId.orEmpty(), logger)
            channelsForCategory.forEach { channel ->
                if (StringUtils.isBlank(channel.channelId)) return@forEach
                val canonicalCategoryId = canonicalCategoryId(category.categoryId, categoryNormalization.canonicalCategoryIdByOriginalId()).orEmpty()
                channel.categoryId = if (StringUtils.isBlank(channel.categoryId)) canonicalCategoryId else canonicalCategoryId(channel.categoryId, categoryNormalization.canonicalCategoryIdByOriginalId()).orEmpty()
                uniqueChannels.putIfAbsent(normalizeCaseInsensitiveKey(channel.channelId), channel)
            }
        }
        return ArrayList(uniqueChannels.values)
    }

    private fun fetchStalkerCategoryChannelsLastResort(account: Account, categoryId: String, logger: LoggerCallback?): List<Channel> {
        val channels = fetchStalkerCategoryChannelsFromPage(account, categoryId, 0, logger)
        return if (channels.isNotEmpty()) channels else fetchStalkerCategoryChannelsFromPage(account, categoryId, 1, logger)
    }

    private fun fetchStalkerCategoryChannelsFromPage(account: Account, categoryId: String, startPage: Int, logger: LoggerCallback?): List<Channel> {
        val aggregated = ArrayList<Channel>()
        var maxAdditionalPages = 2
        for (page in startPage..(startPage + maxAdditionalPages)) {
            val json = fetchProvider.invoke(ChannelService.getChannelOrSeriesParams(categoryId, page, itv, null, null), account)
            if (StringUtils.isBlank(json)) break
            try {
                if (page == startPage) maxAdditionalPages = resolveMaxAdditionalPages(json, maxAdditionalPages)
                val pageChannels = channelService().parseItvChannels(json, false)
                if (pageChannels.isEmpty()) break
                aggregated.addAll(pageChannels)
            } catch (e: Exception) {
                log(logger, "Last-resort fetch failed for category $categoryId at page $page: ${e.message}")
                break
            }
        }
        return dedupeChannels(aggregated)
    }

    private fun loadOfficialLiveCategories(account: Account): List<Category> =
        categoryService().parseCategories(fetchProvider.invoke(getCategoryParams(account.action), account), false)
            .filterNot { CategoryType.ALL.displayName().equals(it.title, true) }

    private fun parseGlobalLiveChannels(account: Account, logger: LoggerCallback?): List<Channel> {
        val jsonChannels = fetchAllStalkerChannelsJson(account)
        if (StringUtils.isBlank(jsonChannels)) return Collections.emptyList()
        return try {
            channelService().parseItvChannels(jsonChannels, false)
        } catch (e: Exception) {
            log(logger, "Failed to parse channels from get_all_channels: ${e.message}")
            Collections.emptyList()
        }
    }

    private fun groupChannelsByCategory(allChannels: List<Channel>, officialCategories: List<Category>, canonicalCategoryIdByOriginalId: Map<String, String>): ChannelGrouping {
        val officialCategoryMap = officialCategories.associateBy { it.categoryId }
        val matchedChannelsByCatId = HashMap<String, MutableList<Channel>>()
        val orphanedChannels = ArrayList<Channel>()
        allChannels.forEach { channel ->
            val categoryId = canonicalCategoryId(channel.categoryId, canonicalCategoryIdByOriginalId)
            if (StringUtils.isNotBlank(categoryId) && officialCategoryMap.containsKey(categoryId)) {
                matchedChannelsByCatId.computeIfAbsent(categoryId.orEmpty()) { ArrayList() }.add(channel)
            } else {
                orphanedChannels.add(channel)
            }
        }
        return ChannelGrouping(matchedChannelsByCatId, orphanedChannels)
    }

    private fun categoriesWithUncategorizedIfNeeded(officialCategories: List<Category>, orphanedChannels: List<Channel>): List<Category> {
        val categoriesToSave = ArrayList(officialCategories)
        if (orphanedChannels.isNotEmpty() && officialCategories.none(this::isUncategorizedCategory)) {
            categoriesToSave.add(Category(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, null, false, 0))
        }
        return categoriesToSave
    }

    private fun isUncategorizedCategory(category: Category): Boolean =
        UNCATEGORIZED_ID == category.categoryId || UNCATEGORIZED_NAME.equals(category.title, true)

    private fun findUncategorizedCategory(savedCategoryMap: Map<String?, Category>): Category? =
        savedCategoryMap.values.firstOrNull(this::isUncategorizedCategory)

    private fun resolveMaxAdditionalPages(json: String, defaultValue: Int): Int {
        val pagination: Pagination? = channelService().parsePagination(json, null)
        return if (pagination == null) defaultValue else maxOf(pagination.pageCount + 1, 2)
    }

    private fun handshakeService(): HandshakeService = handshakeServiceProvider.invoke()

    private fun channelService(): ChannelService = channelServiceProvider.invoke()

    private fun dedupeChannels(aggregated: List<Channel>): List<Channel> {
        val uniqueChannels = LinkedHashMap<String, Channel>()
        aggregated.forEach { channel ->
            if (StringUtils.isNotBlank(channel.channelId)) {
                uniqueChannels.putIfAbsent(normalizeCaseInsensitiveKey(channel.channelId), channel)
            }
        }
        return ArrayList(uniqueChannels.values)
    }

    private data class ChannelGrouping(
        val matchedChannelsByCatId: Map<String, List<Channel>>,
        val orphanedChannels: List<Channel>
    )
}
