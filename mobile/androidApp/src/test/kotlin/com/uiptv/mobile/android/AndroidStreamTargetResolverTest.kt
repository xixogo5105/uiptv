package com.uiptv.mobile.android

import com.sun.net.httpserver.HttpServer
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.playback.PlaybackTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class AndroidStreamTargetResolverTest {
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After fun stopServer() = server.stop(0)

    @Test fun resolvesExtensionlessRedirectAndHlsContentTypeWithoutLosingIdentity() = runBlocking {
        server.createContext("/channel") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Location", "/opaque-final?token=preserve")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        response("/opaque-final", "application/vnd.apple.mpegurl; charset=utf-8", "#EXTM3U\n")
        val original = target("/channel")
        val resolved = resolveAndroidStreamTarget(original)
        assertEquals(original.copy(url = "$baseUrl/opaque-final?token=preserve", manifestType = "hls"), resolved)
        assertEquals(2, requests.get())
    }

    @Test fun detectsHlsWhenServerOmitsManifestContentType() = runBlocking {
        response("/channel", "application/octet-stream", "\uFEFF \r\n#EXTM3U\n#EXT-X-VERSION:3")
        assertEquals("hls", resolveAndroidStreamTarget(target("/channel")).manifestType)
    }

    @Test fun detectsDashAfterXmlDeclaration() = runBlocking {
        response("/channel", "application/octet-stream", "<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\">")
        assertEquals("mpd", resolveAndroidStreamTarget(target("/channel")).manifestType)
    }

    @Test fun preservesOriginalTargetForHtmlOrHttpFailure() = runBlocking {
        response("/html", "text/html", "<html>Access denied</html>")
        response("/failure", "text/html", "Server error", 503)
        for (path in listOf("/html", "/failure", "/missing")) {
            val original = target(path)
            assertEquals(original, resolveAndroidStreamTarget(original))
        }
    }

    @Test fun doesNotProbeDeclaredFormatsOrRegularVideoUrls() = runBlocking {
        response("/video.mp4", "video/mp4", "unused")
        val video = target("/video.mp4")
        val declared = target("/opaque").copy(manifestType = "hls")
        assertEquals(video, resolveAndroidStreamTarget(video))
        assertEquals(declared, resolveAndroidStreamTarget(declared))
        assertEquals(0, requests.get())
    }

    @Test fun stopsRedirectLoopsAndKeepsOriginalUrl() = runBlocking {
        server.createContext("/loop") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Location", "/loop")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        val original = target("/loop")
        assertEquals(original, resolveAndroidStreamTarget(original))
        assertEquals(6, requests.get())
    }

    private fun response(path: String, type: String, body: String, status: Int = 200) {
        server.createContext(path) { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", type)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
    }

    private fun target(path: String) = PlaybackTarget(
        accountId = 41, accountName = "Playlist", mode = BrowseMode.LIVE,
        categoryProviderId = "movies", categoryRowId = 17, channelId = "provider-channel",
        title = "Bollygold", url = "$baseUrl$path", logo = "", drmType = "",
        drmLicenseUrl = "", clearKeysJson = "", inputstreamAddon = ""
    )
}
