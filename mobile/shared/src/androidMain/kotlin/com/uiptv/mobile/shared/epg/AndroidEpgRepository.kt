package com.uiptv.mobile.shared.epg

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import com.uiptv.mobile.shared.accounts.*
import com.uiptv.mobile.shared.cache.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
        mutex.withLock { synchronized(guideLoads) { guideLoads.values.forEach { it.cancel() }; guideLoads.clear() }; synchronized(guides) { guides.clear() }; disk.clear(); discovered.clear(); native.clear(); stalker.clearEpgSessions() }
        com.uiptv.mobile.shared.epg.MobileEpgCache.clear()
        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            for (table in com.uiptv.mobile.shared.db.UiptvSyncSchema.desktopTablesPreservedButHiddenInV1) db.delete(table, null, null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun icon(accountId: Long, providerChannelId: String, name: String = ""): String = withContext(Dispatchers.IO) {
        if (!preferences.load().epgEnabled) return@withContext ""
        val cloned = AndroidSyncedEpgCache(databaseHelper.readableDatabase)
        cloned.icon(accountId, providerChannelId).takeIf { it.isNotBlank() }?.let { return@withContext it }
        cloned.matchedIcon(accountId, providerChannelId, name).takeIf { it.isNotBlank() }?.let { return@withContext it }
        val request = mutex.withLock {
            val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
                ?: return@withContext ""
            val override = runCatching { JSONObject(config("epgMappingsJson")).optJSONObject(accountId.toString())?.optString(providerChannelId) }
                .getOrNull().orEmpty().trim()
            val id = MobileXmltvGuide.usableGuideIdentity(override.ifBlank { guideId(account, providerChannelId) })
            val sources = linkedSetOf<String>().apply {
                addAll(discover(account))
                addAll(splitSources(account.epg))
                addAll(splitSources(config("globalEpgSource")))
            }
            Triple(id, if (override.isBlank()) name else "", sources.filter { it.isNotBlank() })
        }
        val host = preferences.load().remoteEndpoint.host
        val nameRules=AndroidEpgNameRules.read(databaseHelper.readableDatabase)
        for (source in request.third) {
            ensureActive()
            val guide = sharedGuide(resolveDesktopSource(source, host), System.currentTimeMillis()).await().guide ?: continue
            guide.withNameRules(nameRules).iconForChannel(request.first, request.second).takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        ""
    }
    override suspend fun globalSource(): String = withContext(Dispatchers.IO) { config("globalEpgSource") }
    suspend fun backgroundCheckIntervalMinutes(): Int = withContext(Dispatchers.IO) {
        config("epgSchedulerIntervalMinutes").toIntOrNull()
            ?.takeIf { it in listOf(1, 2, 3, 5, 10, 15, 30, 60) } ?: 3
    }
    override suspend fun saveGlobalSource(source: String) = withContext(Dispatchers.IO) {
        require(splitSources(source).all { !it.contains("://") || it.startsWith("http://", true) ||
            it.startsWith("https://", true) || it.startsWith("content://", true) || it.startsWith("file://", true) }) {
            "Use an XMLTV HTTP/HTTPS URL or local file."
        }
        saveConfig("globalEpgSource", source.trim())
        MobileEpgCache.clear()
        Unit
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
        MobileEpgCache.peek(accountId, providerChannelId, now)?.let {
            if (it.status == MobileEpgStatus.AVAILABLE || it.status == MobileEpgStatus.STALE) return@withContext applyTvgShift(accountId, providerChannelId, it)
        }
        val cloned = AndroidSyncedEpgCache(databaseHelper.readableDatabase)
        val synced = cloned.read(accountId, providerChannelId, now)
            ?: cloned.matchCloned(accountId, providerChannelId, name, now)
        if (synced != null) {
            MobileEpgCache.put(MobileEpgCache.key(accountId, providerChannelId), synced.result, now,
                MobileEpgCache.ttlFor(synced.result, now))
            return@withContext applyTvgShift(accountId, providerChannelId, synced.result)
        }
        val desktopHost = preferences.load().remoteEndpoint.host
        // Only serve results that carry real programme data. A cached DISABLED/NO_MATCH/UNAVAILABLE
        // verdict would be stale the moment EPG is enabled, a mapping is saved or a source is
        // updated, so those are recomputed every time (cheap: the guide parse and native schedule
        // caches are still hit).
        MobileEpgCache.peek(accountId, providerChannelId, now)?.let {
            if (it.status == MobileEpgStatus.AVAILABLE || it.status == MobileEpgStatus.STALE) return@withContext applyTvgShift(accountId, providerChannelId, it)
        }
        val request = mutex.withLock {
            ensureActive()
            if (!preferences.load().epgEnabled) return@withContext MobileEpgResult()
            val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
                ?: return@withContext MobileEpgResult(MobileEpgStatus.NO_MATCH)
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
            val hasNativeProgramme = schedules.programmes.any { it.stop > now }
            val hasMeaningfulNativeProgramme = schedules.programmes.any {
                it.stop > now && !MobileEpgSchedules.isGenericTitle(it.title)
            }
            if (hasNativeProgramme && hasMeaningfulNativeProgramme) {
                val result = MobileEpgResult(
                    if (schedules.stale) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE, schedules.programmes)
                MobileEpgCache.put(accountId, providerChannelId, result, now)
                return@withContext applyTvgShift(accountId, providerChannelId, result)
            }
            // Native schedules above retain precedence over shared public guides.
            cloned.matchCloned(accountId, providerChannelId, name, now, shared = true)?.let { shared ->
                val result = if (hasNativeProgramme) shared.result.copy(
                    programmes = MobileEpgSchedules.preferSources(listOf(schedules.programmes, shared.result.programmes))
                ) else shared.result
                MobileEpgCache.put(accountId, providerChannelId, result, now)
                return@withContext applyTvgShift(accountId, providerChannelId, result)
            }
            if (hasNativeProgramme) {
                val result = MobileEpgResult(
                    if (schedules.stale) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE, schedules.programmes)
                MobileEpgCache.put(accountId, providerChannelId, result, now)
                return@withContext applyTvgShift(accountId, providerChannelId, result)
            }
            val override = runCatching { JSONObject(config("epgMappingsJson")).optJSONObject(accountId.toString())?.optString(providerChannelId) }
                .getOrNull().orEmpty()
            val id = MobileXmltvGuide.usableGuideIdentity(override.ifBlank { guideId(account, providerChannelId) })
            val sources = linkedSetOf<String>().apply {
                addAll(discover(account))
                addAll(splitSources(account.epg))
                addAll(splitSources(config("globalEpgSource")))
            }
            Triple(id, sources.filter { it.isNotBlank() }.map { resolveDesktopSource(it, desktopHost) }, override)
        }
        val (id, sources, override) = request
            val nameRules=AndroidEpgNameRules.read(databaseHelper.readableDatabase)
            var failed = false
            var message = ""
            var result: MobileEpgResult = MobileEpgResult(MobileEpgStatus.NO_MATCH, message = message)
            for (source in sources) {
                ensureActive()
                val guide = sharedGuide(source, now).await()
                if (guide.guide == null) { failed = true; message = guide.message; continue }
                val programmes = guide.guide.withNameRules(nameRules).forChannel(id, if (override.isBlank()) name else "")
                if (programmes.any { it.stop > now }) {
                    result = MobileEpgResult(if (guide.stale) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE, programmes)
                    break
                }
            }
            if (result.status == MobileEpgStatus.NO_MATCH && failed) result = MobileEpgResult(MobileEpgStatus.UNAVAILABLE, message = message)
            if (result.status == MobileEpgStatus.AVAILABLE || result.status == MobileEpgStatus.STALE) {
                MobileEpgCache.put(accountId, providerChannelId, result, now)
            }
            return@withContext applyTvgShift(accountId, providerChannelId, result)
    }

    private fun applyTvgShift(accountId: Long, channelId: String, result: MobileEpgResult): MobileEpgResult {
        val extraJson = databaseHelper.readableDatabase.rawQuery(
            "SELECT ch.extraJson FROM Channel ch JOIN Category cat ON ch.categoryId=cat.id WHERE cat.accountId=? AND ch.channelId=? LIMIT 1",
            arrayOf(accountId.toString(), channelId)
        ).use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
        val raw = runCatching { JSONObject(extraJson).optJSONObject("m3u")?.optString("tvg-shift").orEmpty().toDouble() }.getOrNull()
            ?: return result
        if (!raw.isFinite() || kotlin.math.abs(raw) > 24.0 || raw == 0.0 || result.programmes.isEmpty()) return result
        val offset = (raw * 3_600_000.0).toLong()
        return result.copy(programmes = result.programmes.map { it.copy(start = it.start + offset, stop = it.stop + offset) })
    }

    private fun sharedGuide(source: String, now: Long): Deferred<GuideCache> = synchronized(guideLoads) {
        guideLoads[source] ?: guideScope.async {
            guidePermits.withPermit { sourceLocks[(source.hashCode() and Int.MAX_VALUE) % sourceLocks.size].withLock {
                loadGuide(source, now)
            } }
        }.also { job ->
            guideLoads[source] = job
            job.invokeOnCompletion { synchronized(guideLoads) { if (guideLoads[source] === job) guideLoads.remove(source) } }
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
        return fields.map { MobileXmltvGuide.usableGuideIdentity(metadata.optString(it)) }
            .firstOrNull { it.isNotBlank() && !(account.type == MobileAccountType.STALKER_PORTAL && it == channelId) }
            ?: if (account.type == MobileAccountType.XTREME_API) "" else channelId
    }
    private fun discover(account: MobileAccount): List<String> {
        if (account.type != MobileAccountType.M3U8_LOCAL && account.type != MobileAccountType.M3U8_URL) return emptyList()
        val playlist = account.m3u8Path.ifBlank { account.url }
        if (playlist.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        discovered[playlist]?.takeIf { now < it.second }?.let { return it.first }
        var found = emptyList<String>()
        try {
            open(playlist).bufferedReader().use { reader ->
                repeat(20) {
                    val line = reader.readLine()?.replace("\uFEFF", "")?.trim() ?: return@repeat
                    if (line.startsWith("#EXTM3U")) {
                        for (attribute in listOf("x-tvg-url", "url-tvg", "tvg-url")) {
                            found = Regex("""(?i)\b$attribute\s*=\s*["']([^"']+)["']""").find(line)?.groupValues?.get(1).orEmpty().let(::splitSources)
                            if (found.isNotEmpty()) break
                        }
                        return@use
                    }
                }
            }
            if (playlist.startsWith("content:")) found = emptyList()
            else {
                val base = if (playlist.contains("://") || playlist.startsWith("file:")) URI(playlist) else File(playlist).toURI()
                found = found.mapNotNull { runCatching { base.resolve(it).toString() }.getOrNull() }.distinct()
            }
        } catch (_: Exception) { found = emptyList() }
        discovered[playlist] = found to now + if (found.isEmpty()) 30 * 60_000L else 900_000
        while (discovered.size > 8) discovered.remove(discovered.keys.first())
        return found
    }

    private fun splitSources(value: String): List<String> = value.split(Regex("(?:\\r?\\n|\\r|[,;|]+|\\t+| {2,})"))
        .map { it.trim() }.filter { it.isNotBlank() }.distinct()
    private fun loadGuide(source: String, now: Long): GuideCache {
        val previous = synchronized(guides) { guides[source] }
        if (previous != null && now < previous.expires) return previous
        val remote = source.startsWith("http://", true) || source.startsWith("https://", true)
        val persisted = if (remote) disk.read(source, now) else null
        if (persisted != null) return GuideCache(persisted, disk.expires(source), false).also {
            synchronized(guides) { guides[source] = it; trimGuides() }
        }
        val cached = try {
            open(source).buffered().use { buffered ->
                buffered.mark(2); val first = buffered.read(); val second = buffered.read(); buffered.reset()
                val xml = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
                val limited = LimitedStream(xml)
                GuideCache(if (remote) disk.parseAndSave(source, limited, now) else AndroidXmltvReader.read(limited, now, source), now + 24 * 3600_000L, false)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val message = when (error) {
                is OutOfMemoryError -> MobileMessages.text("epgMobileMemoryPressure")
                else -> when (error.message) {
                    "Guide exceeds size limit" -> MobileMessages.text("epgSourceTooLarge")
                    "Programme limit exceeded" -> MobileMessages.text("epgSourceProgrammeLimit")
                    else -> MobileMessages.text("epgSourceInvalid")
                }
            }
            val fallback = previous?.guide ?: if (remote) disk.read(source, now, stale = true) else null
            GuideCache(fallback, now + 30 * 60_000L, fallback != null, message)
        }
        synchronized(guides) { guides[source] = cached; trimGuides() }
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
        private val guideScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val guidePermits = kotlinx.coroutines.sync.Semaphore(2)
        private val sourceLocks = Array(32) { Mutex() }
        private val guideLoads = mutableMapOf<String,Deferred<GuideCache>>()
        private val guides = object : LinkedHashMap<String, GuideCache>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, GuideCache>): Boolean = size > 32
        }
        private fun trimGuides() {
            fun bytes(entry: GuideCache): Long = entry.guide?.let { guide ->
                guide.programmes.values.sumOf { list -> list.sumOf { 160L + 2L * (it.title.length + it.description.length) } } +
                    (guide.names.size + guide.qualityNames.size) * 192L +
                    guide.icons.entries.sumOf { 192L + 2L * (it.key.length + it.value.length) }
            } ?: 0L
            var retained = guides.values.sumOf { bytes(it) }
            val iterator = guides.entries.iterator()
            while (retained > 48L * 1024 * 1024 && iterator.hasNext()) {
                val entry = iterator.next(); retained -= bytes(entry.value); iterator.remove()
            }
        }
        private val discovered = object : LinkedHashMap<String, Pair<List<String>, Long>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Pair<List<String>, Long>>): Boolean = size > 16
        }
        private val native = object : LinkedHashMap<Pair<MobileAccount, String>, NativeCache>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Pair<MobileAccount, String>, NativeCache>): Boolean = size > 64
        }
    }

}
