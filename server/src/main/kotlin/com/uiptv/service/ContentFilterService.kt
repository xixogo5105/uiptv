package com.uiptv.service

import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.util.StringUtils

object ContentFilterService {

    fun filterChannels(channels: List<Channel>?): List<Channel>? {
        if (channels.isNullOrEmpty()) {
            return channels
        }
        val configuration = ConfigurationService.read()
        val filterList = configuration.filterChannelsList.orEmpty()
        if (configuration.pauseFiltering || StringUtils.isBlank(filterList)) {
            return channels
        }
        val blockedWords = parseCsv(filterList)
        return channels.filter { channel ->
            val safeName = StringUtils.safeUtf(channel.name).lowercase()
            blockedWords.none { word -> safeName.contains(word) }
        }
    }

    fun filterCategories(categories: List<Category>?): List<Category>? {
        if (categories.isNullOrEmpty()) {
            return categories
        }
        val configuration = ConfigurationService.read()
        val filterList = configuration.filterCategoriesList.orEmpty()
        if (configuration.pauseFiltering || StringUtils.isBlank(filterList)) {
            return categories
        }
        val blockedWords = parseCsv(filterList)
        return categories.filter { category ->
            val title = category.title.orEmpty().lowercase()
            blockedWords.none(title::contains)
        }
    }

    private fun parseCsv(csv: String): List<String> =
        csv.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::lowercase)
}
