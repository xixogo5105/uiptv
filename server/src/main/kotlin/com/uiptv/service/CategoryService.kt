package com.uiptv.service

import com.uiptv.api.LoggerCallback
import com.uiptv.db.CategoryDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.shared.PlaylistEntry
import com.uiptv.util.AccountType
import com.uiptv.util.AppLog
import com.uiptv.util.FetchAPI
import com.uiptv.util.RssParser
import com.uiptv.util.ServerUtils
import com.uiptv.util.XtremeApiParser
import com.uiptv.util.json.asJsonString
import com.uiptv.util.json.optArray
import com.uiptv.util.json.optObject
import com.uiptv.util.json.parseJsonObject
import java.net.MalformedURLException
import java.net.URL
import java.util.Date
import java.util.LinkedHashSet
import com.uiptv.model.Account.AccountAction.itv
import com.uiptv.model.Account.AccountAction.series
import com.uiptv.model.Account.AccountAction.vod

class CategoryService(
    private val contentFilterService: ContentFilterService,
    private val configurationService: ConfigurationService,
    private val handshakeService: HandshakeService
) {
    constructor() : this(ContentFilterService, ConfigurationService, HandshakeService.INSTANCE)


    fun get(account: Account): List<Category> = get(account, true)
    fun getCached(account: Account?): List<Category> {
        if (account == null) {
            return emptyList()
        }
        return when (account.action) {
            vod -> VodCategoryDb.get().getCategories(account)
            series -> SeriesCategoryDb.get().getCategories(account)
            else -> CategoryDb.get().getCategories(account)
        }
    }
    fun get(account: Account, censor: Boolean): List<Category> = get(account, censor, null)
    fun get(account: Account, censor: Boolean, logger: LoggerCallback?): List<Category> {
        if (account.type == AccountType.RSS_FEED) {
            hardReloadCategories(account, logger)
            return maybeFilterCategories(CategoryDb.get().getCategories(account), censor)
        }

        if (usesVodSeriesCategoryCache(account)) {
            return getVodSeriesCategories(account, censor, logger)
        }

        if (Account.NOT_LIVE_TV_CHANNELS.contains(account.action)) {
            if (account.type == AccountType.STALKER_PORTAL || account.type == AccountType.XTREME_API) {
                val cachedCategories = CategoryDb.get().getCategories(account)
                if (cachedCategories.isNotEmpty()) {
                    log(logger, "Loaded categories from local cache.")
                    return maybeFilterCategories(cachedCategories, censor)
                }
                log(logger, "No cached categories found. Fetching from portal/provider...")
                val fetchedCategories = fetchCategoriesFromBackend(account, logger)
                if (fetchedCategories.isNotEmpty()) {
                    CategoryDb.get().saveAll(fetchedCategories, account)
                    log(logger, "Saved ${fetchedCategories.size} categories to local cache.")
                }
                return maybeFilterCategories(fetchedCategories, censor)
            }

            hardReloadCategories(account, logger)
            return maybeFilterCategories(CategoryDb.get().getCategories(account), censor)
        }

        val cachedCategories = CategoryDb.get().getCategories(account).toMutableList()
        if (cachedCategories.isEmpty() || account.action != itv) {
            hardReloadCategories(account, logger)
            cachedCategories.addAll(CategoryDb.get().getCategories(account))
        }
        return maybeFilterCategories(cachedCategories, censor)
    }

    private fun usesVodSeriesCategoryCache(account: Account): Boolean =
        (account.action == vod || account.action == series) &&
            (account.type == AccountType.STALKER_PORTAL || account.type == AccountType.XTREME_API)

    private fun getVodSeriesCategories(account: Account, censor: Boolean, logger: LoggerCallback?): List<Category> {
        val cached = getVodSeriesCachedCategories(account)
        if (cached.isNotEmpty() && isVodSeriesCategoriesFresh(account)) {
            log(logger, "Loaded categories from local cache.")
            return maybeFilterCategories(cached, censor)
        }
        log(logger, "No fresh cached categories found. Fetching from portal/provider...")
        val fetched = fetchCategoriesFromSource(account)
        if (fetched.isNotEmpty()) {
            saveVodSeriesCategories(account, fetched)
            log(logger, "Saved ${fetched.size} categories to local VOD/Series cache.")
            val stored = getVodSeriesCachedCategories(account)
            return maybeFilterCategories(if (stored.isEmpty()) fetched else stored, censor)
        }
        return maybeFilterCategories(cached, censor)
    }

    private fun fetchCategoriesFromSource(account: Account): List<Category> {
        val categories = ArrayList<Category>()
        try {
            if (account.type == AccountType.XTREME_API) {
                categories.addAll(xtremeAPICategories(account))
            } else {
                val stalker = stalkerPortalCategories(account, null)
                if (!stalker.isNullOrEmpty()) {
                    categories.addAll(stalker)
                }
            }
        } catch (e: Exception) {
            AppLog.addWarningLog(CategoryService::class.java, "Network Error: ${e.message}")
        }
        return categories
    }

    private fun getVodSeriesCachedCategories(account: Account): List<Category> =
        when (account.action) {
            vod -> VodCategoryDb.get().getCategories(account)
            series -> SeriesCategoryDb.get().getCategories(account)
            else -> emptyList()
        }

    private fun isVodSeriesCategoriesFresh(account: Account): Boolean {
        val cacheTtlMs = configurationService.getCacheExpiryMs()
        return when (account.action) {
            vod -> VodCategoryDb.get().isFresh(account, cacheTtlMs)
            series -> SeriesCategoryDb.get().isFresh(account, cacheTtlMs)
            else -> false
        }
    }

    private fun saveVodSeriesCategories(account: Account, categories: List<Category>) {
        when (account.action) {
            vod -> VodCategoryDb.get().saveAll(categories, account)
            series -> SeriesCategoryDb.get().saveAll(categories, account)
            else -> {}
        }
    }

    private fun hardReloadCategories(account: Account, logger: LoggerCallback?) {
        CategoryDb.get().saveAll(fetchCategoriesFromBackend(account, logger), account)
    }

    private fun fetchCategoriesFromBackend(account: Account, logger: LoggerCallback?): List<Category> {
        val categories = ArrayList<Category>()
        try {
            when (account.type) {
                AccountType.M3U8_LOCAL, AccountType.M3U8_URL -> categories.addAll(m3u8Categories(account))
                AccountType.XTREME_API -> {
                    log(logger, "Fetching categories from Xtreme API...")
                    categories.addAll(xtremeAPICategories(account))
                }
                AccountType.RSS_FEED -> categories.addAll(rssCategories())
                else -> {
                    log(logger, "Fetching categories from Stalker Portal...")
                    val stalker = stalkerPortalCategories(account, logger)
                    if (!stalker.isNullOrEmpty()) {
                        categories.addAll(stalker)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.addWarningLog(CategoryService::class.java, "Network Error: ${e.message}")
            log(logger, "Network error while loading categories: ${e.message}")
        }
        return categories
    }

    private fun xtremeAPICategories(account: Account): List<Category> = XtremeApiParser.parseCategories(account)

    @Throws(MalformedURLException::class)
    private fun m3u8Categories(account: Account): List<Category> {
        val categories = LinkedHashSet<Category>()
            val m3uEntries: Set<PlaylistEntry> =
            if (account.type == AccountType.M3U8_URL) {
                com.uiptv.util.M3U8Parser.parseUrlCategory(com.uiptv.util.UiptUtils.parseUrlLikeUri(account.m3u8Path.orEmpty()).toURL())
            } else {
                com.uiptv.util.M3U8Parser.parsePathCategory(account.m3u8Path.orEmpty())
            }
        m3uEntries.forEach { entry ->
            categories.add(Category(entry.id, entry.groupTitle, entry.groupTitle, false, 0))
        }
        return categories.toList()
    }

    private fun rssCategories(): List<Category> {
        val categories = LinkedHashSet<Category>()
        RssParser.getCategories().forEach { entry ->
            categories.add(Category(entry.id, entry.groupTitle, entry.groupTitle, false, 0))
        }
        return categories.toList()
    }

    private fun stalkerPortalCategories(account: Account, logger: LoggerCallback?): List<Category> {
        log(logger, "Performing portal handshake...")
        handshakeService.connect(account)
        if (account.isNotConnected()) {
            log(logger, "Handshake failed.")
            return emptyList()
        }
        log(logger, "Handshake successful. Loading categories...")
        return parseCategories(FetchAPI.fetch(getCategoryParams(account.action), account), false)
    }
    fun readToJson(account: Account): String = ServerUtils.objectToJson(get(account))
    fun parseCategories(json: String?, censor: Boolean): List<Category> {
        val categoryList = ArrayList<Category>()
        try {
            val root = parseJsonObject(json) ?: return maybeFilterCategories(categoryList, censor)
            val list = root.optArray("js")
                ?: root.optObject("js")?.optArray("data")
                ?: root.optArray("data")
                ?: return maybeFilterCategories(categoryList, censor)
            for (index in list.indices) {
                val jsonCategory = list.optObject(index) ?: continue
                val categoryId = firstNonBlank(
                    FetchAPI.nullSafeString(jsonCategory, "id"),
                    FetchAPI.nullSafeString(jsonCategory, "category_id"),
                    FetchAPI.nullSafeString(jsonCategory, "tv_genre_id"),
                    FetchAPI.nullSafeString(jsonCategory, "alias"),
                    FetchAPI.nullSafeString(jsonCategory, "title"),
                    FetchAPI.nullSafeString(jsonCategory, "name")
                )
                val title = firstNonBlank(
                    FetchAPI.nullSafeString(jsonCategory, "title"),
                    FetchAPI.nullSafeString(jsonCategory, "name"),
                    categoryId
                )
                val alias = firstNonBlank(FetchAPI.nullSafeString(jsonCategory, "alias"), title, categoryId)
                if (categoryId.isBlank() || title.isBlank()) continue
                val category = Category(
                    categoryId,
                    title,
                    alias,
                    FetchAPI.nullSafeBoolean(jsonCategory, "active_sub"),
                    FetchAPI.nullSafeInteger(jsonCategory, "censored")
                )
                category.extraJson = jsonCategory.asJsonString()
                categoryList.add(category)
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(CategoryService::class.java, "Error while processing response data${e.message}")
        }
        return maybeFilterCategories(categoryList, censor)
    }

    private fun maybeFilterCategories(categories: List<Category>, applyFilter: Boolean): List<Category> {
        val normalized = normalizeAllAndUncategorized(categories)
        return if (applyFilter) contentFilterService.filterCategories(normalized) ?: normalized else normalized
    }

    private fun normalizeAllAndUncategorized(categories: List<Category>?): List<Category> {
        if (categories.isNullOrEmpty() || categories.size != 2) {
            return categories ?: emptyList()
        }
        val hasAll = categories.any { titleEquals(it, CategoryType.ALL.displayName()) }
        val hasUncategorized = categories.any { titleEquals(it, CategoryType.UNCATEGORIZED.displayName()) }
        if (!hasAll || !hasUncategorized) {
            return categories
        }
        return categories.filterNot { titleEquals(it, CategoryType.UNCATEGORIZED.displayName()) }
    }

    private fun titleEquals(category: Category?, value: String?): Boolean {
        val title = category?.title
        if (title == null || value == null) {
            return false
        }
        return title.trim().equals(value.trim(), true)
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

    private fun log(logger: LoggerCallback?, message: String) {
        logger?.log(message)
    }

    private fun getCategoryParams(accountAction: Account.AccountAction): Map<String, String> {
        val params = HashMap<String, String>()
        params["JsHttpRequest"] = "${Date().time}-xml"
        params["type"] = accountAction.name
        params["action"] = if (accountAction == itv) "get_genres" else "get_categories"
        return params
    }

    companion object {
        @JvmField
        val INSTANCE: CategoryService = CategoryService(ContentFilterService, ConfigurationService, HandshakeService.INSTANCE)
    }
}
