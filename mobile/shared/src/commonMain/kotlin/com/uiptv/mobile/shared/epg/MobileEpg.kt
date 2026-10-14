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
    val qualityNames: Map<String, String> = qualityAliases(names)
) {
    fun forChannel(id: String, name: String): List<MobileEpgProgramme> =
        programmes[id] ?: names[normalize(name)]?.let { programmes[it].orEmpty() }
            ?: programmes[qualityNames[normalizeQuality(name)]].orEmpty()
    companion object {
        fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
        fun normalizeQuality(value: String): String = normalize(normalize(value)
            .replace(Regex("(?<![\\p{L}\\p{N}])(?:fhd|uhd|hd|sd|4k|8k|720p|1080p|2160p)(?![\\p{L}\\p{N}])"), " ")
            .replace(Regex("\\([\\s|/,-]*\\)|\\[[\\s|/,-]*\\]"), " ")
            .replace(Regex("^[\\s|/,-]+|[\\s|/,-]+$"), ""))
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
        "movie of the day", "movie of the week", "feature presentation", "feature film"
    )
    private fun generic(title: String) = title.trim().lowercase() in genericTitles
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
