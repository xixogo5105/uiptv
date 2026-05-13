package com.uiptv.service.cache;

import com.uiptv.util.AccountType;

public class AccountCacheReloaderFactory {
    private final AccountCacheReloader stalkerReloader = new StalkerPortalCacheReloader();
    private final AccountCacheReloader xtremeReloader = new XtremeApiCacheReloader();
    private final AccountCacheReloader m3uReloader = new M3uCacheReloader();
    private final AccountCacheReloader rssReloader = new RssCacheReloader();

    public AccountCacheReloader get(AccountType accountType) {
        if (accountType == null) {
            throw new IllegalArgumentException("Account type is required for cache reload.");
        }
        return switch (accountType) {
            case STALKER_PORTAL -> stalkerReloader;
            case XTREME_API -> xtremeReloader;
            case RSS_FEED -> rssReloader;
            case M3U8_LOCAL, M3U8_URL -> m3uReloader;
        };
    }
}
