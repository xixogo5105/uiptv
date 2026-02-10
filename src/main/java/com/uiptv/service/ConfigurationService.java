package com.uiptv.service;

import com.uiptv.db.ConfigurationDb;
import com.uiptv.db.DatabaseUtils;
import com.uiptv.model.Account;
import com.uiptv.model.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.uiptv.db.SQLConnection.connect;

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

    public void clearCache(Account account) {
        ConfigurationDb.get().clearCache(account);
    }

    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }
}
