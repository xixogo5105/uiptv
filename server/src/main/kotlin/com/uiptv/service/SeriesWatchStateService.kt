package com.uiptv.service

import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesChannelDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.SeriesWatchStateDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.util.StringUtils
import com.uiptv.util.json.parseJsonObject
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArraySet
import java.util.regex.Pattern
import com.uiptv.model.Account.AccountAction.series
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SeriesWatchStateService {
    private const val FIELD_CATEGORY_ID = "categoryId"
    private const val SOURCE_MANUAL = "MANUAL"
    private val SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b")
    private val SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b")
    private val EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b")
    private val listeners = CopyOnWriteArraySet<SeriesWatchStateChangeListener>()

    fun getSeriesLastWatched(accountId: String?, seriesId: String?): SeriesWatchState? =
        getSeriesLastWatched(accountId, "", seriesId)
    fun getSeriesLastWatched(accountId: String?, categoryId: String?, seriesId: String?): SeriesWatchState? {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(seriesId)) {
            return null
        }
        val canonicalSeriesId = canonicalizeSeriesId(seriesId)
        val normalizedCategory = normalizeCategoryId(accountId.orEmpty(), categoryId)
        val exact = SeriesWatchStateDb.get().getBySeries(accountId.orEmpty(), normalizedCategory, canonicalSeriesId)
        if (exact != null) {
            return exact
        }
        var latest: SeriesWatchState? = null
        for (candidate in SeriesWatchStateDb.get().getBySeries(accountId.orEmpty(), canonicalSeriesId)) {
            if (latest == null || candidate.updatedAt > latest.updatedAt) {
                latest = candidate
            }
        }
        return latest
    }
    fun getSeriesLastWatchedByAccount(accountId: String?): Map<String, SeriesWatchState> =
        getSeriesLastWatchedByAccountAndCategory(accountId, "")
    fun getAllSeriesLastWatchedByAccount(accountId: String?): List<SeriesWatchState> {
        if (StringUtils.isBlank(accountId)) {
            return emptyList()
        }
        return SeriesWatchStateDb.get().getByAccount(accountId.orEmpty())
    }
    fun getSeriesLastWatchedByAccountAndCategory(accountId: String?, categoryId: String?): Map<String, SeriesWatchState> {
        val result = LinkedHashMap<String, SeriesWatchState>()
        if (StringUtils.isBlank(accountId)) {
            return result
        }
        val allCategories = StringUtils.isBlank(categoryId)
        val states =
            if (allCategories) {
                SeriesWatchStateDb.get().getByAccount(accountId.orEmpty())
            } else {
                SeriesWatchStateDb.get().getByAccount(accountId.orEmpty(), normalizeCategoryId(accountId.orEmpty(), categoryId))
            }
        states.forEach { state ->
            if (StringUtils.isNotBlank(state.seriesId)) {
                result[state.seriesId.orEmpty()] = state
            }
        }
        return result
    }
    fun clearSeriesLastWatched(accountId: String?, seriesId: String?) {
        clearSeriesLastWatched(accountId, "", seriesId)
    }
    fun clearSeriesLastWatched(accountId: String?, categoryId: String?, seriesId: String?) {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(seriesId)) {
            return
        }
        val canonicalSeriesId = canonicalizeSeriesId(seriesId)
        val normalizedCategory = normalizeCategoryId(accountId.orEmpty(), categoryId)
        SeriesWatchStateDb.get().clear(accountId.orEmpty(), normalizedCategory, canonicalSeriesId)
        SeriesWatchingNowSnapshotService.clear(accountId.orEmpty(), normalizedCategory, canonicalSeriesId)
        notifyListeners(accountId.orEmpty(), canonicalSeriesId)
    }
    fun clearAllSeriesLastWatched() {
        SeriesWatchStateDb.get().clearAllSeries()
        SeriesWatchingNowSnapshotService.clearAll()
        notifyListeners("", "")
    }
    fun markSeriesEpisodeManual(account: Account?, seriesId: String?, episodeId: String?, episodeName: String?, season: String?, episodeNum: String?) {
        markSeriesEpisodeManual(account, "", seriesId, episodeId, episodeName, season, episodeNum)
    }
    fun markSeriesEpisodeManual(account: Account?, categoryId: String?, seriesId: String?, episodeId: String?, episodeName: String?, season: String?, episodeNum: String?) {
        if (account == null || StringUtils.isBlank(account.dbId) || StringUtils.isBlank(seriesId) || StringUtils.isBlank(episodeId)) {
            return
        }
        upsertState(account.dbId.orEmpty(), normalizeCategoryId(account.dbId.orEmpty(), categoryId), canonicalizeSeriesId(seriesId), episodeId.orEmpty(), episodeName, season, parseEpisodeNum(episodeNum, episodeName), SOURCE_MANUAL)
    }
    fun markSeriesEpisodeManualIfNewer(account: Account?, categoryId: String?, seriesId: String?, episodeId: String?, episodeName: String?, season: String?, episodeNum: String?) {
        if (account == null || StringUtils.isBlank(account.dbId) || StringUtils.isBlank(seriesId) || StringUtils.isBlank(episodeId)) {
            return
        }
        val normalizedCategory = normalizeCategoryId(account.dbId.orEmpty(), categoryId)
        val canonicalSeriesId = canonicalizeSeriesId(seriesId)
        val nextEpisodeNum = parseEpisodeNum(episodeNum, episodeName)
        val nextSeasonNum = parseSeasonNum(season, episodeName)
        val existing = SeriesWatchStateDb.get().getBySeries(account.dbId.orEmpty(), normalizedCategory, canonicalSeriesId)
        if (existing == null) {
            upsertState(account.dbId.orEmpty(), normalizedCategory, canonicalSeriesId, episodeId.orEmpty(), episodeName, season, nextEpisodeNum, SOURCE_MANUAL)
            return
        }
        val currentSeasonNum = parseSeasonNum(existing.season, existing.episodeName)
        val currentEpisodeNum = existing.episodeNum
        if (nextEpisodeNum <= 0 && nextSeasonNum <= 0) {
            return
        }
        if (shouldAdvancePointer(currentSeasonNum, currentEpisodeNum, nextSeasonNum, nextEpisodeNum)) {
            upsertState(account.dbId.orEmpty(), normalizedCategory, canonicalSeriesId, episodeId.orEmpty(), episodeName, season, nextEpisodeNum, SOURCE_MANUAL)
        }
    }
    fun onPlaybackResolved(account: Account?, channel: Channel?, requestedSeriesId: String?, parentSeriesId: String?) {
        onPlaybackResolved(account, channel, requestedSeriesId, parentSeriesId, "")
    }
    fun onPlaybackResolved(account: Account?, channel: Channel?, requestedSeriesId: String?, parentSeriesId: String?, categoryId: String?) {
        if (account == null || channel == null || account.action != series || StringUtils.isBlank(account.dbId)) {
            return
        }
        val resolvedCategoryId = normalizeCategoryId(account.dbId.orEmpty(), categoryId)
        val seriesId = canonicalizeSeriesId(firstNonBlank(parentSeriesId))
        if (StringUtils.isBlank(seriesId)) {
            return
        }
        val episodeId = channel.channelId
        if (StringUtils.isBlank(episodeId)) {
            return
        }
        if (seriesId.trim() == episodeId.orEmpty().trim()) {
            return
        }
        val nextEpisodeNum = parseEpisodeNum(channel.episodeNum, channel.name)
        val nextSeasonNum = parseSeasonNum(channel.season, channel.name)
        val existing = SeriesWatchStateDb.get().getBySeries(account.dbId.orEmpty(), resolvedCategoryId, seriesId)
        if (existing == null) {
            upsertState(account.dbId.orEmpty(), resolvedCategoryId, seriesId, episodeId.orEmpty(), channel.name, channel.season, nextEpisodeNum, "AUTO")
            return
        }
        val currentSeasonNum = parseSeasonNum(existing.season, existing.episodeName)
        val currentEpisodeNum = existing.episodeNum
        if (nextEpisodeNum <= 0 && nextSeasonNum <= 0) {
            return
        }
        if (shouldAdvancePointer(currentSeasonNum, currentEpisodeNum, nextSeasonNum, nextEpisodeNum)) {
            upsertState(account.dbId.orEmpty(), resolvedCategoryId, seriesId, episodeId.orEmpty(), channel.name, channel.season, nextEpisodeNum, "AUTO")
        }
    }
    fun addChangeListener(listener: SeriesWatchStateChangeListener?) {
        listener?.let(listeners::add)
    }
    fun removeChangeListener(listener: SeriesWatchStateChangeListener?) {
        listener?.let(listeners::remove)
    }

    private fun upsertState(accountId: String, categoryId: String, seriesId: String, episodeId: String, episodeName: String?, season: String?, episodeNum: Int, source: String) {
        val canonicalSeriesId = canonicalizeSeriesId(seriesId)
        val normalizedSeason = normalizeSeason(season, episodeName)
        val normalizedEpisodeNum = if (episodeNum > 0) episodeNum else parseEpisodeNum("", episodeName)
        val state = SeriesWatchState()
        state.accountId = accountId
        state.mode = "series"
        state.categoryId = normalizeCategoryId(accountId, categoryId)
        state.seriesId = canonicalSeriesId
        state.episodeId = episodeId
        state.episodeName = episodeName
        state.season = normalizedSeason
        state.episodeNum = normalizedEpisodeNum
        state.updatedAt = System.currentTimeMillis()
        state.source = source
        val existing = SeriesWatchStateDb.get().getBySeries(accountId, state.categoryId.orEmpty(), state.seriesId.orEmpty())
        applySnapshots(state, existing)
        SeriesWatchStateDb.get().upsert(state)
        notifyListeners(accountId, canonicalSeriesId)
    }

    private fun applySnapshots(state: SeriesWatchState?, existing: SeriesWatchState?) {
        if (state == null || StringUtils.isBlank(state.accountId) || StringUtils.isBlank(state.seriesId)) return
        val account = Account()
        account.dbId = state.accountId
        val matchedCategory = findCategory(account.dbId.orEmpty(), state.categoryId)
        val portalCategoryId = resolvePortalCategoryId(matchedCategory, state)
        state.seriesCategorySnapshot = buildCategorySnapshot(matchedCategory, portalCategoryId, existing)
        state.seriesChannelSnapshot = buildSeriesChannelSnapshot(account, portalCategoryId, state, matchedCategory, existing)
        state.seriesEpisodeSnapshot = buildEpisodeSnapshot(account, portalCategoryId, state, matchedCategory, existing)
    }

    private fun resolvePortalCategoryId(matchedCategory: Category?, state: SeriesWatchState?): String {
        val fallback = normalizeCategoryId(state?.categoryId)
        val categoryId = safe(matchedCategory?.categoryId)
        return if (StringUtils.isBlank(categoryId)) fallback else categoryId
    }

    private fun buildCategorySnapshot(matchedCategory: Category?, portalCategoryId: String, existing: SeriesWatchState?): String {
        if (matchedCategory == null) {
            return resolveSnapshotFallback("", existing?.seriesCategorySnapshot)
        }
        return enrichSnapshot(matchedCategory.toJson(), FIELD_CATEGORY_ID to portalCategoryId)
    }

    private fun buildSeriesChannelSnapshot(account: Account, portalCategoryId: String, state: SeriesWatchState, matchedCategory: Category?, existing: SeriesWatchState?): String {
        val seriesChannel = findSeriesChannel(account, portalCategoryId, state.seriesId, matchedCategory)
        if (seriesChannel == null) {
            return resolveSnapshotFallback("", existing?.seriesChannelSnapshot)
        }
        return enrichSnapshot(
            seriesChannel.toJson(),
            FIELD_CATEGORY_ID to portalCategoryId,
            "channelId" to safe(seriesChannel.channelId)
        )
    }

    private fun buildEpisodeSnapshot(account: Account, portalCategoryId: String, state: SeriesWatchState, matchedCategory: Category?, existing: SeriesWatchState?): String {
        val episodeChannel = findEpisode(account, portalCategoryId, state.seriesId, state.episodeId, matchedCategory)
        if (episodeChannel == null) {
            val episodeFallback = if (existing != null && safe(existing.episodeId) == safe(state.episodeId)) existing.seriesEpisodeSnapshot else ""
            return resolveSnapshotFallback("", episodeFallback)
        }
        return enrichSnapshot(
            episodeChannel.toJson(),
            FIELD_CATEGORY_ID to portalCategoryId,
            "seriesId" to safe(state.seriesId),
            "channelId" to safe(episodeChannel.channelId)
        )
    }

    private fun enrichSnapshot(rawJson: String, vararg extraFields: Pair<String, String>): String {
        val parsed = parseJsonObject(rawJson)
        return buildJsonObject {
            parsed?.forEach { (key, value) -> put(key, value) }
            extraFields.forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    private fun resolveSnapshotFallback(currentSnapshot: String?, fallbackSnapshot: String?): String {
        val current = safe(currentSnapshot)
        if (StringUtils.isNotBlank(current)) return current
        val fallback = safe(fallbackSnapshot)
        return if (StringUtils.isBlank(fallback)) "" else fallback
    }

    private fun normalizeSeriesId(seriesId: String?): String {
        val raw = seriesId?.trim().orEmpty()
        if (StringUtils.isBlank(raw) || !raw.contains(":")) return raw
        val last = raw.split(":").asReversed().firstOrNull { StringUtils.isNotBlank(it.trim()) }?.trim().orEmpty()
        return if (StringUtils.isBlank(last)) raw else last
    }

    private fun canonicalizeSeriesId(seriesId: String?): String = normalizeSeriesId(seriesId)

    private fun findCategory(accountId: String, categoryId: String?): Category? {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(categoryId)) return null
        val target = safe(categoryId)
        for (category in SeriesCategoryDb.get().getAll(" WHERE accountId=?", arrayOf(accountId))) {
            if (target == safe(category.categoryId) || target == safe(category.dbId)) {
                return category
            }
        }
        return null
    }

    private fun findSeriesChannel(account: Account, portalCategoryId: String, parentChannelId: String?, matchedCategory: Category?): Channel? {
        if (StringUtils.isBlank(account.dbId) || StringUtils.isBlank(parentChannelId)) return null
        val seriesIdCandidates = buildSeriesIdCandidates(parentChannelId)
        if (seriesIdCandidates.isEmpty()) return null
        val candidateIds = buildCandidateIdSet(seriesIdCandidates)
        if (candidateIds.isEmpty()) return null
        val categoryKeys = buildCategoryKeys(portalCategoryId, matchedCategory)
        for (key in categoryKeys) {
            for (channel in SeriesChannelDb.get().getChannels(account, key)) {
                if (candidateIds.contains(safe(channel.channelId))) return channel
            }
        }
        return null
    }

    private fun findEpisode(account: Account, portalCategoryId: String, parentChannelId: String?, episodeId: String?, matchedCategory: Category?): Channel? {
        if (StringUtils.isBlank(account.dbId) || StringUtils.isBlank(parentChannelId) || StringUtils.isBlank(episodeId)) return null
        val seriesIdCandidates = buildSeriesIdCandidates(parentChannelId)
        if (seriesIdCandidates.isEmpty()) return null
        val categoryKeys = buildCategoryKeys(portalCategoryId, matchedCategory)
        for (key in categoryKeys) {
            for (candidate in seriesIdCandidates) {
                for (channel in SeriesEpisodeDb.get().getEpisodes(account, key, candidate)) {
                    if (safe(episodeId) == safe(channel.channelId)) return channel
                }
            }
        }
        return null
    }

    private fun buildSeriesIdCandidates(seriesId: String?): List<String> {
        val raw = safe(seriesId)
        if (StringUtils.isBlank(raw)) return emptyList()
        val candidates = LinkedHashSet<String>()
        if (raw.contains(":")) {
            val parts = raw.split(":")
            if (areAllPartsNumeric(parts)) {
                val last = lastNonBlank(parts)
                if (StringUtils.isNotBlank(last)) candidates.add(last)
            }
            addNonBlankParts(candidates, parts)
        }
        if (!raw.contains(":") && raw.matches(Regex("^\\d+$"))) {
            candidates.add("$raw:$raw")
        }
        candidates.add(raw)
        return ArrayList(candidates)
    }

    private fun buildCandidateIdSet(candidates: List<String>?): Set<String> {
        val candidateIds = LinkedHashSet<String>()
        candidates?.forEach { candidate ->
            val normalized = safe(candidate)
            if (StringUtils.isNotBlank(normalized)) candidateIds.add(normalized)
        }
        return candidateIds
    }

    private fun buildCategoryKeys(portalCategoryId: String?, matchedCategory: Category?): List<String> {
        val categoryKeys = ArrayList<String>()
        if (StringUtils.isNotBlank(portalCategoryId)) categoryKeys.add(portalCategoryId.orEmpty().trim())
        if (matchedCategory != null && StringUtils.isNotBlank(matchedCategory.dbId)) categoryKeys.add(matchedCategory.dbId.orEmpty())
        return categoryKeys
    }

    private fun areAllPartsNumeric(parts: List<String>): Boolean =
        parts.all { part -> val p = safe(part); StringUtils.isBlank(p) || p.matches(Regex("^\\d+$")) }

    private fun lastNonBlank(parts: List<String>): String = parts.asReversed().firstOrNull { StringUtils.isNotBlank(safe(it)) }?.trim().orEmpty()

    private fun addNonBlankParts(candidates: MutableSet<String>, parts: List<String>) {
        parts.forEach { part ->
            val value = safe(part)
            if (StringUtils.isNotBlank(value)) candidates.add(value)
        }
    }
    fun isMatchingEpisode(watchedState: SeriesWatchState?, episodeId: String?, season: String?, episodeNum: String?, episodeName: String?): Boolean {
        if (watchedState == null || StringUtils.isBlank(watchedState.episodeId) || StringUtils.isBlank(episodeId)) return false
        if (watchedState.episodeId.orEmpty().trim() != episodeId.orEmpty().trim()) return false
        val watchedSeason = stripToDigits(watchedState.season)
        val candidateSeason = stripToDigits(normalizeSeason(season, episodeName))
        if (StringUtils.isNotBlank(watchedSeason) && (StringUtils.isBlank(candidateSeason) || watchedSeason != candidateSeason)) return false
        val watchedEpisodeNum = if (watchedState.episodeNum > 0) watchedState.episodeNum.toString() else ""
        val candidateEpisodeNum = stripToDigits(if (StringUtils.isNotBlank(episodeNum)) episodeNum else parseEpisodeNum("", episodeName).toString())
        return StringUtils.isBlank(watchedEpisodeNum) || (StringUtils.isNotBlank(candidateEpisodeNum) && watchedEpisodeNum == candidateEpisodeNum)
    }

    private fun normalizeCategoryId(categoryId: String?): String = if (StringUtils.isBlank(categoryId)) "" else categoryId.orEmpty().trim()

    private fun normalizeCategoryId(accountId: String, categoryId: String?): String {
        val normalized = normalizeCategoryId(categoryId)
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(normalized)) return normalized
        for (category in SeriesCategoryDb.get().getAll(" WHERE accountId=?", arrayOf(accountId))) {
            val dbId = safe(category.dbId)
            val apiId = safe(category.categoryId)
            if (normalized == dbId || normalized == apiId) return if (StringUtils.isBlank(apiId)) normalized else apiId
        }
        return normalized
    }

    private fun safe(value: String?): String = value?.trim().orEmpty()

    private fun notifyListeners(accountId: String, seriesId: String) {
        listeners.forEach { listener ->
            try {
                listener.onSeriesWatchStateChanged(accountId, seriesId)
            } catch (_: Exception) {
            }
        }
    }
    fun parseEpisodeNum(explicitEpisodeNum: String?, fallbackTitle: String?): Int {
        val onlyDigits = stripToDigits(explicitEpisodeNum)
        if (StringUtils.isNotBlank(onlyDigits)) return onlyDigits.toInt()
        val title = fallbackTitle.orEmpty()
        val sxey = SXXEYY_PATTERN.matcher(title)
        if (sxey.find() && StringUtils.isNotBlank(sxey.group(2))) return sxey.group(2).toInt()
        val episodeMatcher = EPISODE_PATTERN.matcher(title)
        if (episodeMatcher.find()) {
            val parsed = firstNonBlank(episodeMatcher.group(1), episodeMatcher.group(2))
            if (StringUtils.isNotBlank(parsed)) return parsed.toInt()
        }
        return 0
    }
    fun parseSeasonNum(explicitSeason: String?, fallbackTitle: String?): Int {
        val normalized = normalizeSeason(explicitSeason, fallbackTitle)
        if (StringUtils.isBlank(normalized)) return 0
        return normalized.toIntOrNull() ?: 0
    }
    fun shouldAdvancePointer(currentSeasonNum: Int, currentEpisodeNum: Int, nextSeasonNum: Int, nextEpisodeNum: Int): Boolean {
        if (nextSeasonNum > 0 && currentSeasonNum > 0) {
            if (nextSeasonNum > currentSeasonNum) return true
            if (nextSeasonNum < currentSeasonNum) return false
            if (nextEpisodeNum <= 0) return false
            return currentEpisodeNum <= 0 || nextEpisodeNum > currentEpisodeNum
        }
        if (nextEpisodeNum <= 0) return false
        return currentEpisodeNum <= 0 || nextEpisodeNum > currentEpisodeNum
    }

    private fun normalizeSeason(explicitSeason: String?, fallbackTitle: String?): String {
        val fromValue = stripToDigits(explicitSeason)
        if (StringUtils.isNotBlank(fromValue)) return fromValue.toInt().toString()
        val title = fallbackTitle.orEmpty()
        val sxey = SXXEYY_PATTERN.matcher(title)
        if (sxey.find() && StringUtils.isNotBlank(sxey.group(1))) return sxey.group(1).toInt().toString()
        val seasonMatcher = SEASON_PATTERN.matcher(title)
        if (seasonMatcher.find()) {
            val parsed = firstNonBlank(seasonMatcher.group(1), seasonMatcher.group(2), seasonMatcher.group(3))
            if (StringUtils.isNotBlank(parsed)) return parsed.toInt().toString()
        }
        return ""
    }

    private fun stripToDigits(value: String?): String {
        if (StringUtils.isBlank(value)) return ""
        val parsed = value.orEmpty().replace(Regex("\\D"), "")
        return if (StringUtils.isBlank(parsed)) "" else parsed
    }

    private fun firstNonBlank(vararg values: String?): String {
        values.forEach { value ->
            if (StringUtils.isNotBlank(value)) return value.orEmpty().trim()
        }
        return ""
    }
}
