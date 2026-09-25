package com.uiptv.service.cache;

import com.uiptv.util.AccountType;

public class AccountCacheReloaderFactory {
    public AccountCacheReloader get(AccountType accountType) {
        if (accountType == null) {
            throw new IllegalArgumentException("Account type is required for cache reload.");
        }
        return switch (accountType) {
            // Reloaders are deliberately request-scoped. Bulk refresh invokes this
            // factory repeatedly while switching accounts (and provider types); a
            // shared instance would allow provider-specific transient state to leak
            // from one account into the next.
            case STALKER_PORTAL -> new StalkerPortalCacheReloader();
            case XTREME_API -> new XtremeApiCacheReloader();
            case M3U8_LOCAL, M3U8_URL -> new M3uCacheReloader();
        };
    }
}
