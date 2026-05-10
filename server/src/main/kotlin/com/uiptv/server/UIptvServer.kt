package com.uiptv.server

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpPrincipal
import com.uiptv.server.api.routes.registerChannelApiRoutes
import com.uiptv.server.api.routes.registerCoreApiRoutes
import com.uiptv.server.api.routes.registerPlayerPublicationApiRoutes
import com.uiptv.server.api.routes.registerRemoteSyncApiRoutes
import com.uiptv.server.api.routes.registerSeriesApiRoutes
import com.uiptv.server.api.routes.registerVodApiRoutes
import com.uiptv.server.bootstrap.configureBackendPlatform
import com.uiptv.server.html.HttpSpaHtmlServer
import com.uiptv.service.ConfigurationService
import com.uiptv.util.AppLog.addInfoLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.BadContentTypeFormatException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import org.koin.ktor.ext.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

private var applicationEngine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

class UIptvServer private constructor() {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun start() {
            KtorServerRuntime.start()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun ensureStarted(): Boolean = KtorServerRuntime.ensureStarted()

        @JvmStatic
        fun stop() {
            KtorServerRuntime.stop()
        }

        @JvmStatic
        fun isRunning(): Boolean = KtorServerRuntime.isRunning()

        @JvmStatic
        private fun getHttpPort(): String = KtorServerRuntime.getHttpPort()

        @JvmStatic
        private fun namedThreadFactory(prefix: String): ThreadFactory = KtorServerRuntime.namedThreadFactory(prefix)
    }
}

object KtorServerRuntime {
    @JvmStatic
    @Throws(IOException::class)
    fun start() {
        initialiseServer()
        applicationEngine!!.start(wait = false)
        addInfoLog(UIptvServer::class.java, "Server Started on port ${getHttpPort()}")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun ensureStarted(): Boolean {
        if (isRunning()) {
            return false
        }
        initialiseServer()
        applicationEngine!!.start(wait = false)
        addInfoLog(UIptvServer::class.java, "Server Started on port ${getHttpPort()}")
        return true
    }

    @JvmStatic
    fun stop() {
        val engine = applicationEngine
        applicationEngine = null
        if (engine != null) {
            Thread({
                try {
                    engine.stop(0, 0)
                } catch (_: Exception) {
                    // Best-effort shutdown for tests and local server restarts.
                }
            }, "uiptv-ktor-stop").apply { isDaemon = true }.start()
        }
        addInfoLog(UIptvServer::class.java, "Server Stopped")
    }

    @JvmStatic
    fun isRunning(): Boolean = applicationEngine != null

    @JvmStatic
    fun getHttpPort(): String {
        val port = ConfigurationService.getInstance().read().serverPort
        return if (port.isNullOrBlank()) "8888" else port
    }

    @JvmStatic
    fun namedThreadFactory(prefix: String): ThreadFactory {
        val sequence = AtomicInteger(1)
        return ThreadFactory { runnable ->
            Thread(runnable, prefix + sequence.getAndIncrement()).apply { isDaemon = true }
        }
    }

    private fun initialiseServer() {
        stop()
        val port = getHttpPort().toInt()
        applicationEngine = embeddedServer(Netty, host = "0.0.0.0", port = port) {
            configureRoutes()
        }
    }

    private fun Application.configureRoutes() {
        configureBackendPlatform()
        val configurationService = get<ConfigurationService>()
        val accountService = get<com.uiptv.service.AccountService>()
        val categoryService = get<com.uiptv.service.CategoryService>()
        val bookmarkService = get<com.uiptv.service.BookmarkService>()
        val channelService = get<com.uiptv.service.ChannelService>()
        val seriesWatchStateService = get<com.uiptv.service.SeriesWatchStateService>()
        val seriesEpisodeService = get<com.uiptv.service.SeriesEpisodeService>()
        val seriesWatchingNowSnapshotService = get<com.uiptv.service.SeriesWatchingNowSnapshotService>()
        val watchingNowSeriesResolver = get<com.uiptv.service.WatchingNowSeriesResolver>()
        val handshakeService = get<com.uiptv.service.HandshakeService>()
        val imdbMetadataService = get<com.uiptv.service.ImdbMetadataService>()
        val vodWatchStateService = get<com.uiptv.service.VodWatchStateService>()
        val watchingNowVodResolver = get<com.uiptv.service.WatchingNowVodResolver>()
        val webPlayerApiService = get<com.uiptv.service.WebPlayerApiService>()
        val playlistExportService = get<com.uiptv.service.PlaylistExportService>()
        val remoteSyncSessionService = get<com.uiptv.service.remotesync.RemoteSyncSessionService>()
        routing {
            registerCoreApiRoutes(configurationService, accountService, categoryService, bookmarkService)
            registerChannelApiRoutes(accountService, channelService, configurationService, seriesWatchStateService)
            registerSeriesApiRoutes(accountService, configurationService, seriesWatchStateService, handshakeService, imdbMetadataService)
            registerVodApiRoutes(
                accountService,
                handshakeService,
                imdbMetadataService,
                seriesWatchStateService,
                seriesEpisodeService,
                seriesWatchingNowSnapshotService,
                watchingNowSeriesResolver,
                vodWatchStateService,
                watchingNowVodResolver
            )
            registerRemoteSyncApiRoutes(remoteSyncSessionService)
            registerPlayerPublicationApiRoutes(webPlayerApiService, playlistExportService)
            legacyAny("/", HttpSpaHtmlServer())
            legacyAny("/index.html", HttpSpaHtmlServer())
            legacyAny("/myflix.html", HttpSpaHtmlServer("myflix.html"))
            legacyAny("/player.html", HttpSpaHtmlServer("player.html"))
            legacyAny("/drm.html", HttpSpaHtmlServer("player.html"))
            legacyAny("/manifest.json", HttpManifestServer())
            legacyAny("/sw.js", HttpJavascriptServer())
            legacyAny("/icon.ico", HttpIconServer())
            legacyAny("/javascript/{...}", HttpJavascriptServer())
            legacyAny("/js/{...}", HttpJavascriptServer())
            legacyAny("/css/{...}", HttpCssServer())
            legacyAny("/hls/{...}", HttpHlsFileServer())
            legacyAny("/hls-upload/{...}", HttpHlsUploadServer())
            legacyAny("/proxy-stream/{...}", HttpProxyStreamServer())
            legacyAny("/bingewatch.m3u8", HttpBingeWatchPlaylistServer())
            legacyAny("/bingwatch/{...}", HttpBingeWatchEntryServer())
            legacyAny("/{...}", HttpSpaHtmlServer())
        }
    }

    private fun Route.legacyAny(path: String, handler: HttpHandler) {
        route(path) {
            handle {
                val exchange = KtorHttpExchange(call, readRequestBytes(call.receiveChannel()))
                handler.handle(exchange)
                exchange.respond()
            }
        }
    }

    private suspend fun readRequestBytes(channel: ByteReadChannel): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) {
                break
            }
            if (read > 0) {
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }
}

private class KtorHttpExchange(
    private val call: ApplicationCall,
    requestBytes: ByteArray
) : HttpExchange() {
    private val requestHeaders = Headers().apply {
        call.request.headers.forEach { key, values -> values.forEach { add(key, it) } }
    }
    private val responseHeaders = Headers()
    private val responseBody = ByteArrayOutputStream()
    private val requestBody = ByteArrayInputStream(requestBytes)
    private val attributes = mutableMapOf<String, Any>()
    private var responseCode = 200
    private var responseLength = 0L

    override fun getRequestHeaders(): Headers = requestHeaders

    override fun getResponseHeaders(): Headers = responseHeaders

    override fun getRequestURI(): URI = URI.create(call.request.uri)

    override fun getRequestMethod(): String = call.request.httpMethod.value

    override fun getHttpContext(): HttpContext? = null

    override fun close() {
        responseBody.close()
    }

    override fun getRequestBody(): InputStream = requestBody

    override fun getResponseBody(): OutputStream = responseBody

    override fun sendResponseHeaders(rCode: Int, responseLength: Long) {
        responseCode = rCode
        this.responseLength = responseLength
    }

    override fun getRemoteAddress(): InetSocketAddress? = null

    override fun getResponseCode(): Int = responseCode

    override fun getLocalAddress(): InetSocketAddress? = null

    override fun getProtocol(): String = "HTTP/1.1"

    override fun getAttribute(name: String?): Any? = name?.let(attributes::get)

    override fun setAttribute(name: String?, value: Any?) {
        if (name == null || value == null) {
            return
        }
        attributes[name] = value
    }

    override fun setStreams(i: InputStream?, o: OutputStream?) {}

    override fun getPrincipal(): HttpPrincipal? = null

    suspend fun respond() {
        responseHeaders.forEach { key, values -> values.forEach { value -> call.response.header(key, value) } }
        val contentType = responseHeaders.getFirst("Content-Type")?.let(::parseContentTypeSafely)
        val status = HttpStatusCode.fromValue(responseCode)
        val bytes = responseBody.toByteArray()
        call.respondBytes(
            bytes = if (responseLength == -1L) ByteArray(0) else bytes,
            contentType = contentType,
            status = status
        )
    }

    private fun parseContentTypeSafely(raw: String): ContentType? =
        try {
            ContentType.parse(raw)
        } catch (_: BadContentTypeFormatException) {
            if (!raw.contains('/')) {
                ContentType.parse("application/$raw")
            } else {
                null
            }
        }
}
