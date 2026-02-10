package com.uiptv.service;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.model.Account;
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
    public void clearCache(Account account) {
        ConfigurationDb.get().clearCache(account);
    }
    public void clearAllCache() {
        ConfigurationDb.get().clearAllCache();
    }

    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }
}
