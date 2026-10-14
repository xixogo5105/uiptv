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
