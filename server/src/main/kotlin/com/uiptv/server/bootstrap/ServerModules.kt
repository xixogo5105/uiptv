package com.uiptv.server.bootstrap

import com.uiptv.db.SqlConnectionRuntime
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.CategoryService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.service.PlayerService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.service.SeriesEpisodeService
import com.uiptv.service.SeriesWatchingNowSnapshotService
import com.uiptv.service.VodWatchStateService
import com.uiptv.service.PlaylistExportService
import com.uiptv.service.WebPlayerApiService
import com.uiptv.service.WatchingNowSeriesResolver
import com.uiptv.service.WatchingNowVodResolver
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.util.HttpClientFactory
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import org.koin.dsl.module

val serverInfrastructureModule = module {
    single<HikariDataSource> { SqlConnectionRuntime.dataSource() }
    single<HttpClient> { HttpClientFactory.shared() }
}

val serverServiceModule = module {
    single { ConfigurationService.getInstance() }
    single { AccountService.getInstance() }
    single { CategoryService.getInstance() }
    single { ChannelService.getInstance() }
    single { BookmarkService.getInstance() }
    single { PlayerService.getInstance() }
    single { PlayerRequestResolver() }
    single { WebPlayerApiService(playerRequestResolverProvider = { get<PlayerRequestResolver>() }) }
    single { PlaylistExportService(playerRequestResolverProvider = { get<PlayerRequestResolver>() }) }
    single { SeriesWatchStateService.getInstance() }
    single { SeriesEpisodeService.getInstance() }
    single { SeriesWatchingNowSnapshotService.getInstance() }
    single { VodWatchStateService.getInstance() }
    single { HandshakeService.getInstance() }
    single { ImdbMetadataService.getInstance() }
    single { WatchingNowSeriesResolver() }
    single { WatchingNowVodResolver() }
    single { RemoteSyncSessionService.getInstance() }
}
