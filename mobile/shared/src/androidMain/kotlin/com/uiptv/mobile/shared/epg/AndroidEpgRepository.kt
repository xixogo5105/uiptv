package com.uiptv.mobile.shared.epg

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.uiptv.mobile.shared.accounts.*
import com.uiptv.mobile.shared.cache.*
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.logging.AndroidAppLog
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.*
import java.io.*
import java.net.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

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

    override suspend fun schedule(accountId: Long, providerChannelId: String, name: String): MobileEpgResult =
        schedule(accountId, providerChannelId, name, "")

    suspend fun schedule(accountId: Long, providerChannelId: String, name: String, epgIdentity: String,
                         channelRowId: Long = 0, categoryRowId: Long = 0,
                         bookmarkId: String = ""): MobileEpgResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val settings = preferences.load()
        if (!settings.epgEnabled) return@withContext MobileEpgResult()
        val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext MobileEpgResult(MobileEpgStatus.NO_MATCH)
        val requestContext = currentCoroutineContext()
        val now = System.currentTimeMillis()
        ensureActive()
        val resolvedGuideId = epgIdentity.takeIf { it.isNotBlank() }?.let { MobileXmltvGuide.usableGuideIdentity(it) }
            ?.takeIf { it.isNotBlank() } ?: guideId(account, providerChannelId)
        AndroidAppLog.i(TAG, "EPG start mode=single accountType=${account.type} accountId=$accountId channelId=$providerChannelId guideId=$resolvedGuideId")
        val native = nativeSchedule(account, providerChannelId, now)
        ensureActive()
        if (native.any { it.stop > now && !MobileEpgSchedules.isGenericTitle(it.title) }) {
            AndroidAppLog.i(TAG, "EPG complete source=native programmes=${native.size} elapsedMs=${elapsedMs(startedAt)}")
            return@withContext applyTvgShift(accountId, providerChannelId, MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
        }
        val id = resolvedGuideId
        val sources = splitSources(account.epg).ifEmpty { discover(account) }
        var failed = false
        for (configured in sources) {
            ensureActive()
            val source = SamsungTvPlusEpgSource.resolve(account, configured) ?: configured
            if (source != configured) AndroidAppLog.i(TAG, "EPG XMLTV using direct Samsung TV Plus source host=${sourceHost(source)}")
            try {
                val programmes = openXml(open(source)).use { xml ->
                    AndroidXmltvReader.read(EpgInputStream(xml, { requestContext.ensureActive() }), now, source, id, name, checkCancelled = { requestContext.ensureActive() }).forChannel(id, name)
                }
                if (programmes.isNotEmpty()) {
                    AndroidAppLog.i(TAG, "EPG complete source=xmltv host=${sourceHost(source)} programmes=${programmes.size} elapsedMs=${elapsedMs(startedAt)}")
                    return@withContext applyTvgShift(accountId, providerChannelId,
                        MobileEpgResult(MobileEpgStatus.AVAILABLE, programmes))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                failed = true
                AndroidAppLog.w(TAG, "EPG XMLTV failed host=${sourceHost(source)} error=${failure.javaClass.simpleName}")
            }
        }
        if (native.isNotEmpty()) return@withContext applyTvgShift(accountId, providerChannelId,
            MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
        val result = MobileEpgResult(if (failed) MobileEpgStatus.UNAVAILABLE else MobileEpgStatus.NO_MATCH)
        AndroidAppLog.i(TAG, "EPG complete source=none status=${result.status} elapsedMs=${elapsedMs(startedAt)}")
        result
    }

    /** Publishes current/next details as soon as either live source can provide them. */
    suspend fun scheduleProgressively(
        accountId: Long,
        providerChannelId: String,
        name: String,
        onUpdate: suspend (MobileEpgResult) -> Unit,
        epgIdentity: String = "",
        channelRowId: Long = 0,
        categoryRowId: Long = 0,
        bookmarkId: String = ""
    ): MobileEpgResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val settings = preferences.load()
        if (!settings.epgEnabled) return@withContext MobileEpgResult()
        val account = AndroidSQLiteAccountRepository(databaseHelper).listAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext MobileEpgResult(MobileEpgStatus.NO_MATCH)
        val resolvedGuideId = epgIdentity.takeIf { it.isNotBlank() }?.let { MobileXmltvGuide.usableGuideIdentity(it) }
            ?.takeIf { it.isNotBlank() } ?: guideId(account, providerChannelId)
        AndroidAppLog.i(TAG, "EPG start mode=progressive accountType=${account.type} accountId=$accountId channelId=$providerChannelId guideId=$resolvedGuideId")
        val requestContext = currentCoroutineContext()
        val now = System.currentTimeMillis()
        coroutineScope {
            val events = Channel<EpgEvent>(Channel.UNLIMITED)
            val nativeJob = launch(Dispatchers.IO) {
                val nativeStartedAt = System.nanoTime()
                val native = try { nativeSchedule(account, providerChannelId, now) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                AndroidAppLog.i(TAG, "EPG native complete programmes=${native.size} elapsedMs=${elapsedMs(nativeStartedAt)}")
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
                    val id = resolvedGuideId
                    val sources = splitSources(account.epg).ifEmpty { discover(account) }
                    AndroidAppLog.i(TAG, "EPG XMLTV sources=${sources.size} guideId=$id")
                    for (configured in sources.takeIf { result == null }.orEmpty()) {
                        ensureActive()
                        val source = SamsungTvPlusEpgSource.resolve(account, configured) ?: configured
                        if (source != configured) AndroidAppLog.i(TAG, "EPG XMLTV using direct Samsung TV Plus source host=${sourceHost(source)}")
                        val sourceStartedAt = System.nanoTime()
                        AndroidAppLog.i(TAG, "EPG XMLTV start host=${sourceHost(source)}")
                        try {
                            val opened = open(source)
                            AndroidAppLog.i(TAG, "EPG XMLTV opened host=${sourceHost(source)} elapsedMs=${elapsedMs(sourceStartedAt)}")
                            val guide = openXml(opened).use { xml ->
                                val epgInput = EpgInputStream(xml, { requestContext.ensureActive() })
                                AndroidXmltvReader.read(epgInput, now, source, id, name, checkCancelled = { requestContext.ensureActive() }, onProgress = { partial ->
                                    val shifted = applyTvgShift(accountId, providerChannelId,
                                        MobileEpgResult(MobileEpgStatus.AVAILABLE, partial))
                                    result = shifted
                                    AndroidAppLog.i(TAG, "EPG XMLTV partial programmes=${partial.size} bytes=${epgInput.bytesRead} elapsedMs=${elapsedMs(sourceStartedAt)}")
                                    events.trySend(EpgEvent.Progress(shifted))
                                }, stopAfterUpcomingProgrammeCount = SWIPE_GUIDE_PROGRAMME_COUNT)
                            }
                            val programmes = guide.forChannel(id, name)
                            if (programmes.isNotEmpty()) {
                                AndroidAppLog.i(TAG, "EPG XMLTV matched host=${sourceHost(source)} programmes=${programmes.size} elapsedMs=${elapsedMs(sourceStartedAt)}")
                                val matched = applyTvgShift(accountId, providerChannelId,
                                    MobileEpgResult(MobileEpgStatus.AVAILABLE, programmes))
                                result = matched
                                events.send(EpgEvent.Progress(matched))
                                break
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) {
                            failed = true
                            AndroidAppLog.w(TAG, "EPG XMLTV failed host=${sourceHost(source)} error=${failure.javaClass.simpleName}")
                        }
                    }
                    if (result == null) AndroidAppLog.i(TAG, "EPG XMLTV no match elapsedMs=${elapsedMs(startedAt)}")
                    else if (failed) AndroidAppLog.i(TAG, "EPG XMLTV retaining partial result after source failure")
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
                        // Publish the current programme immediately (for the one-item player bar),
                        // then keep reading until the swipe guide has a short schedule or both
                        // sources finish. A slower source cannot replace an already-complete guide.
                        if (AndroidXmltvReader.hasEnoughUpcomingProgrammes(
                                event.result.programmes, System.currentTimeMillis(), SWIPE_GUIDE_PROGRAMME_COUNT
                            )) {
                            return@coroutineScope event.result
                        }
                    }
                    is EpgEvent.NativeFinished -> { native = event.programmes; completed++ }
                    is EpgEvent.XmltvFinished -> { xmltv = event.result; failed = event.failed; completed++ }
                }
            } finally {
                nativeJob.cancel(); xmltvJob.cancel(); events.close()
            }
            val finalResult = xmltv ?: if (native.isNotEmpty()) applyTvgShift(accountId, providerChannelId,
                MobileEpgResult(MobileEpgStatus.AVAILABLE, native))
            else MobileEpgResult(if (failed) MobileEpgStatus.UNAVAILABLE else MobileEpgStatus.NO_MATCH)
            AndroidAppLog.i(TAG, "EPG complete source=${if (xmltv != null) "xmltv" else if (native.isNotEmpty()) "native-fallback" else "none"} status=${finalResult.status} programmes=${finalResult.programmes.size} elapsedMs=${elapsedMs(startedAt)}")
            finalResult
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
        val m3u = metadata.optJSONObject("m3u")
        return (fields.map { MobileXmltvGuide.usableGuideIdentity(metadata.optString(it)) } +
            fields.map { MobileXmltvGuide.usableGuideIdentity(m3u?.optString(it)) })
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
            setRequestProperty("Accept-Encoding", "gzip, x-gzip, deflate")
        }
        try {
            val status = connection.responseCode
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            val contentEncoding = connection.getHeaderField("Content-Encoding").orEmpty().ifBlank { "identity" }
            val acceptsRanges = connection.getHeaderField("Accept-Ranges").orEmpty().ifBlank { "unknown" }
            val transferEncoding = connection.getHeaderField("Transfer-Encoding").orEmpty().ifBlank { "none" }
            AndroidAppLog.i(TAG, "XMLTV HTTP endpoint=${sourceEndpoint(source)} status=$status contentLength=$contentLength encoding=$contentEncoding transfer=$transferEncoding ranges=$acceptsRanges")
            if (status !in 200..299) error("Source unavailable")
            val input = connection.inputStream
            val openedAt = System.nanoTime()
            val meteredInput = object : FilterInputStream(input) {
                private var wireBytes = 0L
                override fun read(): Int = super.read().also { if (it >= 0) wireBytes++ }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) wireBytes += it }
                override fun close() {
                    try { super.close() } finally {
                        AndroidAppLog.i(TAG, "XMLTV HTTP closed endpoint=${sourceEndpoint(source)} wireBytes=$wireBytes elapsedMs=${elapsedMs(openedAt)}")
                        connection.disconnect()
                    }
                }
            }
            return contentEncoding.split(',').asReversed().fold(meteredInput as InputStream) { stream, rawEncoding ->
                when (rawEncoding.trim().lowercase()) {
                    "", "identity" -> stream
                    "gzip", "x-gzip" -> GZIPInputStream(BufferedInputStream(stream, XMLTV_BUFFER_SIZE), XMLTV_BUFFER_SIZE)
                    "deflate" -> InflaterInputStream(BufferedInputStream(stream, XMLTV_BUFFER_SIZE))
                    else -> error("Unsupported XMLTV content encoding")
                }
            }
        } catch (failure: Exception) {
            connection.disconnect()
            throw failure
        }
    }

    /** Use large buffers for network XMLTV feeds, especially when decompression is required. */
    private fun openXml(input: InputStream): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, XMLTV_BUFFER_SIZE)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered, XMLTV_BUFFER_SIZE) else buffered
    }
    private suspend fun nativeSchedule(account: MobileAccount, id: String, now: Long): List<MobileEpgProgramme> {
        val actions = when (account.type) {
            MobileAccountType.XTREME_API -> listOf("get_simple_data_table", "get_short_epg")
            MobileAccountType.STALKER_PORTAL -> listOf("get_short_epg", "get_epg_info")
            else -> return emptyList()
        }
        for (action in actions) {
            currentCoroutineContext().ensureActive()
            val startedAt = System.nanoTime()
            val result = try {
                val raw = runInterruptible {
                    if (account.type == MobileAccountType.XTREME_API) xtreme.readApi(account, action, mapOf("stream_id" to id, "limit" to "100"))
                    else stalker.readEpg(account, id, action)
                }
                currentCoroutineContext().ensureActive()
                parseNative(raw, account, id, now)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
            AndroidAppLog.i(TAG, "EPG native action=$action programmes=${result.size} elapsedMs=${elapsedMs(startedAt)}")
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
        private const val TAG = "UIPTV-EPG"
        private const val XMLTV_BUFFER_SIZE = 64 * 1024
        private const val SWIPE_GUIDE_PROGRAMME_COUNT = 8
        private fun elapsedMs(startedAt: Long) = (System.nanoTime() - startedAt) / 1_000_000
        private fun sourceHost(source: String): String = runCatching {
            URI(source).let { it.host ?: it.scheme ?: "local" }
        }.getOrDefault("local")
        private fun sourceEndpoint(source: String): String = runCatching {
            URI(source).let { uri ->
                val host = uri.host ?: uri.scheme ?: "local"
                if (uri.port < 0) host else "$host:${uri.port}"
            }
        }.getOrDefault("local")

        const val MAX_NATIVE_PROGRAMMES = 2000
    }
}
