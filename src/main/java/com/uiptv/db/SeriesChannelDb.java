package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class SeriesChannelDb extends BaseDb {
    private static SeriesChannelDb instance;

    public SeriesChannelDb() {
        super(SERIES_CHANNEL_TABLE);
    }

    public static synchronized SeriesChannelDb get() {
        if (instance == null) {
            instance = new SeriesChannelDb();
        }
        return instance;
    }

    public List<Channel> getChannels(Account account, String categoryId) {
        return getAll(" WHERE accountId=? AND categoryId=?", new String[]{account.getDbId(), categoryId});
    }

    public List<Channel> getChannelsBySeriesIds(Account account, List<String> seriesIds) {
        if (account == null || seriesIds == null || seriesIds.isEmpty()) {
            return List.of();
        }
        List<String> filtered = seriesIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (filtered.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(filtered.size(), "?"));
        String where = " WHERE accountId=? AND channelId IN (" + placeholders + ")";
        List<String> params = new ArrayList<>(filtered.size() + 1);
        params.add(account.getDbId());
        params.addAll(filtered);
        return getAll(where, params.toArray(new String[0]));
    }

    public boolean isFresh(Account account, String categoryId, long maxAgeMs) {
        String sql = "SELECT MAX(cachedAt) FROM " + SERIES_CHANNEL_TABLE.getTableName() + " WHERE accountId=? AND categoryId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long cachedAt = rs.getLong(1);
                    return cachedAt > 0 && (System.currentTimeMillis() - cachedAt) <= maxAgeMs;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    public void saveAll(List<Channel> channels, String categoryId, Account account) {
        deleteByAccountAndCategory(account.getDbId(), categoryId);
        long cachedAt = System.currentTimeMillis();
        channels.forEach(c -> insert(c, categoryId, account, cachedAt));
    }

    public void deleteByAccount(String accountId) {
        String sql = "DELETE FROM " + SERIES_CHANNEL_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    private void deleteByAccountAndCategory(String accountId, String categoryId) {
        String sql = "DELETE FROM " + SERIES_CHANNEL_TABLE.getTableName() + " WHERE accountId=? AND categoryId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, categoryId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    private void insert(Channel channel, String categoryId, Account account, long cachedAt) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(SERIES_CHANNEL_TABLE))) {
            statement.setString(1, channel.getChannelId());
            statement.setString(2, categoryId);
            statement.setString(3, account.getDbId());
            statement.setString(4, channel.getName());
            statement.setString(5, channel.getNumber());
            statement.setString(6, channel.getCmd());
            statement.setString(7, channel.getCmd_1());
            statement.setString(8, channel.getCmd_2());
            statement.setString(9, channel.getCmd_3());
            statement.setString(10, channel.getLogo());
            statement.setInt(11, channel.getCensored());
            statement.setInt(12, channel.getStatus());
            statement.setInt(13, channel.getHd());
            statement.setString(14, channel.getDrmType());
            statement.setString(15, channel.getDrmLicenseUrl());
            statement.setString(16, channel.getClearKeysJson());
            statement.setString(17, channel.getInputstreamaddon());
            statement.setString(18, channel.getManifestType());
            statement.setString(19, channel.getExtraJson());
            statement.setLong(20, cachedAt);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute insert query", e);
        }
    }

    @Override
    Channel populate(ResultSet resultSet) {
        Channel c = new Channel(
                nullSafeString(resultSet, "channelId"),
                nullSafeString(resultSet, "name"),
                nullSafeString(resultSet, "number"),
                nullSafeString(resultSet, "cmd"),
                nullSafeString(resultSet, "cmd_1"),
                nullSafeString(resultSet, "cmd_2"),
                nullSafeString(resultSet, "cmd_3"),
                nullSafeString(resultSet, "logo"),
                safeInteger(resultSet, "censored"),
                safeInteger(resultSet, "status"),
                safeInteger(resultSet, "hd"),
                nullSafeString(resultSet, "drmType"),
                nullSafeString(resultSet, "drmLicenseUrl"),
                null,
                nullSafeString(resultSet, "inputstreamaddon"),
                nullSafeString(resultSet, "manifestType")
        );
        c.setDbId(nullSafeString(resultSet, "id"));
        c.setCategoryId(nullSafeString(resultSet, "categoryId"));
        c.setClearKeysJson(nullSafeString(resultSet, "clearKeysJson"));
        c.setExtraJson(nullSafeString(resultSet, "extraJson"));
        return c;
    }
}
