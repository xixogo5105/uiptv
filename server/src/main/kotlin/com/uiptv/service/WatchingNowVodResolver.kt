package com.uiptv.service

import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.VodWatchState
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONObject
import java.util.function.Consumer
import java.util.function.Supplier

class WatchingNowVodResolver(
    private val accountService: AccountService = AccountService,
    private val vodWatchStateService: VodWatchStateService = VodWatchStateService
) {
    fun resolveAll(): List<VodRow> =
        accountService.getAll().values.flatMap { resolveForAccount(it) }

    fun resolveForAccount(account: Account?): List<VodRow> {
        if (account == null || isBlank(account.dbId)) {
            return emptyList()
        }
        return vodWatchStateService.getAllByAccount(account.dbId.orEmpty())
            .mapNotNull { buildRow(account, it) }
    }

    private fun buildRow(account: Account?, state: VodWatchState?): VodRow? {
        if (account == null || state == null || isBlank(state.vodId)) {
            return null
        }
        val provider = resolveProviderChannel(account, state)
        val providerMetadata = resolveMetadataFromProvider(provider)
        val title = firstNonBlank(state.vodName, providerMetadata.name, state.vodId)
        val logo = firstNonBlank(state.vodLogo, providerMetadata.logo)
        val plot = firstNonBlank(providerMetadata.plot, "")
        val releaseDate = firstNonBlank(providerMetadata.releaseDate, "")
        val rating = firstNonBlank(providerMetadata.rating, "")
        val duration = firstNonBlank(providerMetadata.duration, "")
        val playbackChannel = mergePlaybackChannel(buildFallbackChannel(state), provider)
        val metadata = VodMetadata(logo, plot, releaseDate, rating, duration)
        return VodRow(account, state, playbackChannel, title, metadata)
    }

    private fun resolveMetadataFromProvider(provider: Channel?): VodMetadata {
        val builder = buildMetadataBuilder(provider)
        applyExtraJsonOverrides(builder, provider?.extraJson.orEmpty())
        return toVodMetadata(builder)
    }

    private fun buildMetadataBuilder(provider: Channel?): VodMetadataBuilder {
        val builder = VodMetadataBuilder()
        if (provider == null) {
            return builder
        }
        builder.name = safe(provider.name)
        builder.logo = safe(provider.logo)
        builder.plot = safe(provider.description)
        builder.releaseDate = safe(provider.releaseDate)
        builder.rating = safe(provider.rating)
        builder.duration = safe(provider.duration)
        return builder
    }

    private fun applyExtraJsonOverrides(builder: VodMetadataBuilder?, extraJson: String?) {
        if (builder == null || isBlank(extraJson)) {
            return
        }
        try {
            val json = JSONObject(extraJson)
            builder.name = preferIfBlank(builder.name, json.optString("name"), json.optString("o_name"))
            builder.logo = preferIfBlank(builder.logo, json.optString("stream_icon"), json.optString("cover_big"), json.optString("cover"))
            builder.plot = preferIfBlank(builder.plot, json.optString("description"), json.optString("plot"), json.optString("overview"))
            builder.releaseDate = preferIfBlank(builder.releaseDate, json.optString("release_date"), json.optString("released"), json.optString("year"))
            builder.rating = preferIfBlank(builder.rating, json.optString("rating_imdb"), json.optString("rating"))
            builder.duration = preferIfBlank(builder.duration, json.optString("duration"), json.optString("runtime"), json.optString("time"))
        } catch (_: Exception) {
        }
    }

    private fun preferIfBlank(current: String?, vararg candidates: String?): String {
        if (!isBlank(current)) {
            return current.orEmpty()
        }
        return firstNonBlank(*candidates)
    }

    private fun toVodMetadata(builder: VodMetadataBuilder?): VodMetadata {
        if (builder == null) {
            return VodMetadata("", "", "", "", "")
        }
        return VodMetadata(
            safe(builder.logo),
            safe(builder.plot),
            safe(builder.releaseDate),
            safe(builder.rating),
            safe(builder.duration),
            safe(builder.name)
        )
    }

    private fun resolveProviderChannel(account: Account, state: VodWatchState): Channel? {
        val direct = VodChannelDb.get().getChannelByChannelId(state.vodId.orEmpty(), safe(state.categoryId), account.dbId.orEmpty())
        if (direct != null) {
            return direct
        }
        val matches = VodChannelDb.get().getAll(
            " WHERE accountId=? AND channelId=?",
            arrayOf(account.dbId.orEmpty(), state.vodId.orEmpty())
        )
        return matches.firstOrNull()
    }

    private fun buildFallbackChannel(state: VodWatchState): Channel =
        Channel().apply {
            channelId = state.vodId
            categoryId = state.categoryId
            name = state.vodName
            cmd = state.vodCmd
            logo = state.vodLogo
        }

    private fun mergePlaybackChannel(primary: Channel?, fallback: Channel?): Channel? {
        if (primary == null) return fallback
        if (fallback == null) return primary
        fillIfBlank({ primary.name }, { primary.name = it }, fallback.name)
        fillIfBlank({ primary.logo }, { primary.logo = it }, fallback.logo)
        fillIfBlank({ primary.cmd }, { primary.cmd = it }, fallback.cmd)
        fillIfBlank({ primary.cmd_1 }, { primary.cmd_1 = it }, fallback.cmd_1)
        fillIfBlank({ primary.cmd_2 }, { primary.cmd_2 = it }, fallback.cmd_2)
        fillIfBlank({ primary.cmd_3 }, { primary.cmd_3 = it }, fallback.cmd_3)
        fillIfBlank({ primary.drmType }, { primary.drmType = it }, fallback.drmType)
        fillIfBlank({ primary.drmLicenseUrl }, { primary.drmLicenseUrl = it }, fallback.drmLicenseUrl)
        fillIfBlank({ primary.clearKeysJson }, { primary.clearKeysJson = it }, fallback.clearKeysJson)
        fillIfBlank({ primary.inputstreamaddon }, { primary.inputstreamaddon = it }, fallback.inputstreamaddon)
        fillIfBlank({ primary.manifestType }, { primary.manifestType = it }, fallback.manifestType)
        fillIfBlank({ primary.season }, { primary.season = it }, fallback.season)
        fillIfBlank({ primary.episodeNum }, { primary.episodeNum = it }, fallback.episodeNum)
        if (isBlank(primary.categoryId)) {
            primary.categoryId = fallback.categoryId
        }
        return primary
    }

    private fun fillIfBlank(getter: Supplier<String?>, setter: Consumer<String?>, value: String?) {
        if (isBlank(getter.get()) && !isBlank(value)) {
            setter.accept(value)
        }
    }

    private fun firstNonBlank(vararg values: String?): String {
        values.forEach { value ->
            if (!isBlank(value)) {
                return value.orEmpty().trim()
            }
        }
        return ""
    }

    private fun safe(value: String?): String = value?.trim().orEmpty()

    private class VodMetadataBuilder {
        var logo: String = ""
        var plot: String = ""
        var releaseDate: String = ""
        var rating: String = ""
        var duration: String = ""
        var name: String = ""
    }

    class VodRow(
        val account: Account,
        val state: VodWatchState,
        val playbackChannel: Channel?,
        val displayTitle: String,
        val metadata: VodMetadata
    )

    class VodMetadata(
        val logo: String,
        val plot: String,
        val releaseDate: String,
        val rating: String,
        val duration: String,
        val name: String = ""
    )
}
