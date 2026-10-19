package com.uiptv.mobile.shared.epg

/** All platforms use UTC epoch milliseconds and exact ids, then unique normalized names. */
data class MobileEpgProgramme(val title: String, val description: String, val start: Long, val stop: Long)
enum class MobileEpgStatus { DISABLED, AVAILABLE, NO_MATCH, UNAVAILABLE, STALE }
data class MobileEpgResult(
    val status: MobileEpgStatus = MobileEpgStatus.DISABLED,
    val programmes: List<MobileEpgProgramme> = emptyList(),
    val message: String = ""
) {
    fun now(time: Long) = programmes.lastOrNull { it.start <= time && time < it.stop }
    fun next(time: Long) = programmes.firstOrNull { it.start > time }
}
data class MobileXmltvGuide(
    val programmes: Map<String, List<MobileEpgProgramme>>,
    val names: Map<String, String>,
    val qualityNames: Map<String, String> = qualityAliases(names),
    val icons: Map<String, String> = emptyMap(),
    val nameRules: MobileEpgNameRules? = null
) {
    private var rulesView: MobileXmltvGuide? = null
    fun withNameRules(rules: MobileEpgNameRules): MobileXmltvGuide {
        if (nameRules?.canonical == rules.canonical) return this
        rulesView?.takeIf { it.nameRules?.canonical == rules.canonical }?.let { return it }
        return copy(nameRules=rules).also { rulesView=it }
    }
    private val displayAliases by lazy { nameRules?.aliases(names,false).orEmpty() }
    private val ruleQualityAliases by lazy { nameRules?.aliases(names,true).orEmpty() }
    private fun nameId(name: String): String? {
        fun find(candidate: String): String? = names[normalize(candidate)] ?: if (nameRules != null)
            displayAliases[nameRules.display(candidate)] ?: ruleQualityAliases[nameRules.quality(candidate)]
            else names[normalizeDisplayName(candidate)] ?: qualityNames[normalizeQuality(candidate)]
        return find(name) ?: find(stripLeadingPlaylistIndex(name))
    }
    fun forChannel(id: String, name: String): List<MobileEpgProgramme> =
        programmes[id] ?: nameId(name)?.let { programmes[it].orEmpty() }.orEmpty()
    private val iconAliases by lazy { qualityAliases(names.filterValues { icons.containsKey(it) }) }
    private val ruleIconDisplayAliases by lazy { nameRules?.aliases(names.filterValues { icons.containsKey(it) },false).orEmpty() }
    private val ruleIconQualityAliases by lazy { nameRules?.aliases(names.filterValues { icons.containsKey(it) },true).orEmpty() }
    fun iconForChannel(id: String, name: String): String = icons[id] ?: icons[names[normalize(name)]]
        ?: if (nameRules==null) icons[nameId(name)] ?: icons[iconAliases[normalizeQuality(name)]].orEmpty()
        else icons[ruleIconDisplayAliases[nameRules.display(name)]] ?: icons[ruleIconQualityAliases[nameRules.quality(name)]].orEmpty()
    companion object {
        private val guideIdPlaceholders = setOf("", "DEFAULT", "NONE", "N/A", "NA", "UNKNOWN", "UNAVAILABLE", "NULL", "-",
            "NO DETAILS AVAILABLE", "NO INFORMATION AVAILABLE", "NO PROGRAMME DETAILS AVAILABLE",
            "NO PROGRAM DETAILS AVAILABLE", "NO INFORMATION", "NO INFO", "NO DATA")

        /** Provider feeds commonly emit these values when no XMLTV identity exists. */
        fun usableGuideIdentity(value: String?): String {
            val trimmed = value?.trim().orEmpty()
            return if (trimmed.uppercase() in guideIdPlaceholders) "" else trimmed
        }

        fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
        fun stripLeadingPlaylistIndex(value: String): String = value.trim().replace(Regex("^\\d{1,4}\\s+(?=\\p{L})"), "").trim()
        fun normalizeDisplayName(value: String): String = normalize(value)
            .replace(Regex("\\s*\\((?:144|240|360|480|540|576|720|1080|1440|2160|4320)[pi]\\)"), "")
            .replace(Regex("\\s*\\[geo-blocked\\]"), "").trim()
        fun normalizeQuality(value: String): String = normalize(normalizeDisplayName(value)
            .replace(Regex("(?<![\\p{L}\\p{N}])(?:fhd|uhd|hd|sd|4k|8k|(?:144|240|360|480|540|576|720|1080|1440|2160|4320)[pi])(?![\\p{L}\\p{N}])"), " ")
            .replace(Regex("\\([\\s|/,-]*\\)|\\[[\\s|/,-]*\\]"), " ")
            .replace(Regex("^[\\s|/,-]+|[\\s|/,-]+$"), "")
            .replace(Regex("[Ⓐ-Ⓩⓐ-ⓩ]"), "")
            .replace(Regex("[^\\p{L}\\p{N}+]"), ""))
        fun qualityAliases(names: Map<String, String>): Map<String, String> {
            val result = mutableMapOf<String, String>(); val ambiguous = mutableSetOf<String>()
            names.forEach { (name, id) ->
                val key = normalizeQuality(name)
                val previous = result.put(key, id)
                if (previous != null && previous != id) ambiguous += key
            }
            ambiguous.forEach(result::remove); result.remove(""); return result.toMap()
        }
    }
}
interface MobileEpgRepository {
    suspend fun schedule(accountId: Long, providerChannelId: String, name: String): MobileEpgResult
    suspend fun clearCache()
    suspend fun globalSource(): String
    suspend fun saveGlobalSource(source: String)
    suspend fun saveMapping(accountId: Long, providerChannelId: String, guideId: String)
}

object MobileEpgSchedules {
    /** Generic filler titles that say nothing about the programme. They are only used as a last
     * resort when every overlapping programme has one of these titles; a real title always wins. */
    private val genericTitles = setOf(
        "movie", "film", "programme", "program", "video", "movies", "films", "videos",
        "programmes", "programs", "programme name", "program name", "channel", "channels",
        "live", "show", "shows", "tv", "tv show", "tv shows", "broadcast", "broadcasts",
        "content", "contents", "stream", "streaming", "event", "events", "episode", "episodes",
        "series", "special", "specials", "movie night", "movie time", "watch now", "now playing",
        "feature", "features", "presentation", "presentation", "unknown", "untitled", "title",
        "title name", "name", "default", "default title", "placeholder", "tbd", "n/a", "na",
        "no information", "no information available", "no programme details available",
        "no program details available", "programme information unavailable", "program information unavailable",
        "schedule unavailable", "not available", "no info", "no data",
        "movie of the day", "movie of the week", "feature presentation", "feature film"
    )
    fun isGenericTitle(title: String) = title.trim().lowercase() in genericTitles
    private fun generic(title: String) = isGenericTitle(title)
    /** Source priority wins unless an overlapping entry has a meaningful title. */
    fun preferSources(sources: List<List<MobileEpgProgramme>>): List<MobileEpgProgramme> {
        data class Candidate(val programme: MobileEpgProgramme, val source: Int)
        val ordered = sources.flatMapIndexed { source, programmes -> programmes.map { Candidate(it, source) } }
            .filter { it.programme.stop > it.programme.start }.sortedWith(
                compareBy<Candidate> { generic(it.programme.title) }.thenBy { it.source }
                    .thenByDescending { it.programme.start }.thenBy { it.programme.stop }
                    .thenBy { it.programme.title }.thenBy { it.programme.description })
        val starts = mutableMapOf<Long, MutableList<Int>>()
        val stops = mutableMapOf<Long, MutableList<Int>>()
        ordered.forEachIndexed { index, candidate ->
            starts.getOrPut(candidate.programme.start) { mutableListOf() }.add(index)
            stops.getOrPut(candidate.programme.stop) { mutableListOf() }.add(index)
        }
        val times = (starts.keys + stops.keys).sorted()
        val active = mutableSetOf<Int>()
        val result = mutableListOf<MobileEpgProgramme>()
        var previousWinner: Int? = null
        for (i in 0 until times.size - 1) {
            val start = times[i]; val stop = times[i + 1]
            active.removeAll(stops[start].orEmpty().toSet()); active.addAll(starts[start].orEmpty())
            val winner = active.minOrNull()
            if (winner == null) { previousWinner = null; continue }
            if (winner == previousWinner && result.last().stop == start) result[result.lastIndex] = result.last().copy(stop = stop)
            else result += ordered[winner].programme.copy(start = start, stop = stop)
            previousWinner = winner
        }
        return result
    }

    fun normalize(input: List<MobileEpgProgramme>): List<MobileEpgProgramme> {
        val ordered = input.filter { it.stop > it.start }.distinct().sortedWith(
            compareBy<MobileEpgProgramme> { generic(it.title) }.thenByDescending { it.start }
                .thenByDescending { it.stop }.thenBy { it.title }.thenBy { it.description })
        val starts = mutableMapOf<Long, MutableList<Int>>()
        val stops = mutableMapOf<Long, MutableList<Int>>()
        ordered.forEachIndexed { index, p ->
            starts.getOrPut(p.start) { mutableListOf() }.add(index)
            stops.getOrPut(p.stop) { mutableListOf() }.add(index)
        }
        val times = (starts.keys + stops.keys).sorted()
        val active = mutableSetOf<Int>()
        val result = mutableListOf<MobileEpgProgramme>()
        var previousWinner: Int? = null
        for (i in 0 until times.size - 1) {
            val start = times[i]; val stop = times[i + 1]
            active.removeAll(stops[start].orEmpty().toSet()); active.addAll(starts[start].orEmpty())
            val winner = active.minOrNull()
            if (winner == null) { previousWinner = null; continue }
            if (winner == previousWinner && result.last().stop == start) result[result.lastIndex] = result.last().copy(stop = stop)
            else result += ordered[winner].copy(start = start, stop = stop)
            previousWinner = winner
        }
        return result.toList()
    }
}
