package com.uiptv.server

import com.uiptv.server.api.routes.registerChannelApiRoutes
import com.uiptv.server.api.routes.registerCoreApiRoutes
import com.uiptv.server.api.routes.registerLegacyWebRoutes
import com.uiptv.server.api.routes.registerPlayerPublicationApiRoutes
import com.uiptv.server.api.routes.registerRemoteSyncApiRoutes
import com.uiptv.server.api.routes.registerSeriesApiRoutes
import com.uiptv.server.api.routes.registerVodApiRoutes
import com.uiptv.server.bootstrap.configureBackendPlatform
import com.uiptv.service.ConfigurationService
import com.uiptv.util.AppLog.addInfoLog
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import java.io.IOException
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
        fun start(extraModules: List<Module>) {
            KtorServerRuntime.start(extraModules)
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
    private var startupModules: List<Module> = emptyList()

    @JvmStatic
    @Throws(IOException::class)
    fun start() {
        start(emptyList())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun start(extraModules: List<Module>) {
        startupModules = extraModules
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
        startupModules = emptyList()
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
        val port = ConfigurationService.read().serverPort
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
            configureServerApplication(startupModules)
        }
    }
}

fun Application.configureServerApplication(extraModules: List<Module> = emptyList()) {
    configureBackendPlatform(extraModules)
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
    val bingeWatchService = get<com.uiptv.service.BingeWatchService>()
    routing {
        registerCoreApiRoutes(configurationService, accountService, categoryService, bookmarkService, channelService)
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
        registerLegacyWebRoutes(bingeWatchService)
    }
}
