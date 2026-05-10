package com.uiptv.service

import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.util.StringUtils

object ContentFilterService {
    @JvmStatic
    fun getInstance(): ContentFilterService = this
    fun filterChannels(channels: List<Channel>?): List<Channel>? {
        if (channels.isNullOrEmpty()) {
            return channels
        }
        val configuration = ConfigurationService.getInstance().read()
        val filterList = configuration?.filterChannelsList
        if (StringUtils.isBlank(filterList) || (configuration != null && configuration.pauseFiltering)) {
            return channels
        }
        val blockedWords = parseCsv(filterList!!)
        return channels.filter { channel ->
            if (channel == null) {
                false
            } else {
                val safeName = StringUtils.safeUtf(channel.name).lowercase()
                blockedWords.none { word -> safeName.contains(word.lowercase()) }
            }
        }
    }
    fun filterCategories(categories: List<Category>?): List<Category>? {
        if (categories.isNullOrEmpty()) {
            return categories
        }
        val configuration = ConfigurationService.getInstance().read()
        val filterList = configuration?.filterCategoriesList
        if (StringUtils.isBlank(filterList) || (configuration != null && configuration.pauseFiltering)) {
            return categories
        }
        val blockedWords = parseCsv(filterList!!)
        return categories.filter { category ->
            if (category == null) {
                false
            } else {
                val title = category.title
                blockedWords.none { word ->
                    title != null && title.lowercase().contains(word.lowercase())
                }
            }
        }
    }

    private fun parseCsv(csv: String): List<String> = csv.split(",").map(String::trim)
}
