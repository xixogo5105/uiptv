package com.uiptv.service.cache

import com.uiptv.util.AccountType

class AccountCacheReloaderFactory {
    private val stalkerReloader: AccountCacheReloader = StalkerPortalCacheReloader()
    private val xtremeReloader: AccountCacheReloader = XtremeApiCacheReloader()
    private val m3uReloader: AccountCacheReloader = M3uCacheReloader()
    private val rssReloader: AccountCacheReloader = RssCacheReloader()

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
