package com.uiptv.service

import com.uiptv.db.ChannelDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.shared.Episode
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import java.util.function.Supplier

class PlayerRequestResolver @JvmOverloads constructor(
    private val bookmarkService: BookmarkService = RuntimeServices.bookmarkService,
    private val accountService: AccountService = RuntimeServices.accountService,
    private val playerService: PlayerService = RuntimeServices.playerService,
    private val seriesCategoryDb: SeriesCategoryDb = SeriesCategoryDb.get(),
    private val vodChannelDb: VodChannelDb = VodChannelDb.get(),
    private val channelDb: ChannelDb = ChannelDb.get()
) {
    @Throws(IOException::class)
    fun resolveBookmarkPlayback(bookmarkId: String, mode: String?, seriesParentId: String?): PlayerResponse {
        val bookmark = bookmarkService.getBookmark(bookmarkId) ?: throw IOException("Bookmark not found")
        val account = accountService.getAll()[bookmark.accountName] ?: throw IOException("Account not found")
        applyMode(account, mode)
        bookmark.accountAction?.let { account.action = it }
        val channel = resolveBookmarkChannel(bookmark)
        val scopedCategoryId = resolveSeriesCategoryId(account, bookmark.categoryId)
        return playerService.get(account, channel, bookmark.channelId, seriesParentId, scopedCategoryId)
    }

    @Throws(IOException::class)
    fun resolveDirectPlayback(account: Account?, categoryId: String?, channelId: String?, mode: String?, seriesParentId: String?, seriesId: String?, requestChannel: Channel?): PlayerResponse {
        if (account == null) {
            throw IOException("Account not found")
        }
        applyMode(account, mode)
        val channel = mergeRequestChannel(resolveRequestedChannel(account, categoryId, channelId, mode), requestChannel)
        val scopedCategoryId = resolveSeriesCategoryId(account, categoryId)
        return playerService.get(account, channel, seriesId, seriesParentId, scopedCategoryId)
    }

    fun resolveBookmarkChannel(bookmark: Bookmark): Channel {
        return readBookmarkSnapshot(bookmark) ?: createLegacyBookmarkChannel(bookmark)
    }

    fun mergeRequestChannel(channel: Channel?, requestChannel: Channel?): Channel {
        if (requestChannel == null) return channel ?: Channel()
        if (channel == null) return requestChannel
        fillIfBlank({ channel.name }, { channel.name = it }, requestChannel.name)
        fillIfBlank({ channel.logo }, { channel.logo = it }, requestChannel.logo)
        fillIfBlank({ channel.cmd }, { channel.cmd = it }, requestChannel.cmd)
        fillIfBlank({ channel.cmd_1 }, { channel.cmd_1 = it }, requestChannel.cmd_1)
        fillIfBlank({ channel.cmd_2 }, { channel.cmd_2 = it }, requestChannel.cmd_2)
        fillIfBlank({ channel.cmd_3 }, { channel.cmd_3 = it }, requestChannel.cmd_3)
        fillIfBlank({ channel.drmType }, { channel.drmType = it }, requestChannel.drmType)
        fillIfBlank({ channel.drmLicenseUrl }, { channel.drmLicenseUrl = it }, requestChannel.drmLicenseUrl)
        fillIfBlank({ channel.clearKeysJson }, { channel.clearKeysJson = it }, requestChannel.clearKeysJson)
        fillIfBlank({ channel.inputstreamaddon }, { channel.inputstreamaddon = it }, requestChannel.inputstreamaddon)
        fillIfBlank({ channel.manifestType }, { channel.manifestType = it }, requestChannel.manifestType)
        fillIfBlank({ channel.season }, { channel.season = it }, requestChannel.season)
        fillIfBlank({ channel.episodeNum }, { channel.episodeNum = it }, requestChannel.episodeNum)
        return channel
    }

    fun resolveSeriesCategoryId(account: Account?, rawCategoryId: String?): String {
        if (account == null || account.action != Account.AccountAction.series) return ""
        if (isBlank(rawCategoryId)) return ""
        val category: Category? = seriesCategoryDb.getById(rawCategoryId.orEmpty())
        if (category != null && isNotBlank(category.categoryId)) return category.categoryId.orEmpty()
        return rawCategoryId.orEmpty()
    }

    private fun readBookmarkSnapshot(bookmark: Bookmark?): Channel? {
        if (bookmark == null) return null
        if (isNotBlank(bookmark.seriesJson)) {
            val episode = Episode.fromJson(bookmark.seriesJson)
            if (episode != null) {
                val channel = Channel()
                channel.cmd = episode.cmd
                channel.name = episode.title
                channel.channelId = episode.id
                channel.logo = episode.info?.movieImage
                return channel
            }
        }
        if (isNotBlank(bookmark.channelJson)) return Channel.fromJson(bookmark.channelJson.orEmpty())
        if (isNotBlank(bookmark.vodJson)) return Channel.fromJson(bookmark.vodJson.orEmpty())
        return null
    }

    private fun createLegacyBookmarkChannel(bookmark: Bookmark): Channel {
        val channel = Channel()
        channel.cmd = decodeBookmarkCmd(bookmark.cmd)
        channel.channelId = bookmark.channelId
        channel.name = bookmark.channelName
        channel.drmType = bookmark.drmType
        channel.drmLicenseUrl = bookmark.drmLicenseUrl
        channel.clearKeysJson = bookmark.clearKeysJson
        channel.inputstreamaddon = bookmark.inputstreamaddon
        channel.manifestType = bookmark.manifestType
        return channel
    }

    private fun decodeBookmarkCmd(value: String?): String? {
        if (isBlank(value)) return value
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            value
        }
    }

    private fun resolveRequestedChannel(account: Account?, categoryId: String?, channelId: String?, mode: String?): Channel? {
        val normalizedMode = if (isBlank(mode)) "" else mode!!.trim().lowercase()
        if (normalizedMode == "vod" && account != null) {
            val vodChannel = vodChannelDb.getChannelByChannelId(channelId.orEmpty(), categoryId.orEmpty(), account.dbId.orEmpty())
            if (vodChannel != null) return vodChannel
            return vodChannelDb.getChannelByChannelIdAndAccount(channelId.orEmpty(), account.dbId.orEmpty())
        }
        return channelDb.getChannelById(channelId.orEmpty(), categoryId.orEmpty())
    }

    private fun applyMode(account: Account?, mode: String?) {
        if (account == null || isBlank(mode)) return
        try {
            account.action = Account.AccountAction.valueOf(mode!!.lowercase())
        } catch (_: Exception) {
            account.action = Account.AccountAction.itv
        }
    }

    private fun fillIfBlank(getter: Supplier<String?>, setter: Consumer<String?>, value: String?) {
        if (isBlank(getter.get()) && isNotBlank(value)) setter.accept(value)
    }
}
