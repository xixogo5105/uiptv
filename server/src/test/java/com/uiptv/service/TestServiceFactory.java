package com.uiptv.service;

import com.uiptv.util.FetchAPI;

public final class TestServiceFactory {
    private final HandshakeService handshakeService;
    private final CategoryService categoryService;
    private final CacheService cacheService;
    private final ChannelService channelService;
    private final PlayerService playerService;
    private final BingeWatchService bingeWatchService;
    private final SeriesEpisodeService seriesEpisodeService;

    private TestServiceFactory() {
        handshakeService = new HandshakeService(AccountService.INSTANCE, AccountInfoService.INSTANCE);
        categoryService = new CategoryService(
                ContentFilterService.INSTANCE,
                ConfigurationService.INSTANCE,
                handshakeService
        );

        final ChannelService[] channelRef = new ChannelService[1];
        cacheService = new CacheServiceImpl(
                () -> handshakeService,
                () -> categoryService,
                () -> ConfigurationService.INSTANCE,
                () -> channelRef[0],
                FetchAPI::fetch
        );
        channelService = new ChannelService(
                () -> cacheService,
                ContentFilterService.INSTANCE,
                LogoResolverService.INSTANCE,
                ConfigurationService.INSTANCE,
                handshakeService
        );
        channelRef[0] = channelService;

        playerService = new PlayerService(
                SeriesWatchStateService.INSTANCE,
                new XtremePlayerService(),
                new StalkerPortalPlayerService(AccountService.INSTANCE, handshakeService),
                new PredefinedPlayerService()
        );
        bingeWatchService = new BingeWatchService(AccountService.INSTANCE, SeriesWatchStateService.INSTANCE, playerService);
        seriesEpisodeService = new SeriesEpisodeService(channelService, ConfigurationService.INSTANCE);
    }

    public static TestServiceFactory create() {
        return new TestServiceFactory();
    }

    public CategoryService categoryService() {
        return categoryService;
    }

    public CacheService cacheService() {
        return cacheService;
    }

    public ChannelService channelService() {
        return channelService;
    }

    public PlayerService playerService() {
        return playerService;
    }

    public BingeWatchService bingeWatchService() {
        return bingeWatchService;
    }

    public SeriesEpisodeService seriesEpisodeService() {
        return seriesEpisodeService;
    }
}
