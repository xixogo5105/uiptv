package com.uiptv.mobile.android

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class EmbeddedHlsProxyTest {
    private lateinit var server: HttpServer
    private lateinit var proxy: EmbeddedHlsProxy
    private val requests = AtomicInteger()
    private val base: String get() = "http://127.0.0.1:${server.address.port}"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        proxy = EmbeddedHlsProxy()
    }

    @After fun stop() {
        proxy.close()
        server.stop(0)
    }

    @Test fun rewritesRedirectedMasterAndNestedPlaylistsKeysAndSegments() {
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "/cdn/master")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        text("/cdn/master", "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"/audio\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvideo/media?token=keep\n")
        text("/cdn/video/media", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key?token=key\"\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:10,\n/segments/chunk.ts?token=segment\n")
        binary("/cdn/video/key", byteArrayOf(1, 2, 3))
        binary("/cdn/video/init.mp4", byteArrayOf(4, 5, 6))
        binary("/segments/chunk.ts", byteArrayOf(7, 8, 9))
        val masterUrl = proxy.playbackUrl("$base/start")
        val master = URL(masterUrl).readText()
        assertFalse(master.contains(base))
        val mediaUrl = master.lineSequence().first { it.startsWith("http://") }
        assertTrue(mediaUrl.endsWith(".m3u8"))
        val media = URL(mediaUrl).readText()
        val uris = Regex("URI=\"([^\"]+)\"").findAll(media).map { it.groupValues[1] }.toList()
        assertArrayEquals(byteArrayOf(1, 2, 3), URL(uris[0]).readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), URL(uris[1]).readBytes())
        val segment = media.lineSequence().first { it.startsWith("http://") }
        assertTrue(segment.endsWith(".ts"))
        assertTrue(uris[1].endsWith(".mp4"))
        assertArrayEquals(byteArrayOf(7, 8, 9), URL(segment).readBytes())
        assertEquals(masterUrl, proxy.playbackUrl("$base/start"))
    }

    @Test fun preservesByteRangeRequestsAndResponses() {
        text("/media", "#EXTM3U\n#EXT-X-BYTERANGE:4@2\nchunk\n")
        server.createContext("/chunk") { exchange ->
            assertEquals("bytes=2-5", exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.add("Content-Range", "bytes 2-5/10")
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(206, 4)
            exchange.responseBody.use { it.write(byteArrayOf(2, 3, 4, 5)) }
            exchange.close()
        }
        val playlist = URL(proxy.playbackUrl("$base/media")).readText()
        val segment = playlist.lineSequence().first { it.startsWith("http://") }
        val connection = URL(segment).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Range", "bytes=2-5")
            assertEquals(206, connection.responseCode)
            assertEquals("bytes 2-5/10", connection.getHeaderField("Content-Range"))
            assertArrayEquals(byteArrayOf(2, 3, 4, 5), connection.inputStream.use { it.readBytes() })
        } finally { connection.disconnect() }
    }

    @Test fun fetchesMediaAgainEvenWhenUpstreamAllowsCaching() {
        text("/media", "#EXTM3U\nchunk.ts\n")
        val downloads = AtomicInteger()
        server.createContext("/chunk.ts") { exchange ->
            val bytes = byteArrayOf(downloads.incrementAndGet().toByte())
            exchange.responseHeaders.add("Cache-Control", "public, max-age=3600")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        val playlist = URL(proxy.playbackUrl("$base/media")).readText()
        val segment = playlist.lineSequence().first { it.startsWith("http://") }
        assertArrayEquals(byteArrayOf(1), URL(segment).readBytes())
        assertArrayEquals(byteArrayOf(2), URL(segment).readBytes())
        assertEquals(2, downloads.get())
    }

    @Test fun preservesInlineKeysAndRewritesHeadResponseLength() {
        text("/media", "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"data:application/octet-stream;base64,AQID\"\nchunk\n")
        val url = proxy.playbackUrl("$base/media")
        val get = URL(url).readText()
        assertTrue(get.contains("URI=\"data:application/octet-stream;base64,AQID\""))
        val head = URL(url).openConnection() as HttpURLConnection
        try {
            head.requestMethod = "HEAD"
            assertEquals(200, head.responseCode)
            assertEquals(get.toByteArray().size, head.contentLength)
            assertEquals(0, head.inputStream.use { it.readBytes().size })
        } finally { head.disconnect() }
    }

    @Test fun rejectsUnknownLocalRoutesWithoutFetchingUpstream() {
        text("/media", "#EXTM3U\n")
        val good = proxy.playbackUrl("$base/media")
        val bad = good.replace(Regex("/[^/]+/1/"), "/invalid/1/")
        val connection = URL(bad).openConnection() as HttpURLConnection
        try { assertEquals(404, connection.responseCode) } finally { connection.disconnect() }
        assertEquals(0, requests.get())
    }

    @Test fun propagatesUpstreamErrors() {
        server.createContext("/unavailable") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        val connection = URL(proxy.playbackUrl("$base/unavailable")).openConnection() as HttpURLConnection
        try { assertEquals(503, connection.responseCode) } finally { connection.disconnect() }
    }

    @Test fun closeDisconnectsActiveClientsAndStopsListener() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/slow") { exchange ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        val executor = Executors.newSingleThreadExecutor()
        val url = proxy.playbackUrl("$base/slow")
        try {
            val result = executor.submit<Boolean> {
                try { URL(url).readBytes(); false } catch (_: IOException) { true }
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            proxy.close()
            assertTrue(result.get(3, TimeUnit.SECONDS))
            try { URL(url).openConnection().apply { connectTimeout = 500 }.getInputStream(); fail("Proxy still accepts requests") } catch (_: IOException) { }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun text(path: String, body: String) = binary(path, body.toByteArray(), "application/vnd.apple.mpegurl")

    private fun binary(path: String, bytes: ByteArray, type: String = "application/octet-stream") {
        server.createContext(path) { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", type)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
    }
}
