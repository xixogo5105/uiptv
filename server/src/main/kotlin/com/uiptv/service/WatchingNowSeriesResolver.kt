package com.uiptv.service

import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.model.SeriesWatchingNowSnapshot
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.json.KJsonArray
import com.uiptv.util.json.KJsonObject

class WatchingNowSeriesResolver(
    private val accountService: AccountService = AccountService,
    private val seriesWatchStateService: SeriesWatchStateService = SeriesWatchStateService,
    private val seriesWatchingNowSnapshotService: SeriesWatchingNowSnapshotService = SeriesWatchingNowSnapshotService
) {
    fun resolveAll(): List<SeriesRow> =
        accountService.getAll().values.flatMap { resolveForAccount(it) }

    fun resolveForAccount(account: Account?): List<SeriesRow> {
        if (account == null || isBlank(account.dbId)) {
            return emptyList()
        }
        val deduped = dedupeSeriesStates(account.dbId.orEmpty())
        return deduped.values.mapNotNull { buildRow(account, it) }
    }

    private fun dedupeSeriesStates(accountId: String): Map<String, SeriesWatchState> {
        val deduped = LinkedHashMap<String, SeriesWatchState>()
        seriesWatchStateService.getAllSeriesLastWatchedByAccount(accountId).forEach { state ->
            if (isBlank(state.seriesId)) {
                return@forEach
            }
            val key = normalizeSeriesIdentity(state.seriesId)
            val existing = deduped[key]
            if (existing == null || state.updatedAt > existing.updatedAt) {
                deduped[key] = state
            }
        }
        return deduped
    }

    private fun buildRow(account: Account, state: SeriesWatchState): SeriesRow? {
        val scope = resolveSnapshotScope(state)
        val scopedState = copyStateWithScope(state, scope.categoryId, scope.parentChannelId)
        var cacheInfo = resolveSeriesInfoFromCache(account, scopedState)
        val snapshot: SeriesWatchingNowSnapshot? =
            seriesWatchingNowSnapshotService.getSnapshot(account.dbId, scopedState.categoryId, scopedState.seriesId)

        if (!isBlank(scope.seriesTitle)) {
            cacheInfo = SeriesCacheInfo(scope.seriesTitle, firstNonBlank(scope.seriesPoster, cacheInfo.seriesPoster), true)
        } else if (!isBlank(scope.seriesPoster)) {
            cacheInfo = SeriesCacheInfo(cacheInfo.seriesTitle, scope.seriesPoster, cacheInfo.resolvedFromCache)
        }
        if ((!cacheInfo.resolvedFromCache || isBlank(cacheInfo.seriesTitle)) && snapshot != null) {
            cacheInfo = SeriesCacheInfo(
                firstNonBlank(snapshot.seriesTitle, cacheInfo.seriesTitle),
                firstNonBlank(snapshot.seriesPoster, cacheInfo.seriesPoster),
                true
            )
        }
        if (!cacheInfo.resolvedFromCache && isAllDigits(scopedState.seriesId)) {
            return null
        }
        var categoryDbId = resolveSeriesCategoryDbId(account, scopedState.categoryId)
        if (isBlank(categoryDbId) && snapshot != null) {
            categoryDbId = safe(snapshot.categoryDbId)
        }
        if (isBlank(categoryDbId)) {
            categoryDbId = safe(scopedState.categoryId)
        }
        return SeriesRow(account, scopedState, cacheInfo.seriesTitle, cacheInfo.seriesPoster, categoryDbId, cacheInfo.resolvedFromCache)
    }

    private fun resolveSeriesInfoFromCache(account: Account, state: SeriesWatchState): SeriesCacheInfo {
        val directMatch = resolveSeriesInfoFromCandidateCategories(account, state)
        if (!needsSeriesCacheFallback(directMatch, state)) {
            return directMatch
        }
        return resolveSeriesInfoFromAllCategories(account, state, directMatch) ?: directMatch
    }

    private fun resolveSeriesInfoFromCandidateCategories(account: Account, state: SeriesWatchState): SeriesCacheInfo {
        val rawSeriesId = safe(state.seriesId)
        var defaultTitle = rawSeriesId
        val normalizedSeriesId = normalizeSeriesIdentity(rawSeriesId)
        if (!isBlank(normalizedSeriesId) && normalizedSeriesId != rawSeriesId) {
            defaultTitle = normalizedSeriesId
        }
        val seriesIdCandidates = buildSeriesIdCandidates(rawSeriesId)
        buildSeriesCategoryCandidates(account, state).forEach { categoryCandidate ->
            val match = findSeriesChannel(account, categoryCandidate, seriesIdCandidates)
            if (match != null) {
                return buildSeriesCacheInfo(match, defaultTitle, true)
            }
        }
        return SeriesCacheInfo(firstNonBlank(defaultTitle, rawSeriesId), "", false)
    }

    private fun buildSeriesCategoryCandidates(account: Account, state: SeriesWatchState): List<String> {
        val categoryDbId = resolveSeriesCategoryDbId(account, state.categoryId)
        val categoryCandidates = ArrayList<String>()
        categoryCandidates.add(safe(state.categoryId))
        if (!isBlank(categoryDbId)) {
            categoryCandidates.add(categoryDbId)
        }
        return categoryCandidates
    }

    private fun needsSeriesCacheFallback(cacheInfo: SeriesCacheInfo?, state: SeriesWatchState): Boolean =
        cacheInfo == null || isBlank(cacheInfo.seriesTitle) || cacheInfo.seriesTitle == state.seriesId

    private fun resolveSeriesInfoFromAllCategories(account: Account, state: SeriesWatchState, current: SeriesCacheInfo?): SeriesCacheInfo? {
        val seriesIdCandidates = buildSeriesIdCandidates(state.seriesId)
        val categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", arrayOf(account.dbId.orEmpty()))
        categories.forEach { category ->
            val match = findSeriesChannel(account, category.dbId, seriesIdCandidates)
            if (match != null) {
                val defaultTitle = current?.seriesTitle ?: state.seriesId.orEmpty()
                return buildSeriesCacheInfo(match, defaultTitle, true)
            }
        }
        return null
    }

    private fun findSeriesChannel(account: Account, categoryId: String?, seriesIds: List<String>?): Channel? {
        if (isBlank(categoryId) || seriesIds.isNullOrEmpty()) {
            return null
        }
        val channels = SeriesChannelDb.get().getChannels(account, categoryId.orEmpty())
        seriesIds.forEach { seriesId ->
            val target = safe(seriesId)
            if (isBlank(target)) {
                return@forEach
            }
            channels.forEach { channel ->
                if (target == safe(channel.channelId)) {
                    return channel
                }
            }
        }
        return null
    }

    private fun buildSeriesCacheInfo(match: Channel, defaultTitle: String, resolved: Boolean): SeriesCacheInfo {
        val title = firstNonBlank(match.name, defaultTitle)
        val poster = firstNonBlank(match.logo, "")
        return SeriesCacheInfo(firstNonBlank(title, defaultTitle), poster, resolved)
    }

    private fun resolveSnapshotScope(state: SeriesWatchState?): SnapshotScope {
        var categoryId = safe(state?.categoryId)
        val parentChannelId = safe(state?.seriesId)
        var title = ""
        var poster = ""
        if (state == null) {
            return SnapshotScope(categoryId, parentChannelId, title, poster)
        }
        try {
            val category = Category.fromJson(state.seriesCategorySnapshot.orEmpty())
            if (category != null) {
                categoryId = firstNonBlank(category.categoryId, categoryId)
            }
        } catch (_: Exception) {
        }
        try {
            val channel = Channel.fromJson(state.seriesChannelSnapshot.orEmpty())
            if (channel != null) {
                title = firstNonBlank(channel.name, title)
                poster = firstNonBlank(channel.logo, poster)
            }
        } catch (_: Exception) {
        }
        val snapshotData = extractSnapshotData(state.seriesChannelSnapshot)
        if (snapshotData != null) {
            title = firstNonBlank(snapshotData.title, title)
            poster = firstNonBlank(snapshotData.poster, poster)
        }
        val episodeData = extractSnapshotData(state.seriesEpisodeSnapshot)
        if (episodeData != null) {
            if (isBlank(title)) {
                title = firstNonBlank(episodeData.seriesTitle, title)
            }
            if (isBlank(poster)) {
                poster = firstNonBlank(episodeData.poster, poster)
            }
        }
        return SnapshotScope(categoryId, parentChannelId, title, poster)
    }

    private fun copyStateWithScope(source: SeriesWatchState?, categoryId: String?, parentChannelId: String?): SeriesWatchState {
        val scoped = SeriesWatchState()
        if (source == null) {
            return scoped
        }
        scoped.dbId = source.dbId
        scoped.accountId = source.accountId
        scoped.mode = source.mode
        scoped.categoryId = firstNonBlank(categoryId, source.categoryId)
        scoped.seriesId = firstNonBlank(parentChannelId, source.seriesId)
        scoped.episodeId = source.episodeId
        scoped.episodeName = source.episodeName
        scoped.season = source.season
        scoped.episodeNum = source.episodeNum
        scoped.updatedAt = source.updatedAt
        scoped.source = source.source
        scoped.seriesCategorySnapshot = source.seriesCategorySnapshot
        scoped.seriesChannelSnapshot = source.seriesChannelSnapshot
        scoped.seriesEpisodeSnapshot = source.seriesEpisodeSnapshot
        return scoped
    }

    private fun resolveSeriesCategoryDbId(account: Account?, apiCategoryId: String?): String {
        if (account == null || isBlank(account.dbId) || isBlank(apiCategoryId)) {
            return ""
        }
        val target = safe(apiCategoryId)
        val categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", arrayOf(account.dbId.orEmpty()))
        categories.forEach { category ->
            if (target == safe(category.categoryId) || target == safe(category.dbId)) {
                return safe(category.dbId)
            }
        }
        return ""
    }

    private fun normalizeSeriesIdentity(seriesId: String?): String {
        val normalized = safe(seriesId)
        if (isBlank(normalized)) {
            return ""
        }
        if (!normalized.contains(":")) {
            return normalized
        }
        val parts = normalized.split(":")
        if (areAllPartsNumeric(parts)) {
            val lastNumeric = lastNonBlank(parts)
            if (!isBlank(lastNumeric)) {
                return lastNumeric
            }
        }
        val first = firstNonBlankPart(parts)
        return if (isBlank(first)) normalized else first
    }

    private fun extractSnapshotData(snapshotJson: String?): SnapshotData? {
        val raw = safe(snapshotJson)
        if (isBlank(raw)) {
            return null
        }
        var payload: KJsonObject? = null
        try {
            payload = KJsonObject(raw)
        } catch (_: Exception) {
            try {
                val array = KJsonArray(raw)
                payload = array.optJSONObject(0)
            } catch (_: Exception) {
                return null
            }
        }
        if (payload == null) {
            return null
        }
        val title = firstNonBlank(
            safe(payload.optString("seriesTitle")),
            safe(payload.optString("seriesName")),
            safe(payload.optString("name")),
            safe(payload.optString("channelName")),
            safe(payload.optString("title"))
        )
        val seriesTitle = firstNonBlank(
            safe(payload.optString("seriesTitle")),
            safe(payload.optString("seriesName"))
        )
        val poster = firstNonBlank(
            safe(payload.optString("logo")),
            safe(payload.optString("poster")),
            safe(payload.optString("cover")),
            safe(payload.optString("movieImage"))
        )
        return SnapshotData(title, seriesTitle, poster)
    }

    private fun areAllPartsNumeric(parts: List<String>): Boolean {
        var allNumeric = true
        parts.forEach { part ->
            val p = safe(part)
            if (!isBlank(p) && !isAllDigits(p)) {
                allNumeric = false
            }
        }
        return allNumeric
    }

    private fun lastNonBlank(parts: List<String>): String {
        var last = ""
        for (i in parts.indices.reversed()) {
            val p = safe(parts[i])
            if (!isBlank(p) && isBlank(last)) {
                last = p
            }
        }
        return last
    }

    private fun firstNonBlankPart(parts: List<String>): String {
        parts.forEach { part ->
            val p = safe(part)
            if (!isBlank(p)) {
                return p
            }
        }
        return ""
    }

    private fun buildSeriesIdCandidates(seriesId: String?): List<String> {
        val raw = safe(seriesId)
        if (isBlank(raw)) {
            return emptyList()
        }
        val candidates = LinkedHashSet<String>()
        val normalized = normalizeSeriesIdentity(raw)
        if (!isBlank(normalized)) {
            candidates.add(normalized)
        }
        if (raw.contains(":")) {
            raw.split(":").forEach { part ->
                val p = safe(part)
                if (!isBlank(p)) {
                    candidates.add(p)
                }
            }
        }
        if (!raw.contains(":") && isAllDigits(raw)) {
            candidates.add("$raw:$raw")
        }
        candidates.add(raw)
        return ArrayList(candidates)
    }

    private fun firstNonBlank(vararg values: String?): String {
        values.forEach { value ->
            if (!isBlank(value)) return value.orEmpty().trim()
        }
        return ""
    }

    private fun safe(value: String?): String = value?.trim().orEmpty()

    private fun isAllDigits(value: String?): Boolean {
        val normalized = safe(value)
        return !isBlank(normalized) && DIGITS_ONLY_PATTERN.matches(normalized)
    }

    class SeriesRow(
        val account: Account,
        val state: SeriesWatchState,
        val seriesTitle: String,
        val seriesPoster: String,
        val categoryDbId: String,
        val resolvedFromCache: Boolean
    ) {
        fun isResolvedFromCache(): Boolean = resolvedFromCache
    }

    private data class SeriesCacheInfo(
        val seriesTitle: String,
        val seriesPoster: String,
        val resolvedFromCache: Boolean
    )

    private data class SnapshotScope(
        val categoryId: String = "",
        val parentChannelId: String = "",
        val seriesTitle: String = "",
        val seriesPoster: String = ""
    )

    private data class SnapshotData(
        val title: String = "",
        val seriesTitle: String = "",
        val poster: String = ""
    )

    companion object {
        private val DIGITS_ONLY_PATTERN = Regex("^\\d+$")
    }
}
