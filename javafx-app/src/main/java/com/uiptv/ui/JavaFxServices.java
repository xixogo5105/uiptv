package com.uiptv.ui;

import com.uiptv.db.PublishedM3uCategorySelectionDb;
import com.uiptv.db.PublishedM3uChannelSelectionDb;
import com.uiptv.db.PublishedM3uSelectionDb;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.AccountService;
import com.uiptv.service.AppDataRefreshService;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.service.FilterLockService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.LogoResolverService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.service.PlayerService;
import com.uiptv.service.PredefinedPlayerService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.ThemeCssOverrideService;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.service.ContentFilterService;
import com.uiptv.service.StalkerPortalPlayerService;
import com.uiptv.service.XtremePlayerService;
import com.uiptv.service.remotesync.RemoteSyncClientService;

public record JavaFxServices(
        AccountService accountService,
        AccountInfoService accountInfoService,
        AppDataRefreshService appDataRefreshService,
        BingeWatchService bingeWatchService,
        BookmarkService bookmarkService,
        CacheService cacheService,
        CategoryService categoryService,
        ChannelService channelService,
        ConfigurationService configurationService,
        DatabaseSyncService databaseSyncService,
        FilterLockService filterLockService,
        HandshakeService handshakeService,
        ImdbMetadataService imdbMetadataService,
        M3U8PublicationService m3u8PublicationService,
        PlayerService playerService,
        RemoteSyncClientService remoteSyncClientService,
        SeriesEpisodeService seriesEpisodeService,
        SeriesWatchStateService seriesWatchStateService,
        SeriesWatchingNowSnapshotService seriesWatchingNowSnapshotService,
        ThemeCssOverrideService themeCssOverrideService,
        VodWatchStateService vodWatchStateService
) {
    private static volatile JavaFxServices current;

    public static JavaFxServices defaults() {
        var accountService = AccountService.INSTANCE;
        var accountInfoService = AccountInfoService.INSTANCE;
        var appDataRefreshService = AppDataRefreshService.INSTANCE;
        var bookmarkService = BookmarkService.INSTANCE;
        var configurationService = ConfigurationService.INSTANCE;
        var databaseSyncService = DatabaseSyncService.INSTANCE;
        var filterLockService = FilterLockService.INSTANCE;
        var themeCssOverrideService = ThemeCssOverrideService.INSTANCE;
        var seriesWatchStateService = SeriesWatchStateService.INSTANCE;
        var seriesWatchingNowSnapshotService = SeriesWatchingNowSnapshotService.INSTANCE;
        var vodWatchStateService = VodWatchStateService.INSTANCE;
        var imdbMetadataService = ImdbMetadataService.INSTANCE;

        var handshakeService = new HandshakeService(accountService, accountInfoService);
        var categoryService = new CategoryService(
                ContentFilterService.INSTANCE,
                configurationService,
                handshakeService
        );

        final CacheService[] cacheRef = new CacheService[1];
        final ChannelService[] channelRef = new ChannelService[1];

        cacheRef[0] = new CacheServiceImpl(
                () -> handshakeService,
                () -> categoryService,
                () -> configurationService,
                () -> channelRef[0],
                com.uiptv.util.FetchAPI::fetch
        );
        channelRef[0] = new ChannelService(
                () -> cacheRef[0],
                ContentFilterService.INSTANCE,
                LogoResolverService.INSTANCE,
                configurationService,
                handshakeService
        );

        var stalkerPortalPlayerService = new StalkerPortalPlayerService(accountService, handshakeService);
        var playerService = new PlayerService(
                seriesWatchStateService,
                new XtremePlayerService(),
                stalkerPortalPlayerService,
                new PredefinedPlayerService()
        );
        var bingeWatchService = new BingeWatchService(accountService, seriesWatchStateService, playerService);
        var seriesEpisodeService = new SeriesEpisodeService(channelRef[0], configurationService);
        var m3u8PublicationService = new M3U8PublicationService(
                accountService,
                bookmarkService,
                configurationService,
                PublishedM3uSelectionDb.get(),
                PublishedM3uCategorySelectionDb.get(),
                PublishedM3uChannelSelectionDb.get()
        );
        var remoteSyncClientService = RemoteSyncClientService.INSTANCE;

        return new JavaFxServices(
                accountService,
                accountInfoService,
                appDataRefreshService,
                bingeWatchService,
                bookmarkService,
                cacheRef[0],
                categoryService,
                channelRef[0],
                configurationService,
                databaseSyncService,
                filterLockService,
                handshakeService,
                imdbMetadataService,
                m3u8PublicationService,
                playerService,
                remoteSyncClientService,
                seriesEpisodeService,
                seriesWatchStateService,
                seriesWatchingNowSnapshotService,
                themeCssOverrideService,
                vodWatchStateService
        );
    }

    public static JavaFxServices current() {
        JavaFxServices services = current;
        if (services == null) {
            services = defaults();
            current = services;
        }
        return services;
    }

    public static void configureCurrent(JavaFxServices services) {
        if (services != null) {
            current = services;
        }
    }
}
