package com.uiptv.service.cache

import com.uiptv.api.LoggerCallback
import com.uiptv.db.AccountDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.service.CategoryService
import com.uiptv.service.ConfigurationService
import com.uiptv.shared.PlaylistEntry
import com.uiptv.util.AccountType
import com.uiptv.util.M3U8Parser
import com.uiptv.util.RssParser
import com.uiptv.util.StringUtils
import com.uiptv.util.UiptUtils
import java.net.MalformedURLException
import java.util.Date
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID

abstract class AbstractAccountCacheReloader(
    private val categoryServiceProvider: () -> CategoryService = { CategoryService.INSTANCE },
    private val configurationServiceProvider: () -> ConfigurationService = { ConfigurationService }
) : AccountCacheReloader {
    companion object {
        private const val ALL_CATEGORY = "All"
        const val UNCATEGORIZED_ID: String = "uncategorized"
        const val UNCATEGORIZED_NAME: String = "Uncategorized"
    }

    protected fun clearCache(account: Account?) {
        val existingPortalUrl = account?.serverPortalUrl ?: ""
        configurationService().clearCache(account)
        if (account != null &&
            account.type == AccountType.STALKER_PORTAL &&
            StringUtils.isNotBlank(existingPortalUrl)
        ) {
            account.serverPortalUrl = existingPortalUrl
            AccountDb.get().saveServerPortalUrl(account)
        }
    }

    protected fun log(logger: LoggerCallback?, message: String) {
        logger?.log(message)
    }

    protected fun getCategoryParams(accountAction: Account.AccountAction): Map<String, String> {
        val params = HashMap<String, String>()
        params["JsHttpRequest"] = Date().time.toString() + "-xml"
        params["type"] = accountAction.name
        params["action"] = if (accountAction == Account.AccountAction.itv) "get_genres" else "get_categories"
        return params
    }

    protected fun getAllChannelsParams(page: Int?, perPage: Int?): Map<String, String> {
        val params = HashMap<String, String>()
        params["type"] = "itv"
        params["action"] = "get_all_channels"
        if (page != null) {
            params["p"] = page.toString()
        }
        if (perPage != null) {
            params["per_page"] = perPage.toString()
        }
        params["JsHttpRequest"] = Date().time.toString() + "-xml"
        return params
    }

    protected fun cacheVodAndSeriesCategoriesOnly(account: Account, logger: LoggerCallback?) {
        if (account.type != AccountType.STALKER_PORTAL && account.type != AccountType.XTREME_API) {
            return
        }
        val original = account.action
        try {
            listOf(Account.AccountAction.vod, Account.AccountAction.series).forEach { mode ->
                account.action = mode
                try {
                    val categories = categoryService().get(account, false, logger)
                    saveVodOrSeriesCategories(account, categories)
                } catch (e: Exception) {
                    log(logger, "Global ${mode.name.uppercase()} category list failed: ${shortReason(e)}")
                }
            }
        } finally {
            account.action = original
        }
    }

    private fun shortReason(e: Exception?): String {
        if (e == null) {
            return "unknown error"
        }
        val message = e.message ?: e.cause?.message
        return if (StringUtils.isBlank(message)) e.javaClass.simpleName else message.orEmpty()
    }

    protected fun saveVodOrSeriesCategories(account: Account, categories: List<Category>) {
        val normalizedCategories = normalizeCategoriesByTitle(categories).categories
        when (account.action) {
            Account.AccountAction.vod -> VodCategoryDb.get().saveAll(normalizedCategories, account)
            Account.AccountAction.series -> SeriesCategoryDb.get().saveAll(normalizedCategories, account)
            else -> {}
        }
    }

    protected fun normalizeCategoriesByTitle(categories: List<Category>?): CategoryNormalization {
        if (categories.isNullOrEmpty()) {
            return CategoryNormalization(emptyList(), emptyMap())
        }
        val canonicalByKey = LinkedHashMap<String, Category>()
        val canonicalCategoryIdByOriginalId = HashMap<String, String>()
        categories.forEach { category ->
            val categoryKey = categoryComparisonKey(category)
            val canonical = canonicalByKey.computeIfAbsent(categoryKey) { category }
            if (StringUtils.isNotBlank(category.categoryId) && StringUtils.isNotBlank(canonical.categoryId)) {
                canonicalCategoryIdByOriginalId[category.categoryId.orEmpty()] = canonical.categoryId.orEmpty()
            }
        }
        return CategoryNormalization(ArrayList(canonicalByKey.values), canonicalCategoryIdByOriginalId)
    }

    protected fun canonicalCategoryId(categoryId: String?, canonicalCategoryIdByOriginalId: Map<String, String>): String? {
        if (StringUtils.isBlank(categoryId)) {
            return categoryId
        }
        return canonicalCategoryIdByOriginalId[categoryId] ?: categoryId
    }

    protected fun mergeChannelsCaseInsensitive(existingChannels: List<Channel>?, channelsToAdd: List<Channel>?): List<Channel> {
        if (existingChannels.isNullOrEmpty() && channelsToAdd.isNullOrEmpty()) {
            return emptyList()
        }
        val uniqueChannels = LinkedHashMap<String, Channel>()
        addChannelsCaseInsensitive(uniqueChannels, existingChannels)
        addChannelsCaseInsensitive(uniqueChannels, channelsToAdd)
        return ArrayList(uniqueChannels.values)
    }

    protected fun categoryLookupKey(categoryTitle: String?): String = normalizeCaseInsensitiveKey(categoryTitle)

    protected fun categoryService(): CategoryService = categoryServiceProvider.invoke()

    protected fun configurationService(): ConfigurationService = configurationServiceProvider.invoke()

    protected fun normalizeCaseInsensitiveKey(value: String?): String =
        if (StringUtils.isBlank(value)) "" else value!!.trim().lowercase(Locale.ROOT)

    private fun addChannelsCaseInsensitive(uniqueChannels: MutableMap<String, Channel>, channels: List<Channel>?) {
        channels?.forEach { channel ->
            uniqueChannels.putIfAbsent(channelComparisonKey(channel), channel)
        }
    }

    private fun categoryComparisonKey(category: Category?): String {
        if (category == null) {
            return ""
        }
        val titleKey = normalizeCaseInsensitiveKey(category.title)
        if (StringUtils.isNotBlank(titleKey)) {
            return titleKey
        }
        val categoryIdKey = normalizeCaseInsensitiveKey(category.categoryId)
        if (StringUtils.isNotBlank(categoryIdKey)) {
            return categoryIdKey
        }
        return UUID.randomUUID().toString()
    }

    private fun channelComparisonKey(channel: Channel): String {
        val channelIdKey = normalizeCaseInsensitiveKey(channel.channelId)
        if (StringUtils.isNotBlank(channelIdKey)) {
            return "id:$channelIdKey"
        }
        val nameKey = normalizeCaseInsensitiveKey(channel.name)
        if (StringUtils.isNotBlank(nameKey)) {
            return "name:$nameKey"
        }
        val cmdKey = normalizeCaseInsensitiveKey(channel.cmd)
        if (StringUtils.isNotBlank(cmdKey)) {
            return "cmd:$cmdKey"
        }
        return "fallback:${UUID.randomUUID()}"
    }

    @Throws(MalformedURLException::class)
    protected fun m3u8Channels(category: String, account: Account): List<Channel> {
        val channels = LinkedHashSet<Channel>()
        val m3uCategories = loadM3uCategories(account)
        val hasOtherCategories = m3uCategories.size >= 2
        for (entry in loadM3uEntries(account)) {
            if (matchesM3uCategory(entry, category, hasOtherCategories)) {
                channels.add(toChannel(entry))
            }
        }
        return channels.toList()
    }

    protected open fun rssChannels(category: String, account: Account): List<Channel> {
        val channels = LinkedHashSet<Channel>()
        val rssUrl = account.m3u8Path
        if (StringUtils.isBlank(rssUrl)) {
            return emptyList()
        }
        val rssEntries = RssParser.parse(rssUrl)
        rssEntries.filter {
            CategoryType.ALL.displayName().equals(category, true) ||
                it.groupTitle.equals(category, true) ||
                it.id.equals(category, true)
        }.forEach { entry ->
            channels.add(
                Channel(
                    entry.id,
                    entry.title,
                    null,
                    entry.getPlaylistEntry(),
                    null,
                    null,
                    null,
                    entry.logo,
                    0,
                    0,
                    0,
                    entry.drmType,
                    entry.drmLicenseUrl,
                    entry.clearKeys,
                    entry.inputstreamaddon,
                    entry.manifestType
                )
            )
        }
        return channels.toList()
    }

    @Throws(MalformedURLException::class)
    protected fun loadM3uCategories(account: Account): Set<PlaylistEntry> {
        val path = account.m3u8Path.orEmpty()
        if (StringUtils.isBlank(path)) {
            return LinkedHashSet()
        }
        return if (account.type == AccountType.M3U8_URL) {
            M3U8Parser.parseUrlCategory(UiptUtils.parseUrlLikeUri(path).toURL())
        } else {
            M3U8Parser.parsePathCategory(path)
        }
    }

    @Throws(MalformedURLException::class)
    protected fun loadM3uEntries(account: Account): List<PlaylistEntry> {
        val path = account.m3u8Path.orEmpty()
        if (StringUtils.isBlank(path)) {
            return emptyList()
        }
        return if (account.type == AccountType.M3U8_URL) {
            M3U8Parser.parseChannelUrlM3U8(UiptUtils.parseUrlLikeUri(path).toURL())
        } else {
            M3U8Parser.parseChannelPathM3U8(path)
        }
    }

    private fun matchesM3uCategory(entry: PlaylistEntry, category: String, hasOtherCategories: Boolean): Boolean {
        val trimmedGroupTitle = entry.groupTitle?.trim().orEmpty()
        if (category.equals(ALL_CATEGORY, true)) {
            return true
        }
        if (category.equals(UNCATEGORIZED_NAME, true)) {
            return hasOtherCategories && (trimmedGroupTitle.isEmpty() || trimmedGroupTitle.equals(UNCATEGORIZED_NAME, true))
        }
        return trimmedGroupTitle.equals(category, true) || (entry.id != null && entry.id.equals(category, true))
    }

    protected fun toChannel(entry: PlaylistEntry): Channel {
        var channelId = entry.id
        if (StringUtils.isBlank(channelId)) {
            channelId = UUID.nameUUIDFromBytes((entry.title.orEmpty() + entry.getPlaylistEntry().orEmpty()).toByteArray()).toString()
        }
        return Channel(
            channelId,
            entry.title,
            null,
            entry.getPlaylistEntry(),
            null,
            null,
            null,
            entry.logo,
            0,
            0,
            0,
            entry.drmType,
            entry.drmLicenseUrl,
            entry.clearKeys,
            entry.inputstreamaddon,
            entry.manifestType
        )
    }

    protected data class CategoryNormalization(
        val categories: List<Category>,
        val canonicalCategoryIdByOriginalId: Map<String, String>
    ) {
        fun categories(): List<Category> = categories

        fun canonicalCategoryIdByOriginalId(): Map<String, String> = canonicalCategoryIdByOriginalId
    }
}
