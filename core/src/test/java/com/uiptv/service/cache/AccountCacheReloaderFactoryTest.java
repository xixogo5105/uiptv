package com.uiptv.service.cache;

import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AccountCacheReloaderFactoryTest {

    @Test
    void returnsFreshReloaderForEachAccountReload() {
        AccountCacheReloaderFactory factory = new AccountCacheReloaderFactory();

        AccountCacheReloader firstStalker = factory.get(AccountType.STALKER_PORTAL);
        AccountCacheReloader secondStalker = factory.get(AccountType.STALKER_PORTAL);
        AccountCacheReloader firstXtreme = factory.get(AccountType.XTREME_API);
        AccountCacheReloader secondXtreme = factory.get(AccountType.XTREME_API);
        AccountCacheReloader firstM3u = factory.get(AccountType.M3U8_URL);
        AccountCacheReloader secondM3u = factory.get(AccountType.M3U8_LOCAL);

        assertNotSame(firstStalker, secondStalker);
        assertNotSame(firstXtreme, secondXtreme);
        assertNotSame(firstM3u, secondM3u);
        assertSame(firstStalker.getClass(), secondStalker.getClass());
        assertSame(firstXtreme.getClass(), secondXtreme.getClass());
        assertSame(firstM3u.getClass(), secondM3u.getClass());
    }
}
