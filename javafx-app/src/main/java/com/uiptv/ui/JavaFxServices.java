package com.uiptv.ui;

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
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.service.PlayerService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.ThemeCssOverrideService;
import com.uiptv.service.VodWatchStateService;
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
    public static JavaFxServices defaults() {
        return new JavaFxServices(
                AccountService.INSTANCE,
                AccountInfoService.INSTANCE,
                AppDataRefreshService.INSTANCE,
                BingeWatchService.INSTANCE,
                BookmarkService.INSTANCE,
                CacheServiceImpl.INSTANCE,
                CategoryService.INSTANCE,
                ChannelService.INSTANCE,
                ConfigurationService.INSTANCE,
                DatabaseSyncService.INSTANCE,
                FilterLockService.INSTANCE,
                HandshakeService.INSTANCE,
                ImdbMetadataService.INSTANCE,
                M3U8PublicationService.INSTANCE,
                PlayerService.INSTANCE,
                RemoteSyncClientService.INSTANCE,
                SeriesEpisodeService.INSTANCE,
                SeriesWatchStateService.INSTANCE,
                SeriesWatchingNowSnapshotService.INSTANCE,
                ThemeCssOverrideService.INSTANCE,
                VodWatchStateService.INSTANCE
        );
    }
}
