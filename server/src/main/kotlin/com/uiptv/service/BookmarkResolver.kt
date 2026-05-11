package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.Channel
import com.uiptv.shared.Episode
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank

class BookmarkResolver(
    private val accountServiceProvider: () -> AccountService = { AccountService },
    private val channelServiceProvider: () -> ChannelService
) {
    fun prepare(bookmarks: List<Bookmark>?): ResolutionContext {
        val accountByName = accountServiceProvider.invoke().getAll()
        val renderDataByBookmarkId = preloadRenderData(bookmarks)
        val channelByAccountAndChannel = preloadFallbackChannels(bookmarks, accountByName, renderDataByBookmarkId)
        return ResolutionContext(accountByName, channelByAccountAndChannel, renderDataByBookmarkId)
    }

    fun resolveBookmarks(bookmarks: List<Bookmark>?): List<ResolvedBookmark> {
        val context = prepare(bookmarks)
        if (bookmarks.isNullOrEmpty()) {
            return emptyList()
        }
        return bookmarks.map { resolveBookmark(it, context) }
    }

    fun resolveBookmark(bookmark: Bookmark, context: ResolutionContext): ResolvedBookmark {
        val account = context.accountByName[bookmark.accountName]
        val renderData = context.renderDataByBookmarkId[bookmarkKey(bookmark)] ?: BookmarkRenderData.fromBookmark(bookmark)
        if (needsChannelFallback(renderData)) {
            mergeRenderData(renderData, lookupFallbackChannel(account, bookmark.channelId, context.channelByAccountAndChannel))
        }

        val accountAction = bookmark.accountAction ?: account?.action ?: Account.AccountAction.itv
        return ResolvedBookmark(bookmark, account, accountAction, renderData).apply {
            applyToBookmark()
        }
    }

    private fun resolveBookmarkRenderData(bookmark: Bookmark?): BookmarkRenderData {
        val renderData = BookmarkRenderData.fromBookmark(bookmark)
        mergeRenderData(renderData, resolveBookmarkChannelSnapshot(bookmark))
        return renderData
    }

    private fun preloadRenderData(bookmarks: List<Bookmark>?): Map<String, BookmarkRenderData> {
        if (bookmarks.isNullOrEmpty()) {
            return emptyMap()
        }
        val renderDataByBookmarkId = LinkedHashMap<String, BookmarkRenderData>()
        bookmarks.forEach { bookmark ->
            renderDataByBookmarkId[bookmarkKey(bookmark)] = resolveBookmarkRenderData(bookmark)
        }
        return renderDataByBookmarkId
    }

    private fun resolveBookmarkChannelSnapshot(bookmark: Bookmark?): Channel? {
        if (bookmark == null) {
            return null
        }
        if (isNotBlank(bookmark.channelJson)) {
            Channel.fromJson(bookmark.channelJson.orEmpty())?.let { return it }
        }
        if (isNotBlank(bookmark.vodJson)) {
            Channel.fromJson(bookmark.vodJson.orEmpty())?.let { return it }
        }
        if (isNotBlank(bookmark.seriesJson)) {
            val episode = Episode.fromJson(bookmark.seriesJson)
            if (episode != null) {
                return Channel().apply {
                    logo = episode.info?.movieImage ?: ""
                }
            }
        }
        return null
    }

    private fun needsChannelFallback(renderData: BookmarkRenderData?): Boolean =
        renderData == null ||
            isBlank(renderData.logo) ||
            isBlank(renderData.drmType) ||
            isBlank(renderData.drmLicenseUrl) ||
            isBlank(renderData.clearKeysJson) ||
            isBlank(renderData.inputstreamaddon) ||
            isBlank(renderData.manifestType)

    private fun mergeRenderData(target: BookmarkRenderData?, channel: Channel?) {
        if (target == null || channel == null) {
            return
        }
        if (isBlank(target.logo)) target.logo = channel.logo
        if (isBlank(target.drmType)) target.drmType = channel.drmType
        if (isBlank(target.drmLicenseUrl)) target.drmLicenseUrl = channel.drmLicenseUrl
        if (isBlank(target.clearKeysJson)) target.clearKeysJson = channel.clearKeysJson
        if (isBlank(target.inputstreamaddon)) target.inputstreamaddon = channel.inputstreamaddon
        if (isBlank(target.manifestType)) target.manifestType = channel.manifestType
    }

    private fun preloadFallbackChannels(
        bookmarks: List<Bookmark>?,
        accountByName: Map<String, Account>,
        renderDataByBookmarkId: Map<String, BookmarkRenderData>
    ): MutableMap<String, Channel> {
        if (bookmarks.isNullOrEmpty() || accountByName.isEmpty()) {
            return LinkedHashMap()
        }
        val channelByAccountAndChannel = LinkedHashMap<String, Channel>()
        val requestedChannelIdsByAccountId = collectFallbackChannelIds(bookmarks, accountByName, renderDataByBookmarkId)
        loadFallbackChannelsIntoCache(requestedChannelIdsByAccountId, channelByAccountAndChannel)
        return channelByAccountAndChannel
    }

    private fun collectFallbackChannelIds(
        bookmarks: List<Bookmark>,
        accountByName: Map<String, Account>,
        renderDataByBookmarkId: Map<String, BookmarkRenderData>
    ): Map<String, List<String>> {
        val requestedChannelIdsByAccountId = LinkedHashMap<String, MutableList<String>>()
        bookmarks.forEach { bookmark ->
            if (!requiresFallbackLookup(bookmark, accountByName, renderDataByBookmarkId)) {
                return@forEach
            }
            val account = accountByName[bookmark.accountName] ?: return@forEach
            requestedChannelIdsByAccountId.computeIfAbsent(account.dbId.orEmpty()) { ArrayList() }
                .add(bookmark.channelId.orEmpty())
        }
        return requestedChannelIdsByAccountId
    }

    private fun requiresFallbackLookup(
        bookmark: Bookmark?,
        accountByName: Map<String, Account>,
        renderDataByBookmarkId: Map<String, BookmarkRenderData>
    ): Boolean {
        if (bookmark == null) {
            return false
        }
        val renderData = renderDataByBookmarkId[bookmarkKey(bookmark)]
        if (!needsChannelFallback(renderData)) {
            return false
        }
        val account = accountByName[bookmark.accountName]
        return account != null && isNotBlank(account.dbId) && isNotBlank(bookmark.channelId)
    }

    private fun loadFallbackChannelsIntoCache(
        requestedChannelIdsByAccountId: Map<String, List<String>>,
        channelByAccountAndChannel: MutableMap<String, Channel>
    ) {
        requestedChannelIdsByAccountId.forEach { (accountId, channelIds) ->
            val channels = channelServiceProvider.invoke().getChannelsByChannelIdsAndAccount(channelIds, accountId, false)
            cacheChannelsByAccountAndId(accountId, channels, channelByAccountAndChannel)
        }
    }

    private fun cacheChannelsByAccountAndId(accountId: String, channels: List<Channel>, channelByAccountAndChannel: MutableMap<String, Channel>) {
        channels.forEach { channel ->
            if (isBlank(channel.channelId)) {
                return@forEach
            }
            channelByAccountAndChannel["$accountId|${channel.channelId}"] = channel
        }
    }

    private fun lookupFallbackChannel(account: Account?, channelId: String?, channelByAccountAndChannel: MutableMap<String, Channel>): Channel? {
        if (account == null || isBlank(account.dbId) || isBlank(channelId)) {
            return null
        }
        val key = account.dbId.orEmpty() + "|" + channelId
        if (channelByAccountAndChannel.containsKey(key)) {
            return channelByAccountAndChannel[key]
        }
        val channel = try {
            channelServiceProvider.invoke().getChannelByChannelIdAndAccount(channelId.orEmpty(), account.dbId.orEmpty(), false)
        } catch (_: Exception) {
            null
        }
        if (channel != null) {
            channelByAccountAndChannel[key] = channel
        }
        return channel
    }

    private fun bookmarkKey(bookmark: Bookmark?): String {
        if (bookmark == null) {
            return ""
        }
        if (isNotBlank(bookmark.dbId)) {
            return bookmark.dbId.orEmpty()
        }
        return bookmark.accountName.orEmpty() + "|" +
            bookmark.channelId.orEmpty() + "|" +
            bookmark.channelName.orEmpty()
    }

    class ResolutionContext(
        val accountByName: Map<String, Account> = emptyMap(),
        val channelByAccountAndChannel: MutableMap<String, Channel> = LinkedHashMap(),
        val renderDataByBookmarkId: Map<String, BookmarkRenderData> = emptyMap()
    )

    class ResolvedBookmark(
        val bookmark: Bookmark?,
        val account: Account?,
        val accountAction: Account.AccountAction?,
        private val renderData: BookmarkRenderData = BookmarkRenderData()
    ) {
        fun getLogo(): String? = renderData.logo
        fun getDrmType(): String? = renderData.drmType
        fun getDrmLicenseUrl(): String? = renderData.drmLicenseUrl
        fun getClearKeysJson(): String? = renderData.clearKeysJson
        fun getInputstreamaddon(): String? = renderData.inputstreamaddon
        fun getManifestType(): String? = renderData.manifestType

        fun applyToBookmark() {
            val currentBookmark = bookmark ?: return
            if (currentBookmark.accountAction == null && accountAction != null) {
                currentBookmark.accountAction = accountAction
            }
            currentBookmark.logo = renderData.logo
            currentBookmark.drmType = renderData.drmType
            currentBookmark.drmLicenseUrl = renderData.drmLicenseUrl
            currentBookmark.clearKeysJson = renderData.clearKeysJson
            currentBookmark.inputstreamaddon = renderData.inputstreamaddon
            currentBookmark.manifestType = renderData.manifestType
        }
    }

    class BookmarkRenderData {
        var logo: String? = null
        var drmType: String? = null
        var drmLicenseUrl: String? = null
        var clearKeysJson: String? = null
        var inputstreamaddon: String? = null
        var manifestType: String? = null

        companion object {
            @JvmStatic
            fun fromBookmark(bookmark: Bookmark?): BookmarkRenderData {
                val data = BookmarkRenderData()
                if (bookmark == null) {
                    return data
                }
                data.logo = bookmark.logo
                data.drmType = bookmark.drmType
                data.drmLicenseUrl = bookmark.drmLicenseUrl
                data.clearKeysJson = bookmark.clearKeysJson
                data.inputstreamaddon = bookmark.inputstreamaddon
                data.manifestType = bookmark.manifestType
                return data
            }
        }
    }
}
