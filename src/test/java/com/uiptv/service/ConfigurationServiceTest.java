package com.uiptv.service;

import com.uiptv.model.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
