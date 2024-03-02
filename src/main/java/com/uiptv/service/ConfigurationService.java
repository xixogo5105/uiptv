package com.uiptv.service;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.model.Configuration;

public class ConfigurationService {

    private static ConfigurationService instance;

    private ConfigurationService() {
    }

    public static synchronized ConfigurationService getInstance() {
        if (instance == null) {
            instance = new ConfigurationService();
        }
        return instance;
    }

    public void clearCache() {
        ConfigurationDb.get().clearCache();
    }
    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }
}

