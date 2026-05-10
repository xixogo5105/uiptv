package com.uiptv.server.bootstrap

import com.uiptv.db.SqlConnectionRuntime
import com.uiptv.service.AccountService
import com.uiptv.service.AccountInfoService
import com.uiptv.service.BingeWatchService
import com.uiptv.service.BookmarkService
import com.uiptv.service.CategoryService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.FfmpegService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.service.M3U8PublicationService
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
    single { ConfigurationService }
    single { AccountService }
    single { BookmarkService }
    single { SeriesWatchStateService }
    single { SeriesWatchingNowSnapshotService }
    single { VodWatchStateService }
    single { HandshakeService.configureDependencies(get(), AccountInfoService) }
    single { ImdbMetadataService }
    single { CategoryService }
    single { ChannelService.getInstance() }
    single { PlayerService.configureDependencies(get()) }
    single { BingeWatchService.getInstance() }
    single { M3U8PublicationService.configureDependencies(get(), get(), get()) }
    single { SeriesEpisodeService }
    single { PlayerRequestResolver(get(), get(), get()) }
    single { WebPlayerApiService(get(), get(), FfmpegService, get(), get()) }
    single { PlaylistExportService(get(), get(), get(), get(), get(), get()) }
    single { WatchingNowSeriesResolver(get(), get(), get()) }
    single { WatchingNowVodResolver(get(), get()) }
    single { RemoteSyncSessionService.getInstance() }
}
