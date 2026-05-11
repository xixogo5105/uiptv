package com.uiptv.service

import com.uiptv.util.HttpUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import org.json.JSONArray
import org.json.JSONObject

object LogoResolverService {
    private const val CHANNELS_CATALOG_URL = "https://iptv-org.github.io/api/channels.json"
    private const val LOGOS_CATALOG_URL = "https://raw.githubusercontent.com/iptv-org/database/master/data/logos.csv"
    private const val CATALOG_REFRESH_MS = 24L * 60L * 60L * 1000L
    private val NOISE_TOKENS = setOf(
        "uhd", "fhd", "hd", "sd", "hq", "4k", "8k",
        "hevc", "h264", "h265", "x264", "x265",
        "tv", "channel", "live", "official",
        "plus", "intl", "international"
    )

    private val localCache = ConcurrentHashMap<String, String>()
    private val catalog = ConcurrentHashMap<String, String>()
    private val localCacheFile = File(System.getProperty("java.io.tmpdir"), "uiptv-logo-cache.json")
    @Volatile
    private var lastCatalogRefreshAt = 0L
    @Volatile
    private var refreshInProgress = false

    init {
        loadLocalCache()
    }

    @JvmStatic
    fun getInstance(): LogoResolverService = this
    fun resolve(channelName: String?, providerLogo: String?): String {
        if (isNotBlank(providerLogo)) return providerLogo.orEmpty()
        if (isBlank(channelName)) return ""
        val key = makeLookupKey(channelName)
        if (isBlank(key)) return ""
        val cached = localCache[key]
        if (isNotBlank(cached)) return cached.orEmpty()
        val resolved = resolveFromCatalog(channelName!!, key)
        return cacheResolvedLogo(key, resolved)
    }

    @Synchronized
    private fun ensureCatalogLoaded() {
        val now = System.currentTimeMillis()
        if (isCatalogFresh(now) || refreshInProgress) return
        refreshInProgress = true
        try {
            val response = fetchChannelsCatalog()
            if (response.statusCode != HttpUtil.STATUS_OK) return
            val logoByChannelId = loadLogosByChannelId()
            val fresh = buildCatalogEntries(JSONArray(response.body), logoByChannelId)
            if (fresh.isNotEmpty()) {
                catalog.clear()
                catalog.putAll(fresh)
                lastCatalogRefreshAt = now
            }
        } catch (_: Exception) {
        } finally {
            refreshInProgress = false
        }
    }

    private fun isCatalogFresh(now: Long): Boolean = catalog.isNotEmpty() && now - lastCatalogRefreshAt < CATALOG_REFRESH_MS

    private fun fetchChannelsCatalog(): HttpUtil.HttpResult = HttpUtil.sendRequest(CHANNELS_CATALOG_URL, null, "GET")

    private fun buildCatalogEntries(channels: JSONArray, logoByChannelId: Map<String, String>): ConcurrentHashMap<String, String> {
        val fresh = ConcurrentHashMap<String, String>()
        for (index in 0 until channels.length()) {
            channels.optJSONObject(index)?.let { addCatalogAliases(fresh, it, logoByChannelId) }
        }
        return fresh
    }

    private fun addCatalogAliases(fresh: ConcurrentHashMap<String, String>, item: JSONObject, logoByChannelId: Map<String, String>) {
        val logo = resolveCatalogLogo(item, logoByChannelId)
        if (isBlank(logo)) return
        addAlias(fresh, item.optString("name", ""), logo)
        val altNames = item.optJSONArray("alt_names") ?: return
        for (index in 0 until altNames.length()) {
            addAlias(fresh, altNames.optString(index, ""), logo)
        }
    }

    private fun resolveCatalogLogo(item: JSONObject, logoByChannelId: Map<String, String>): String {
        val channelId = item.optString("id", "")
        val logo = item.optString("logo", "")
        return if (isBlank(logo) && isNotBlank(channelId)) logoByChannelId[channelId].orEmpty() else logo
    }

    private fun resolveFromCatalog(channelName: String, key: String): String {
        triggerCatalogRefreshAsync()
        var resolved = catalog[key]
        if (isBlank(resolved)) resolved = catalog[makeLookupKey(stripCommonSuffixes(channelName))]
        if (isBlank(resolved)) resolved = resolveByVariants(channelName)
        if (isBlank(resolved)) resolved = resolveByTokenSubset(channelName)
        return resolved.orEmpty()
    }

    private fun cacheResolvedLogo(key: String, resolved: String?): String {
        if (isBlank(resolved)) return ""
        localCache[key] = resolved!!
        persistLocalCache()
        return resolved
    }

    private fun loadLogosByChannelId(): Map<String, String> {
        val logoById = ConcurrentHashMap<String, String>()
        try {
            val response = HttpUtil.sendRequest(LOGOS_CATALOG_URL, null, "GET")
            if (response.statusCode != HttpUtil.STATUS_OK) return logoById
            val lines = response.body.split(Regex("\\r?\\n"))
            for (index in 1 until lines.size) {
                val line = lines[index]
                if (isBlank(line)) continue
                val cells = parseCsvLine(line)
                if (cells.size < 7) continue
                val channel = cells[0]
                val logoUrl = cells[6]
                if (isBlank(channel) || isBlank(logoUrl)) continue
                logoById.putIfAbsent(channel, logoUrl)
            }
        } catch (_: Exception) {
        }
        return logoById
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val c = line[index]
            if (c == '"') {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                cells.add(current.toString())
                current.setLength(0)
            } else {
                current.append(c)
            }
            index++
        }
        cells.add(current.toString())
        return cells
    }

    private fun triggerCatalogRefreshAsync() {
        val now = System.currentTimeMillis()
        if ((catalog.isNotEmpty() && now - lastCatalogRefreshAt < CATALOG_REFRESH_MS) || refreshInProgress) return
        Thread(this::ensureCatalogLoaded, "uiptv-logo-catalog-refresh").apply {
            isDaemon = true
            start()
        }
    }

    private fun addAlias(target: MutableMap<String, String>, alias: String?, logo: String?) {
        val key = makeLookupKey(alias)
        if (isNotBlank(key) && isNotBlank(logo)) {
            target.putIfAbsent(key, logo!!)
        }
    }

    private fun stripCommonSuffixes(value: String?): String {
        if (value == null) return ""
        var cleaned = value.replace(Regex("(?i)\\b(\\+1|\\+2|\\+3|\\+4)\\b"), " ")
        cleaned = cleaned.replace(Regex("(?i)\\b(uhd|fhd|hd|sd|hq|4k|8k|hevc|h264|h265|x264|x265|tv|channel|live|official|intl|international)\\b"), " ")
        return cleaned.trim()
    }

    private fun makeLookupKey(value: String?): String {
        if (isBlank(value)) return ""
        return value!!.lowercase()
            .replace('&', ' ')
            .replace('+', ' ')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun resolveByVariants(originalName: String): String {
        buildNameVariants(originalName).forEach { variant ->
            val hit = catalog[variant]
            if (isNotBlank(hit)) return hit.orEmpty()
        }
        return ""
    }

    private fun buildNameVariants(originalName: String): List<String> {
        val base = makeLookupKey(originalName)
        val stripped = makeLookupKey(stripCommonSuffixes(originalName))
        val variants = LinkedHashSet<String>()
        if (isNotBlank(base)) variants.add(base)
        if (isNotBlank(stripped)) variants.add(stripped)
        val tokenList = ArrayList<String>()
        stripped.split(" ").forEach { token ->
            if (isBlank(token) || NOISE_TOKENS.contains(token)) return@forEach
            tokenList.add(token)
        }
        if (tokenList.isNotEmpty()) {
            variants.add(tokenList.joinToString(" "))
            for (index in tokenList.size - 1 downTo 1) {
                variants.add(tokenList.subList(0, index).joinToString(" "))
            }
        }
        return ArrayList(variants)
    }

    private fun resolveByTokenSubset(originalName: String): String {
        val tokens = LinkedHashSet<String>()
        makeLookupKey(stripCommonSuffixes(originalName)).split(" ").forEach { token ->
            if (isBlank(token) || NOISE_TOKENS.contains(token)) return@forEach
            tokens.add(token)
        }
        if (tokens.isEmpty()) return ""
        for ((catalogKey, value) in catalog.entries) {
            val allMatch = tokens.all { catalogKey.contains(it) }
            if (allMatch && isNotBlank(value)) return value
        }
        return ""
    }

    private fun loadLocalCache() {
        if (!localCacheFile.exists()) return
        try {
            FileInputStream(localCacheFile).use { input ->
                val root = JSONObject(String(input.readAllBytes(), StandardCharsets.UTF_8))
                root.keys().forEach { key ->
                    val value = root.optString(key, "")
                    if (isNotBlank(value)) localCache[key] = value
                }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun persistLocalCache() {
        try {
            FileOutputStream(localCacheFile).use { output ->
                output.write(JSONObject(localCache.toMap()).toString().toByteArray(StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
        }
    }
}
