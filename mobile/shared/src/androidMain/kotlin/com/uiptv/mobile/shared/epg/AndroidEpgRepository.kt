package com.uiptv.mobile.shared.epg

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.uiptv.mobile.shared.accounts.*
import com.uiptv.mobile.shared.cache.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.*
import java.io.*
import java.net.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream

/** Live account-level requests; no guide data survives the requesting screen/player. */
class AndroidEpgRepository(
    private val context: Context,
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val preferences: AndroidPreferencesRepository
) : MobileEpgRepository {
    private val stalker = AndroidStalkerCacheReloader(databaseHelper)
    private val xtreme = AndroidXtremeCacheReloader(databaseHelper)

    private sealed interface EpgEvent {
        data class Progress(val result: MobileEpgResult) : EpgEvent
        data class NativeFinished(val programmes: List<MobileEpgProgramme>) : EpgEvent
        data class XmltvFinished(val result: MobileEpgResult?, val failed: Boolean) : EpgEvent
    }

    override suspend fun schedule(accountId: Long, providerChannelId: String, name: String): MobileEpgResult = withContext(Dispatchers.IO) {
        val settings = preferences.load()
        if (!settings.epgEnabled) return@withContext MobileEpgResult()
        val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext MobileEpgResult(MobileEpgStatus.NO_MATCH)
        val requestContext = currentCoroutineContext()
        val now = System.currentTimeMillis()
        ensureActive()
        val native = nativeSchedule(account, providerChannelId, now)
        ensureActive()
        if (native.any { it.stop > now && !MobileEpgSchedules.isGenericTitle(it.title) }) {
            return@withContext applyTvgShift(accountId, providerChannelId, MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
        }
        val id = guideId(account, providerChannelId)
        val sources = splitSources(account.epg).ifEmpty { discover(account) }
        var failed = false
        for (configured in sources) {
            ensureActive()
            val source = resolveDesktopSource(configured, settings.remoteEndpoint.host)
            try {
                val programmes = open(source).buffered().use { buffered ->
                    buffered.mark(2); val first = buffered.read(); val second = buffered.read(); buffered.reset()
                    val xml = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
                    AndroidXmltvReader.read(EpgInputStream(xml, { requestContext.ensureActive() }), now, source, id, name, checkCancelled = { requestContext.ensureActive() }).forChannel(id, name)
                }
                if (programmes.isNotEmpty()) return@withContext applyTvgShift(accountId, providerChannelId,
                    MobileEpgResult(MobileEpgStatus.AVAILABLE, programmes))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
        if (native.isNotEmpty()) return@withContext applyTvgShift(accountId, providerChannelId,
            MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
        MobileEpgResult(if (failed) MobileEpgStatus.UNAVAILABLE else MobileEpgStatus.NO_MATCH)
    }

    /** Publishes current/next details as soon as either live source can provide them. */
    suspend fun scheduleProgressively(
        accountId: Long,
        providerChannelId: String,
        name: String,
        onUpdate: suspend (MobileEpgResult) -> Unit
    ): MobileEpgResult = withContext(Dispatchers.IO) {
        val settings = preferences.load()
        if (!settings.epgEnabled) return@withContext MobileEpgResult()
        val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext MobileEpgResult(MobileEpgStatus.NO_MATCH)
        val requestContext = currentCoroutineContext()
        val now = System.currentTimeMillis()
        coroutineScope {
            val events = Channel<EpgEvent>(Channel.UNLIMITED)
            val nativeJob = launch(Dispatchers.IO) {
                val native = try { nativeSchedule(account, providerChannelId, now) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                if (native.any { it.stop > now && !MobileEpgSchedules.isGenericTitle(it.title) }) {
                    events.send(EpgEvent.Progress(applyTvgShift(accountId, providerChannelId,
                        MobileEpgResult(MobileEpgStatus.AVAILABLE, native))))
                }
                events.send(EpgEvent.NativeFinished(native))
            }
            val xmltvJob = launch(Dispatchers.IO) {
                var result: MobileEpgResult? = null
                var failed = false
                try {
                    val id = guideId(account, providerChannelId)
                    val sources = splitSources(account.epg).ifEmpty { discover(account) }
                    for (configured in sources) {
                        ensureActive()
                        val source = resolveDesktopSource(configured, settings.remoteEndpoint.host)
                        try {
                            val guide = open(source).buffered().use { buffered ->
                                buffered.mark(2); val first = buffered.read(); val second = buffered.read(); buffered.reset()
                                val xml = if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
                                AndroidXmltvReader.read(EpgInputStream(xml, { requestContext.ensureActive() }), now, source, id, name, checkCancelled = { requestContext.ensureActive() }, onProgress = { partial ->
                                    val shifted = applyTvgShift(accountId, providerChannelId,
                                        MobileEpgResult(MobileEpgStatus.AVAILABLE, partial))
                                    events.trySend(EpgEvent.Progress(shifted))
                                })
                            }
                            val programmes = guide.forChannel(id, name)
                            if (programmes.isNotEmpty()) {
                                result = applyTvgShift(accountId, providerChannelId,
                                    MobileEpgResult(MobileEpgStatus.AVAILABLE, programmes))
                                events.send(EpgEvent.Progress(result!!))
                                break
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                events.send(EpgEvent.XmltvFinished(result, failed))
            }

            var native: List<MobileEpgProgramme> = emptyList()
            var xmltv: MobileEpgResult? = null
            var failed = false
            var completed = 0
            var lastUpdate: MobileEpgResult? = null
            try {
                while (completed < 2) when (val event = events.receive()) {
                    is EpgEvent.Progress -> if (event.result != lastUpdate) {
                        lastUpdate = event.result
                        onUpdate(event.result)
                    }
                    is EpgEvent.NativeFinished -> { native = event.programmes; completed++ }
                    is EpgEvent.XmltvFinished -> { xmltv = event.result; failed = event.failed; completed++ }
                }
            } finally {
                nativeJob.cancel(); xmltvJob.cancel(); events.close()
            }
            xmltv ?: if (native.isNotEmpty()) applyTvgShift(accountId, providerChannelId,
                MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
            else MobileEpgResult(if (failed) MobileEpgStatus.UNAVAILABLE else MobileEpgStatus.NO_MATCH)
        }
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
    private suspend fun discover(account: MobileAccount): List<String> {
        val requestContext = currentCoroutineContext()
        if (account.type != MobileAccountType.M3U8_LOCAL && account.type != MobileAccountType.M3U8_URL) return emptyList()
        val playlist = account.m3u8Path.ifBlank { account.url }
        if (playlist.isBlank()) return emptyList()
        var found = emptyList<String>()
        try {
            open(playlist).bufferedReader().use { reader ->
                repeat(20) {
                    requestContext.ensureActive()
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
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { found = emptyList() }
        return found
    }

    private fun splitSources(value: String): List<String> = value.split(Regex("(?:\\r?\\n|\\r|[,;|]+|\\t+| {2,})"))
        .map { it.trim() }.filter { it.isNotBlank() }.distinct()
    private fun open(source: String): InputStream {
        if (source.startsWith("content://")) return context.contentResolver.openInputStream(Uri.parse(source)) ?: error("File unavailable")
        if (source.startsWith("file:")) return File(URI(source)).inputStream()
        if (!source.contains("://")) return File(source).inputStream()
        require(source.startsWith("http://", true) || source.startsWith("https://", true))
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
            setRequestProperty("Accept-Encoding", "gzip")
        }
        try {
            if (connection.responseCode !in 200..299) error("Source unavailable")
            val input = connection.inputStream
            return object : FilterInputStream(input) { override fun close() { try { super.close() } finally { connection.disconnect() } } }
        } catch (failure: Exception) {
            connection.disconnect()
            throw failure
        }
    }
    private suspend fun nativeSchedule(account: MobileAccount, id: String, now: Long): List<MobileEpgProgramme> {
        val actions = when (account.type) {
            MobileAccountType.XTREME_API -> listOf("get_simple_data_table", "get_short_epg")
            MobileAccountType.STALKER_PORTAL -> listOf("get_short_epg", "get_epg_info")
            else -> return emptyList()
        }
        for (action in actions) {
            currentCoroutineContext().ensureActive()
            val result = try {
                val raw = runInterruptible {
                    if (account.type == MobileAccountType.XTREME_API) xtreme.readApi(account, action, mapOf("stream_id" to id, "limit" to "100"))
                    else stalker.readEpg(account, id, action)
                }
                currentCoroutineContext().ensureActive()
                parseNative(raw, account, id, now)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
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
    }
}
