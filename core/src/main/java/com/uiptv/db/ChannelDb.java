package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.uiptv.db.DatabaseUtils.DbTable.CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.DatabaseUtils.validatedTableName;
import static com.uiptv.db.SQLConnection.connect;

public class ChannelDb extends BaseDb {
    private static ChannelDb instance;
    private static final int BATCH_SIZE = 1000;


    public ChannelDb() {
        super(CHANNEL_TABLE);
    }

    public static synchronized ChannelDb get() {
        if (instance == null) {
            instance = new ChannelDb();
        }
        return instance;
    }

    public static void insert(Channel channel, Category category) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(CHANNEL_TABLE))) {
            statement.setString(1, channel.getChannelId());
            statement.setString(2, category.getDbId());
            statement.setString(3, channel.getName());
            statement.setString(4, channel.getNumber());
            statement.setString(5, channel.getCmd());
            statement.setString(6, channel.getCmd_1());
            statement.setString(7, channel.getCmd_2());
            statement.setString(8, channel.getCmd_3());
            statement.setString(9, channel.getLogo());
            statement.setInt(10, channel.getCensored());
            statement.setInt(11, channel.getStatus());
            statement.setInt(12, channel.getHd());
            statement.setString(13, channel.getDrmType());
            statement.setString(14, channel.getDrmLicenseUrl());
            statement.setString(15, channel.getClearKeysJson());
            statement.setString(16, channel.getInputstreamaddon());
            statement.setString(17, channel.getManifestType());
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute query", e);
        }
    }

    private static void deleteAll(String categoryId) {
        String sql = "DELETE FROM " + validatedTableName(CHANNEL_TABLE) + " WHERE categoryId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, categoryId);
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute delete all query", e);
        }
    }

    public List<Channel> getChannels(String dbId) {
        return getAll(" WHERE categoryId=?", new String[]{dbId});
    }

    public int getChannelCountForAccount(String accountId) {
        String sql = "SELECT COUNT(*) FROM " + validatedTableName(CHANNEL_TABLE) +
                " WHERE categoryId IN (" +
                "SELECT id FROM " + validatedTableName(CATEGORY_TABLE) + " WHERE accountId = ?)";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute channel count for account query", e);
        }
        return 0;
    }

    public Channel getChannelById(String dbId, String categoryId) {
        List<Channel> channels = getAll(" WHERE id=? AND categoryId=?", new String[]{dbId, categoryId});
        return (channels != null && !channels.isEmpty()) ? channels.get(0) : null;
    }

    public Channel getChannelByChannelIdAndAccount(String channelId, String accountId) {
        String sql = "SELECT c.* FROM " + validatedTableName(CHANNEL_TABLE) + " c" +
                " INNER JOIN " + validatedTableName(CATEGORY_TABLE) + " cat ON c.categoryId = cat.id" +
                " WHERE c.channelId = ? AND cat.accountId = ?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, channelId);
            statement.setString(2, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return populate(rs);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute getChannelByChannelIdAndAccount query", e);
        }
        return null;
    }

    @SuppressWarnings("java:S2077")
    public List<Channel> getChannelsByChannelIdsAndAccount(Collection<String> channelIds, String accountId) {
        if (channelIds == null || channelIds.isEmpty() || accountId == null || accountId.isBlank()) {
            return List.of();
        }

        List<String> effectiveChannelIds = channelIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (effectiveChannelIds.isEmpty()) {
            return List.of();
        }

        // The IN-clause shape is derived only from the number of validated ids; values are still bound safely.
        String placeholders = String.join(",", java.util.Collections.nCopies(effectiveChannelIds.size(), "?"));
        String sql = "SELECT c.* FROM " + validatedTableName(CHANNEL_TABLE) + " c" +
                " INNER JOIN " + validatedTableName(CATEGORY_TABLE) + " cat ON c.categoryId = cat.id" +
                " WHERE cat.accountId = ? AND c.channelId IN (" + placeholders + ")";

        List<Channel> channels = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            for (int i = 0; i < effectiveChannelIds.size(); i++) {
                statement.setString(i + 2, effectiveChannelIds.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    channels.add(populate(rs));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute getChannelsByChannelIdsAndAccount query", e);
        }
        return channels;
    }

    @SuppressWarnings("java:S1141")
    public void saveAll(List<Channel> channels, String dbCategoryId, Account account) {
        Category category = new CategoryDb().getCategoryByDbId(dbCategoryId, account);
        deleteAll(category.getDbId());
        List<Channel> dedupedChannels = dedupeChannelsCaseInsensitive(channels);
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(insertTableSql(CHANNEL_TABLE))) {
                int count = 0;
                for (Channel channel : dedupedChannels) {
                    statement.setString(1, channel.getChannelId());
                    statement.setString(2, category.getDbId());
                    statement.setString(3, channel.getName());
                    statement.setString(4, channel.getNumber());
                    statement.setString(5, channel.getCmd());
                    statement.setString(6, channel.getCmd_1());
                    statement.setString(7, channel.getCmd_2());
                    statement.setString(8, channel.getCmd_3());
                    statement.setString(9, channel.getLogo());
                    statement.setInt(10, channel.getCensored());
                    statement.setInt(11, channel.getStatus());
                    statement.setInt(12, channel.getHd());
                    statement.setString(13, channel.getDrmType());
                    statement.setString(14, channel.getDrmLicenseUrl());
                    statement.setString(15, channel.getClearKeysJson());
                    statement.setString(16, channel.getInputstreamaddon());
                    statement.setString(17, channel.getManifestType());
                    statement.addBatch();
                    if (++count % BATCH_SIZE == 0) {
                        statement.executeBatch();
                    }
                }
                statement.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new DatabaseAccessException("Unable to execute saveAll query", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to connect to database", e);
        }
    }

    private List<Channel> dedupeChannelsCaseInsensitive(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Channel channel : channels) {
            if (channel == null) {
                continue;
            }
            uniqueChannels.putIfAbsent(channelComparisonKey(channel), channel);
        }
        return new ArrayList<>(uniqueChannels.values());
    }

    private String channelComparisonKey(Channel channel) {
        String channelId = normalize(channel.getChannelId());
        if (!channelId.isEmpty()) {
            return "id:" + channelId;
        }
        String name = normalize(channel.getName());
        if (!name.isEmpty()) {
            return "name:" + name;
        }
        String cmd = normalize(channel.getCmd());
        if (!cmd.isEmpty()) {
            return "cmd:" + cmd;
        }
        return "fallback:" + System.identityHashCode(channel);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public void deleteByAccount(String accountId) {
        String sql = "DELETE FROM " + CHANNEL_TABLE.getTableName() +
                " WHERE categoryId IN (SELECT id FROM " + CATEGORY_TABLE.getTableName() + " WHERE accountId=?)";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute deleteByAccount query", e);
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
                null, // clearKeys map is reconstructed from JSON
                nullSafeString(resultSet, "inputstreamaddon"),
                nullSafeString(resultSet, "manifestType")
        );
        c.setDbId(nullSafeString(resultSet, "id"));
        c.setCategoryId(nullSafeString(resultSet, "categoryId"));
        c.setClearKeysJson(nullSafeString(resultSet, "clearKeysJson"));
        return c;
    }

}
