package com.uiptv.service

object RuntimeServices {
    @JvmField
    val accountService: AccountService = AccountService

    @JvmField
    val accountInfoService: AccountInfoService = AccountInfoService

    @JvmField
    val configurationService: ConfigurationService = ConfigurationService

    @JvmField
    val contentFilterService: ContentFilterService = ContentFilterService

    @JvmField
    val logoResolverService: LogoResolverService = LogoResolverService

    @JvmField
    val seriesWatchStateService: SeriesWatchStateService = SeriesWatchStateService

    @JvmField
    val bookmarkService: BookmarkService = BookmarkService

    val handshakeService: HandshakeService by lazy(LazyThreadSafetyMode.NONE) {
        HandshakeService(accountService, accountInfoService)
    }

    val categoryService: CategoryService by lazy(LazyThreadSafetyMode.NONE) {
        CategoryService(contentFilterService, configurationService, handshakeService)
    }

    val cacheService: CacheService by lazy(LazyThreadSafetyMode.NONE) {
        CacheServiceImpl(
            handshakeServiceProvider = { handshakeService },
            categoryServiceProvider = { categoryService },
            configurationServiceProvider = { configurationService },
            channelServiceProvider = { channelService }
        )
    }

    val channelService: ChannelService by lazy(LazyThreadSafetyMode.NONE) {
        ChannelService(
            cacheServiceProvider = { cacheService },
            contentFilterService = contentFilterService,
            logoResolverService = logoResolverService,
            configurationService = configurationService,
            handshakeService = handshakeService
        )
    }

    @JvmField
    val xtremePlayerService: XtremePlayerService = XtremePlayerService()

    @JvmField
    val predefinedPlayerService: PredefinedPlayerService = PredefinedPlayerService()

    val stalkerPortalPlayerService: StalkerPortalPlayerService by lazy(LazyThreadSafetyMode.NONE) {
        StalkerPortalPlayerService(accountService, handshakeService)
    }

    val playerService: PlayerService by lazy(LazyThreadSafetyMode.NONE) {
        PlayerService(
            seriesWatchStateService = seriesWatchStateService,
            xtremePlayerService = xtremePlayerService,
            stalkerPortalPlayerService = stalkerPortalPlayerService,
            predefinedPlayerService = predefinedPlayerService
        )
    }
}
