package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationServiceTest extends DbBackedTest {

    @Test
    void cacheExpiry_defaultsTo30Days_whenNotConfigured() {
        ConfigurationService service = ConfigurationService.getInstance();
        assertEquals(30, service.getCacheExpiryDays());
        assertEquals(30L * 24L * 60L * 60L * 1000L, service.getCacheExpiryMs());
    }

    @Test
    void cacheExpiry_usesConfiguredPositiveDays() {
        ConfigurationService service = ConfigurationService.getInstance();
        Configuration configuration = new Configuration();
        configuration.setCacheExpiryDays("7");
        service.save(configuration);

        assertEquals(7, service.getCacheExpiryDays());
        assertEquals(7L * 24L * 60L * 60L * 1000L, service.getCacheExpiryMs());
    }

    @Test
    void cacheExpiry_fallsBackToDefault_whenInvalidOrZero() {
        ConfigurationService service = ConfigurationService.getInstance();
        Configuration invalid = new Configuration();
        invalid.setCacheExpiryDays("abc");
        service.save(invalid);
        assertEquals(30, service.getCacheExpiryDays());

        Configuration zero = service.read();
        zero.setCacheExpiryDays("0");
        service.save(zero);
        assertEquals(30, service.getCacheExpiryDays());
    }

    @Test
    void litePlayerFfmpegFlag_defaultsToFalse_andPersistsWhenEnabled() {
        ConfigurationService service = ConfigurationService.getInstance();
        assertFalse(service.read().isEnableLitePlayerFfmpeg());

        Configuration configuration = service.read();
        configuration.setEnableLitePlayerFfmpeg(true);
        service.save(configuration);

        assertTrue(service.read().isEnableLitePlayerFfmpeg());
    }

    @Test
    void autoRunServerOnStartup_defaultsToFalse_andPersistsWhenEnabled() {
        ConfigurationService service = ConfigurationService.getInstance();
        assertFalse(service.read().isAutoRunServerOnStartup());

        Configuration configuration = service.read();
        configuration.setAutoRunServerOnStartup(true);
        service.save(configuration);

        assertTrue(service.read().isAutoRunServerOnStartup());
    }

    @Test
    void vlcSettings_defaultToOneSecondCaching_andEnabledFlags() {
        Configuration configuration = ConfigurationService.getInstance().read();

        assertEquals(ConfigurationService.DEFAULT_VLC_CACHING_MS,
                ConfigurationService.getInstance().normalizeVlcCachingMs(configuration.getVlcNetworkCachingMs()));
        assertEquals(ConfigurationService.DEFAULT_VLC_CACHING_MS,
                ConfigurationService.getInstance().normalizeVlcCachingMs(configuration.getVlcLiveCachingMs()));
        assertTrue(configuration.isEnableVlcHttpUserAgent());
        assertTrue(configuration.isEnableVlcHttpForwardCookies());
    }

    @Test
    void vlcSettings_persistCustomValues() {
        ConfigurationService service = ConfigurationService.getInstance();
        Configuration configuration = service.read();
        configuration.setVlcNetworkCachingMs("30000");
        configuration.setVlcLiveCachingMs("");
        configuration.setEnableVlcHttpUserAgent(false);
        configuration.setEnableVlcHttpForwardCookies(false);
        service.save(configuration);

        Configuration saved = service.read();
        assertEquals("30000", saved.getVlcNetworkCachingMs());
        assertEquals("", saved.getVlcLiveCachingMs());
        assertFalse(saved.isEnableVlcHttpUserAgent());
        assertFalse(saved.isEnableVlcHttpForwardCookies());
    }

    @Test
    void resolveChainAndDeepRedirects_defaultsToDisabled_andPersistsWhenEnabled() {
        ConfigurationService service = ConfigurationService.getInstance();
        assertFalse(service.isResolveChainAndDeepRedirectsEnabled());

        Configuration configuration = service.read();
        configuration.setResolveChainAndDeepRedirects(true);
        service.save(configuration);

        assertTrue(service.isResolveChainAndDeepRedirectsEnabled());
        assertTrue(service.read().isResolveChainAndDeepRedirects());
    }

    @Test
    void resolveChainAndDeepRedirects_accountOverrideEnablesFeatureWhenGlobalIsDisabled() {
        ConfigurationService service = ConfigurationService.getInstance();
        Account account = new Account();
        account.setResolveChainAndDeepRedirects(true);

        assertTrue(service.isResolveChainAndDeepRedirectsEnabled(account));
    }

    @Test
    void publishedM3uCategoryMode_defaultsToSourceDashCategory_andPersistsCustomValue() {
        ConfigurationService service = ConfigurationService.getInstance();
        assertEquals(M3U8PublicationService.PublishedCategoryMode.SOURCE_DASH_CATEGORY, service.getPublishedM3uCategoryMode());

        Configuration configuration = service.read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue());
        service.save(configuration);

        assertEquals(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP, service.getPublishedM3uCategoryMode());
        assertEquals(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue(),
                service.read().getPublishedM3uCategoryMode());
    }

}
