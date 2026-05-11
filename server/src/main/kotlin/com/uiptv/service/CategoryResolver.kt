package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.util.AccountType.M3U8_LOCAL
import com.uiptv.util.AccountType.M3U8_URL
import com.uiptv.util.AccountType.STALKER_PORTAL
import com.uiptv.util.AccountType.XTREME_API
import com.uiptv.util.I18n
import com.uiptv.util.koinOrNull
class CategoryResolver(
    private val channelServiceProvider: () -> ChannelService = { koinOrNull<ChannelService>() ?: ChannelService() }
) {
    fun resolveCategories(account: Account?, categories: List<Category>?): List<Category> {
        var processed = ArrayList(categories ?: emptyList())

        if (account != null && (account.type == M3U8_LOCAL || account.type == M3U8_URL)) {
            processed = ArrayList(
                processed.filter { category ->
                    if (category == null) {
                        false
                    } else if (isUncategorized(category)) {
                        channelServiceProvider.invoke().hasCachedLiveChannelsByDbCategoryId(category.dbId.orEmpty())
                    } else {
                        true
                    }
                }
            )
        }

        val hasAllCategory = processed.any { isAllCategory(it) }
        var shouldAddAll = true
        if (account != null && (account.type == STALKER_PORTAL || account.type == XTREME_API)) {
            shouldAddAll = processed.size >= 2
        }
        if (!hasAllCategory && shouldAddAll) {
            val withAll = ArrayList<Category>()
            withAll.add(buildAllCategory())
            withAll.addAll(processed)
            processed = withAll
        }

        return processed
    }

    private fun isUncategorized(category: Category?): Boolean =
        category?.title != null && CategoryType.isUncategorized(category.title)

    private fun isAllCategory(category: Category?): Boolean {
        if (category == null) {
            return false
        }
        return isAllValue(category.title) || isAllValue(category.categoryId) || isAllValue(category.dbId)
    }

    private fun isAllValue(value: String?): Boolean {
        if (value == null) {
            return false
        }
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            return false
        }
        if (ALL_CATEGORY_ID.equals(normalized, ignoreCase = true)) {
            return true
        }
        if (CategoryType.isAll(normalized)) {
            return true
        }
        return I18n.tr("commonAll").equals(normalized, ignoreCase = true)
    }

    private fun buildAllCategory(): Category =
        Category().apply {
            dbId = ALL_CATEGORY_ID
            categoryId = ALL_CATEGORY_ID
            title = CategoryType.ALL.displayName()
            alias = CategoryType.ALL.displayName()
        }

    companion object {
        @JvmField
        val ALL_CATEGORY_ID: String = CategoryType.ALL.identifier()
    }
}
