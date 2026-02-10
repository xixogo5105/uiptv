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
        if (account == null || account.getDbId() == null) {
            return;
        }
        try (Connection conn = connect()) {
            String deleteChannelsSql = "DELETE FROM " + DatabaseUtils.DbTable.CHANNEL_TABLE.getTableName() + " WHERE accountId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteChannelsSql)) {
                pstmt.setString(1, account.getDbId());
                pstmt.executeUpdate();
            }

            String deleteCategoriesSql = "DELETE FROM " + DatabaseUtils.DbTable.CATEGORY_TABLE.getTableName() + " WHERE accountId = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteCategoriesSql)) {
                pstmt.setString(1, account.getDbId());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            // Log or handle the exception
        }
    }

    public void save(Configuration configuration) {
        ConfigurationDb.get().save(configuration);
    }

    public Configuration read() {
        return ConfigurationDb.get().getConfiguration();
    }
}
