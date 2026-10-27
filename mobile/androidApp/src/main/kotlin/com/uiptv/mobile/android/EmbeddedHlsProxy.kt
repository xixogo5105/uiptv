package com.uiptv.mobile.android

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** MPV keeps decoding; OkHttp uses Android TLS to download every HLS resource. */
internal class EmbeddedHlsProxy : Closeable {
    private data class Resource(val url: String, val playlist: Boolean)
    private val closed = AtomicBoolean()
    private val token = UUID.randomUUID().toString()
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val resources = LinkedHashMap<String, Resource>(128, 0.75f, true)
    private val resourceIds = HashMap<Resource, String>()
    private var nextId = 0L
    private val client = OkHttpClient.Builder()
        .cache(null) // Stream playlists and media segments are never stored on disk.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(16), {
        Thread(it, "uiptv-hls-download").apply { isDaemon = true }
    })

    init {
        Thread({
            while (!closed.get()) {
                val socket = try { server.accept() } catch (_: IOException) { break }
                sockets.add(socket)
                try {
                    workers.execute { serve(socket) }
                } catch (_: RejectedExecutionException) {
                    sockets.remove(socket)
                    socket.close()
                }
            }
        }, "uiptv-hls-listener").apply { isDaemon = true }.start()
    }

    fun playbackUrl(url: String): String = register(URL(url), true)

    private fun register(url: URL, playlist: Boolean): String {
        require(url.protocol in setOf("http", "https")) { "Unsupported HLS resource protocol" }
        val resource = Resource(url.toExternalForm(), playlist)
        val id = synchronized(resources) {
            check(!closed.get()) { "HLS proxy is closed" }
            resourceIds[resource]?.also { resources[it] } ?: (++nextId).toString().also {
                resources[it] = resource
                resourceIds[resource] = it
                if (resources.size > 8192) {
                    val oldest = resources.entries.iterator()
                    val entry = oldest.next()
                    resourceIds.remove(entry.value)
                    oldest.remove()
                }
            }
        }
        val extension = url.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "ts"
        return "http://127.0.0.1:${server.localPort}/$token/$id/${if (playlist) "index.m3u8" else "data.$extension"}"
    }

    private fun serve(socket: Socket) {
        var started = false
        try {
            socket.soTimeout = 10_000
            val input = socket.getInputStream().buffered()
            val request = readLine(input, 4096).split(' ')
            if (request.size != 3 || request[0] !in setOf("GET", "HEAD")) {
                writeHeaders(socket.getOutputStream(), 405, emptyMap())
                return
            }
            val headers = HashMap<String, String>()
            var headerBytes = 0
            while (true) {
                val line = readLine(input, 8192)
                if (line.isEmpty()) break
                headerBytes += line.length
                if (headerBytes > 16_384) throw IOException("Request headers too large")
                if (':' in line) headers[line.substringBefore(':').lowercase(Locale.ROOT)] = line.substringAfter(':').trim()
            }
            val path = request[1].substringBefore('?').split('/')
            val resource = if (path.size == 4 && path[1] == token) {
                synchronized(resources) { resources[path[2]] }
            } else null
            if (resource == null) {
                writeHeaders(socket.getOutputStream(), 404, emptyMap())
                return
            }
            val upstream = Request.Builder().url(resource.url)
                .header("User-Agent", StreamProbeUserAgent)
                .header("Accept-Encoding", "identity")
            if (!resource.playlist) {
                for (name in listOf("range", "if-range")) headers[name]?.let { upstream.header(name, it) }
            }
            if (closed.get()) throw IOException("HLS proxy closed")
            client.newCall(upstream.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    writeHeaders(socket.getOutputStream(), response.code, emptyMap())
                    return
                }
                val type = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
                val playlist = resource.playlist || type in HlsContentTypes
                val output = socket.getOutputStream().buffered()
                if (playlist) {
                    val text = response.body.byteStream().use { readManifest(it) }
                    val rewritten = rewriteManifest(text, response.request.url.toUrl()).toByteArray(Charsets.UTF_8)
                    writeHeaders(output, 200, mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Content-Length" to rewritten.size.toString()))
                    started = true
                    if (request[0] != "HEAD") output.write(rewritten)
                } else {
                    val responseHeaders = linkedMapOf("Content-Type" to (response.header("Content-Type") ?: "application/octet-stream"))
                    for (name in listOf("Content-Length", "Content-Range", "Accept-Ranges")) {
                        response.header(name)?.let { responseHeaders[name] = it }
                    }
                    writeHeaders(output, response.code, responseHeaders)
                    started = true
                    if (request[0] != "HEAD") response.body.byteStream().use { it.copyTo(output, 64 * 1024) }
                }
                output.flush()
            }
        } catch (_: IOException) {
            if (!started && !closed.get()) runCatching { writeHeaders(socket.getOutputStream(), 502, emptyMap()) }
        } catch (_: IllegalArgumentException) {
            if (!started && !closed.get()) runCatching { writeHeaders(socket.getOutputStream(), 502, emptyMap()) }
        } catch (_: IllegalStateException) {
            if (!started && !closed.get()) runCatching { writeHeaders(socket.getOutputStream(), 502, emptyMap()) }
        } finally {
            sockets.remove(socket)
            runCatching { socket.close() }
        }
    }

    private fun rewriteManifest(text: String, base: URL): String {
        var nextIsPlaylist = false
        return text.removePrefix("\uFEFF").lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                !trimmed.startsWith('#') -> {
                    val url = URL(base, trimmed)
                    register(url, nextIsPlaylist || url.path.endsWith(".m3u8", true)).also { nextIsPlaylist = false }
                }
                else -> {
                    if (trimmed.startsWith("#EXT-X-STREAM-INF:")) nextIsPlaylist = true
                    val playlistUri = listOf("#EXT-X-MEDIA:", "#EXT-X-I-FRAME-STREAM-INF:", "#EXT-X-RENDITION-REPORT:").any(trimmed::startsWith)
                    HlsUriAttribute.replace(line) { match ->
                        val uri = match.groupValues[1]
                        if (uri.startsWith("data:", true)) match.value else "URI=\"${register(URL(base, uri), playlistUri)}\""
                    }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        client.dispatcher.cancelAll()
        workers.shutdownNow()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        synchronized(resources) { resources.clear(); resourceIds.clear() }
    }

    private fun readManifest(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > 2 * 1024 * 1024) throw IOException("HLS playlist too large")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun readLine(input: InputStream, limit: Int): String {
        val output = ByteArrayOutputStream()
        while (output.size() < limit) {
            val next = input.read()
            if (next < 0) throw IOException("Incomplete HTTP request")
            if (next == 10) return output.toString(Charsets.US_ASCII.name()).trimEnd('\r')
            output.write(next)
        }
        throw IOException("HTTP line too long")
    }

    private fun writeHeaders(output: OutputStream, status: Int, headers: Map<String, String>) {
        val text = buildString {
            append("HTTP/1.1 $status Response\r\nConnection: close\r\n")
            if (headers.isEmpty()) append("Content-Length: 0\r\n")
            headers.forEach { (key, value) -> append("$key: ${value.replace("\r", "").replace("\n", "")}\r\n") }
            append("\r\n")
        }
        output.write(text.toByteArray(Charsets.US_ASCII))
        output.flush()
    }
}

private val HlsUriAttribute = Regex("URI=\"([^\"]*)\"")
private val HlsContentTypes = setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")
