package com.uiptv.mobile.shared.epg

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import com.uiptv.mobile.shared.accounts.*
import com.uiptv.mobile.shared.cache.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.*
import java.net.*
import java.time.*
import java.time.format.DateTimeFormatter
import android.util.Base64
import java.util.zip.GZIPInputStream

/** View coroutines own loads; one mutex bounds concurrency and shares source/native results. */
class AndroidEpgRepository(
    private val context: Context,
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val preferences: AndroidPreferencesRepository
) : MobileEpgRepository {
    private val disk = AndroidEpgDiskCache(context)
    private val stalker = AndroidStalkerCacheReloader(databaseHelper)
    private val xtreme = AndroidXtremeCacheReloader(databaseHelper)
    private data class GuideCache(val guide: MobileXmltvGuide?, val expires: Long, val stale: Boolean, val message: String = "")
    private data class NativeCache(val programmes: List<MobileEpgProgramme>, val expires: Long, val stale: Boolean)

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        mutex.withLock { guides.clear(); disk.clear(); discovered.clear(); native.clear(); stalker.clearEpgSessions() }
        com.uiptv.mobile.shared.epg.MobileEpgCache.clear()
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            for (table in com.uiptv.mobile.shared.db.UiptvSyncSchema.desktopTablesPreservedButHiddenInV1) db.delete(table, null, null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun icon(accountId: Long, providerChannelId: String): String = withContext(Dispatchers.IO) {
        if (!preferences.load().epgEnabled) ""
        else AndroidSyncedEpgCache(databaseHelper.readableDatabase).icon(accountId, providerChannelId)
    }
    override suspend fun globalSource(): String = withContext(Dispatchers.IO) { config("globalEpgSource") }
    override suspend fun saveGlobalSource(source: String) = withContext(Dispatchers.IO) {
        require(source.lineSequence().map { it.trim() }.all { it.isBlank() || !it.contains("://") || it.startsWith("http://", true) ||
            it.startsWith("https://", true) || it.startsWith("content://", true) || it.startsWith("file://", true) }) {
            "Use an XMLTV HTTP/HTTPS URL or local file."
        }
        saveConfig("globalEpgSource", source.trim())
        MobileEpgCache.clear()
    }
    override suspend fun saveMapping(accountId: Long, providerChannelId: String, guideId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val mappings = runCatching { JSONObject(config("epgMappingsJson")) }.getOrDefault(JSONObject())
            val account = mappings.optJSONObject(accountId.toString()) ?: JSONObject()
            if (guideId.isBlank()) account.remove(providerChannelId) else account.put(providerChannelId, guideId.trim())
            mappings.put(accountId.toString(), account)
            saveConfig("epgMappingsJson", mappings.toString())
            MobileEpgCache.clear(accountId, providerChannelId)
        }
    }
    override suspend fun schedule(accountId: Long, providerChannelId: String, name: String): MobileEpgResult = withContext(Dispatchers.IO) {
        if (!preferences.load().epgEnabled) return@withContext MobileEpgResult()
        val now = System.currentTimeMillis()
        val synced = AndroidSyncedEpgCache(databaseHelper.readableDatabase).read(accountId, providerChannelId, now)
        if (synced?.result?.status == MobileEpgStatus.AVAILABLE) {
            MobileEpgCache.put(MobileEpgCache.key(accountId, providerChannelId), synced.result, now,
                minOf(MobileEpgCache.ttlFor(synced.result, now), synced.expiresAt - now))
            return@withContext synced.result
        }
        val desktopHost = preferences.load().remoteEndpoint.host
        // Only serve results that carry real programme data. A cached DISABLED/NO_MATCH/UNAVAILABLE
        // verdict would be stale the moment EPG is enabled, a mapping is saved or a source is
        // updated, so those are recomputed every time (cheap: the guide parse and native schedule
        // caches are still hit).
        MobileEpgCache.peek(accountId, providerChannelId, now)?.let {
            if (it.status == MobileEpgStatus.AVAILABLE || it.status == MobileEpgStatus.STALE) return@withContext it
        }
        mutex.withLock {
            ensureActive()
            if (!preferences.load().epgEnabled) return@withLock MobileEpgResult()
            val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
                ?: return@withLock MobileEpgResult(MobileEpgStatus.NO_MATCH)
            val key = account to providerChannelId
            val cached = native[key]
            val schedules = if (cached != null && now < cached.expires) cached else {
                val programmes = try { nativeSchedule(account, providerChannelId, now) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
                val stale = programmes.isEmpty() && cached != null && cached.programmes.any { it.stop > now }
                NativeCache(if (stale) cached.programmes else programmes, now + 30 * 60_000L, stale)
                    .also { native[key] = it }
            }
            if (schedules.programmes.any { it.stop > now }) {
                val result = MobileEpgResult(
                    if (schedules.stale) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE, schedules.programmes)
                MobileEpgCache.put(accountId, providerChannelId, result, now)
                return@withLock result
            }
            val override = runCatching { JSONObject(config("epgMappingsJson")).optJSONObject(accountId.toString())?.optString(providerChannelId) }
                .getOrNull().orEmpty()
            val id = override.ifBlank { guideId(account, providerChannelId) }
            val sources = linkedSetOf(discover(account), account.epg).apply {
                addAll(config("globalEpgSource").lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList())
            }
            var failed = false
            var message = ""
            var result: MobileEpgResult = MobileEpgResult(MobileEpgStatus.NO_MATCH, message = message)
            for (source in sources.filter { it.isNotBlank() }) {
                ensureActive()
                val guide = loadGuide(resolveDesktopSource(source, desktopHost), now)
                if (guide.guide == null) { failed = true; message = guide.message; continue }
                val programmes = guide.guide.forChannel(id, if (override.isBlank()) name else "")
                if (programmes.any { it.stop > now }) {
                    result = MobileEpgResult(if (guide.stale) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE, programmes)
                    break
                }
            }
            if (result.status == MobileEpgStatus.NO_MATCH && failed) result = MobileEpgResult(MobileEpgStatus.UNAVAILABLE, message = message)
            if (result.status == MobileEpgStatus.AVAILABLE || result.status == MobileEpgStatus.STALE) {
                MobileEpgCache.put(accountId, providerChannelId, result, now)
            }
            if (result.status != MobileEpgStatus.AVAILABLE && synced != null) return@withLock synced.result
            return@withLock result
        }
    }

    private fun config(column: String): String = databaseHelper.readableDatabase.rawQuery("SELECT $column FROM Configuration LIMIT 1", null)
        .use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
    private fun saveConfig(column: String, value: String) {
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply { put(column, value) }
        if (db.update("Configuration", values, null, null) == 0) db.insertOrThrow("Configuration", null, values)
    }
    private fun guideId(account: MobileAccount, channelId: String): String {
        val raw = databaseHelper.readableDatabase.rawQuery(
            "SELECT ch.extraJson FROM Channel ch JOIN Category cat ON ch.categoryId=cat.id WHERE cat.accountId=? AND ch.channelId=? LIMIT 1",
            arrayOf(account.id.toString(), channelId)).use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
        val metadata = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val fields = if (account.type == MobileAccountType.XTREME_API) listOf("epg_channel_id") else listOf("xmltv_id", "epg_id", "tvg-id")
        return fields.map { metadata.optString(it).trim() }.firstOrNull { it.isNotBlank() }
            ?: if (account.type == MobileAccountType.XTREME_API) "" else channelId
    }
    private fun discover(account: MobileAccount): String {
        if (account.type != MobileAccountType.M3U8_LOCAL && account.type != MobileAccountType.M3U8_URL) return ""
        val playlist = account.m3u8Path.ifBlank { account.url }
        if (playlist.isBlank()) return ""
        val now = System.currentTimeMillis()
        discovered[playlist]?.takeIf { now < it.second }?.let { return it.first }
        var found = ""
        try {
            open(playlist).bufferedReader().use { reader ->
                repeat(20) {
                    val line = reader.readLine()?.replace("\uFEFF", "")?.trim() ?: return@repeat
                    if (line.startsWith("#EXTM3U")) {
                        for (attribute in listOf("x-tvg-url", "url-tvg", "tvg-url")) {
                            found = Regex("""(?i)\b$attribute\s*=\s*["']([^"']+)["']""").find(line)?.groupValues?.get(1).orEmpty().split(',')[0].trim()
                            if (found.isNotBlank()) break
                        }
                    }
                }
            }
            if (found.isNotBlank()) found = if (playlist.startsWith("content:")) found else
                (if (playlist.contains("://") || playlist.startsWith("file:")) URI(playlist) else File(playlist).toURI()).resolve(found).toString()
        } catch (_: Exception) { found = "" }
        discovered[playlist] = found to now + if (found.isBlank()) 30 * 60_000L else 900_000
        while (discovered.size > 8) discovered.remove(discovered.keys.first())
        return found
    }
    private fun loadGuide(source: String, now: Long): GuideCache {
        val previous = guides[source]
        if (previous != null && now < previous.expires) return previous
        val remote = source.startsWith("http://", true) || source.startsWith("https://", true)
        val persisted = if (remote) disk.read(source, now) else null
        if (persisted != null) return GuideCache(persisted, disk.expires(source), false).also {
            guides[source] = it
            while (guides.size > 32) guides.remove(guides.keys.first())
        }
        val cached = try {
            open(source).buffered().use { buffered ->
                buffered.mark(2); val first = buffered.read(); val second = buffered.read(); buffered.reset()
                val xml = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
                val limited = LimitedStream(xml)
                GuideCache(if (remote) disk.parseAndSave(source, limited, now) else AndroidXmltvReader.read(limited, now), now + 24 * 3600_000L, false)
            }
        } catch (error: Throwable) {
            val message = when (error) {
                is OutOfMemoryError -> "Out of memory reading EPG guide. Clear the cache and use a smaller regional feed."
                else -> when (error.message) {
                    "Guide exceeds size limit" -> "XMLTV exceeds the 200 MiB decompressed limit. Use a smaller regional feed."
                    "Programme limit exceeded" -> "XMLTV exceeds the programme limit. Use a smaller regional feed."
                    else -> "Guide source unavailable or invalid."
                }
            }
            val fallback = previous?.guide ?: if (remote) disk.read(source, now, stale = true) else null
            GuideCache(fallback, now + 30 * 60_000L, fallback != null, message)
        }
        guides[source] = cached
        return cached
    }
    private fun open(source: String): InputStream {
        if (source.startsWith("content://")) return context.contentResolver.openInputStream(Uri.parse(source)) ?: error("File unavailable")
        if (source.startsWith("file:")) return File(URI(source)).inputStream()
        if (!source.contains("://")) return File(source).inputStream()
        require(source.startsWith("http://", true) || source.startsWith("https://", true))
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
            setRequestProperty("Accept-Encoding", "gzip")
        }
        if (connection.responseCode !in 200..299) { connection.disconnect(); error("Source unavailable") }
        val input = connection.inputStream
        return object : FilterInputStream(input) { override fun close() { try { super.close() } finally { connection.disconnect() } } }
    }
    internal class LimitedStream(input: InputStream) : FilterInputStream(input) {
        private var remaining = 200L * 1024 * 1024
        override fun read(): Int = `in`.read().also { if (it >= 0) if (--remaining < 0) throw IOException("Guide exceeds size limit") }
        override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also { if (it > 0) { remaining -= it; if (remaining < 0) throw IOException("Guide exceeds size limit") } }
    }
    private fun nativeSchedule(account: MobileAccount, id: String, now: Long): List<MobileEpgProgramme> {
        val actions = when (account.type) {
            MobileAccountType.XTREME_API -> listOf("get_simple_data_table", "get_short_epg")
            MobileAccountType.STALKER_PORTAL -> listOf("get_short_epg", "get_epg_info")
            else -> return emptyList()
        }
        for (action in actions) {
            val result = runCatching {
                val raw = if (account.type == MobileAccountType.XTREME_API) xtreme.readApi(account, action, mapOf("stream_id" to id, "limit" to "100"))
                    else stalker.readEpg(account, id, action)
                parseNative(raw, account, id, now)
            }.getOrDefault(emptyList())
            if (result.any { it.stop > now }) return result
        }
        return emptyList()
    }
    internal fun parseNative(raw: String, account: MobileAccount, id: String, now: Long): List<MobileEpgProgramme> {
        var data: Any? = JSONTokener(raw).nextValue()
        repeat(5) {
            val obj = data as? JSONObject ?: return@repeat
            data = obj.opt("js") ?: obj.opt("epg_listings") ?: obj.opt("data") ?: obj.opt(id)
        }
        val list = data as? JSONArray ?: return emptyList()
        val zone = runCatching { ZoneId.of(account.timezone) }.getOrDefault(ZoneOffset.UTC)
        fun time(value: String): Long {
            value.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
            runCatching { OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
            return runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")).atZone(zone).toInstant().toEpochMilli() }.getOrDefault(0)
        }
        fun text(value: String) = if (account.type == MobileAccountType.XTREME_API)
            runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.decode(value, Base64.DEFAULT))).toString() }.getOrDefault(value) else value
        return MobileEpgSchedules.normalize((0 until minOf(list.length(), MAX_NATIVE_PROGRAMMES)).mapNotNull { index ->
            val p = list.optJSONObject(index) ?: return@mapNotNull null
            fun field(vararg keys: String) = keys.map { p.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
            val associated = field("ch_id", "channel_id", "stream_id")
            if (associated.isNotBlank() && associated != id) return@mapNotNull null
            val start = time(field("start_timestamp", "start", "time", "start_time"))
            var stop = time(field("stop_timestamp", "end_timestamp", "end", "stop", "end_time"))
            if (stop == 0L && p.optLong("duration") in 1..86399) stop = start + p.optLong("duration") * 1000
            val title = text(field("title", "name"))
            if (start > now - 86400_000 && start < now + 7 * 86400_000L && stop > start && title.isNotBlank())
                MobileEpgProgramme(title, text(field("description", "descr", "desc")), start, stop) else null
        }.sortedBy { it.start })
    }
    companion object {
        fun resolveDesktopSource(source: String, desktopHost: String): String {
            if (desktopHost.isBlank()) return source
            return runCatching {
                val uri = URI(source)
                if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host?.lowercase() !in setOf("localhost", "127.0.0.1", "::1", "[::1]")) source
                else {
                    val authority = uri.rawAuthority
                    val offset = authority.lastIndexOf('@') + 1
                    val hostPart = authority.substring(offset)
                    val length = if (hostPart.startsWith("[")) hostPart.indexOf(']') + 1 else hostPart.substringBefore(':').length
                    val start = source.indexOf("://") + 3 + offset
                    val replacement = desktopHost.trim().let { if (it.contains(':') && !it.startsWith("[")) "[$it]" else it }
                    source.replaceRange(start, start + length, replacement)
                }
            }.getOrDefault(source)
        }

        const val MAX_NATIVE_PROGRAMMES = 2000
        private val mutex = Mutex()
        private val guides = object : LinkedHashMap<String, GuideCache>(6, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, GuideCache>): Boolean = size > 6
        }
        private val discovered = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Pair<String, Long>>): Boolean = size > 16
        }
        private val native = object : LinkedHashMap<Pair<MobileAccount, String>, NativeCache>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Pair<MobileAccount, String>, NativeCache>): Boolean = size > 64
        }
    }

}
