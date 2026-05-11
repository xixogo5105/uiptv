package com.uiptv.server.bootstrap

import com.uiptv.db.SqlConnectionRuntime
import com.uiptv.service.AccountService
import com.uiptv.service.AccountInfoService
import com.uiptv.service.BingeWatchService
import com.uiptv.service.BookmarkService
import com.uiptv.service.CategoryService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.CacheService
import com.uiptv.service.CacheServiceImpl
import com.uiptv.service.ContentFilterService
import com.uiptv.service.DatabaseSyncService
import com.uiptv.service.FfmpegService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.service.LogoResolverService
import com.uiptv.service.M3U8PublicationService
import com.uiptv.service.PlayerService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.service.SeriesWatchingNowSnapshotService
import com.uiptv.service.SeriesEpisodeService
import com.uiptv.service.VodWatchStateService
import com.uiptv.service.PlaylistExportService
import com.uiptv.service.PredefinedPlayerService
import com.uiptv.service.XtremePlayerService
import com.uiptv.service.WebPlayerApiService
import com.uiptv.service.WatchingNowSeriesResolver
import com.uiptv.service.WatchingNowVodResolver
import com.uiptv.service.StalkerPortalPlayerService
import com.uiptv.service.remotesync.DatabaseSnapshotService
import com.uiptv.service.remotesync.RemoteSyncClientService
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
    single { DatabaseSnapshotService() }
    single { AccountInfoService }
    single { ConfigurationService }
    single { AccountService }
    single { BookmarkService }
    single { SeriesWatchStateService }
    single { SeriesWatchingNowSnapshotService }
    single { VodWatchStateService }
    single { ContentFilterService }
    single { LogoResolverService }
    single { DatabaseSyncService }
    single<XtremePlayerService> { XtremePlayerService() }
    single<PredefinedPlayerService> { PredefinedPlayerService() }
    single { HandshakeService(get(), get()) }
    single { StalkerPortalPlayerService(get(), get()) }
    single<CacheService> { CacheServiceImpl({ get() }, { get() }, { get() }, { get() }, com.uiptv.util.FetchAPI::fetch) }
    single { ImdbMetadataService }
    single { CategoryService(get(), get(), get()) }
    single { ChannelService(cacheServiceProvider = { get() }, contentFilterService = get(), logoResolverService = get(), configurationService = get(), handshakeService = get()) }
    single { PlayerService(get(), get(), get(), get()) }
    single { BingeWatchService(get(), get(), get()) }
    single { M3U8PublicationService(get(), get(), get()) }
    single { SeriesEpisodeService(get(), get()) }
    single { PlayerRequestResolver(get(), get(), get(), com.uiptv.db.SeriesCategoryDb.get(), com.uiptv.db.VodChannelDb.get(), com.uiptv.db.ChannelDb.get()) }
    single { WebPlayerApiService(get(), get(), FfmpegService, get(), get()) }
    single { PlaylistExportService(get(), get(), get(), get(), get(), get(), com.uiptv.db.ChannelDb.get(), get()) }
    single { WatchingNowSeriesResolver(get(), get(), get()) }
    single { WatchingNowVodResolver(get(), get()) }
    single { RemoteSyncSessionService.runtimeInstance(get(), get()) }
    single { RemoteSyncClientService(databaseSyncService = get()) }
}
