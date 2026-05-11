package com.uiptv.service.cache

import com.uiptv.service.CategoryService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.util.AccountType
import com.uiptv.util.koinOrNull

class AccountCacheReloaderFactory @JvmOverloads constructor(
    categoryServiceProvider: () -> CategoryService = { koinOrNull<CategoryService>() ?: CategoryService() },
    configurationServiceProvider: () -> ConfigurationService = { ConfigurationService },
    handshakeServiceProvider: () -> HandshakeService = { koinOrNull<HandshakeService>() ?: HandshakeService() },
    channelServiceProvider: () -> ChannelService = { koinOrNull<ChannelService>() ?: ChannelService() },
    fetchProvider: (Map<String, String>, com.uiptv.model.Account) -> String = com.uiptv.util.FetchAPI::fetch
) {
    private val stalkerReloader: AccountCacheReloader =
        StalkerPortalCacheReloader(handshakeServiceProvider, channelServiceProvider, categoryServiceProvider, configurationServiceProvider, fetchProvider)
    private val xtremeReloader: AccountCacheReloader =
        XtremeApiCacheReloader(categoryServiceProvider, configurationServiceProvider)
    private val m3uReloader: AccountCacheReloader =
        M3uCacheReloader(categoryServiceProvider, configurationServiceProvider)
    private val rssReloader: AccountCacheReloader =
        RssCacheReloader(categoryServiceProvider, configurationServiceProvider)

    fun get(accountType: AccountType?): AccountCacheReloader {
        require(accountType != null) { "Account type is required for cache reload." }
        return when (accountType) {
            AccountType.STALKER_PORTAL -> stalkerReloader
            AccountType.XTREME_API -> xtremeReloader
            AccountType.RSS_FEED -> rssReloader
            AccountType.M3U8_LOCAL, AccountType.M3U8_URL -> m3uReloader
        }
    }
}
