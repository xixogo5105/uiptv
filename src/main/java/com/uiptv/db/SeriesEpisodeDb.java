package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_EPISODE_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class SeriesEpisodeDb extends BaseDb {
    private static SeriesEpisodeDb instance;

    public SeriesEpisodeDb() {
        super(SERIES_EPISODE_TABLE);
    }

    public static synchronized SeriesEpisodeDb get() {
        if (instance == null) {
            instance = new SeriesEpisodeDb();
        }
        return instance;
    }

    public List<Channel> getEpisodes(Account account, String categoryId, String seriesId) {
        return getAll(" WHERE accountId=? AND categoryId=? AND seriesId=?", new String[]{account.getDbId(), safeCategoryId(categoryId), seriesId});
    }

    public boolean isFresh(Account account, String categoryId, String seriesId, long maxAgeMs) {
        String sql = "SELECT MAX(cachedAt) FROM " + SERIES_EPISODE_TABLE.getTableName() + " WHERE accountId=? AND categoryId=? AND seriesId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, safeCategoryId(categoryId));
            statement.setString(3, seriesId);
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

    public void saveAll(Account account, String categoryId, String seriesId, List<Channel> episodes) {
        deleteBySeries(account.getDbId(), categoryId, seriesId);
        long cachedAt = System.currentTimeMillis();
        for (Channel channel : episodes) {
            insert(account, categoryId, seriesId, channel, cachedAt);
        }
    }

    public void deleteByAccount(String accountId) {
        String sql = "DELETE FROM " + SERIES_EPISODE_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    private void deleteBySeries(String accountId, String categoryId, String seriesId) {
        String sql = "DELETE FROM " + SERIES_EPISODE_TABLE.getTableName() + " WHERE accountId=? AND categoryId=? AND seriesId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, safeCategoryId(categoryId));
            statement.setString(3, seriesId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    private void insert(Account account, String categoryId, String seriesId, Channel channel, long cachedAt) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(SERIES_EPISODE_TABLE))) {
            statement.setString(1, account.getDbId());
            statement.setString(2, safeCategoryId(categoryId));
            statement.setString(3, seriesId);
            statement.setString(4, channel.getChannelId());
            statement.setString(5, channel.getName());
            statement.setString(6, channel.getCmd());
            statement.setString(7, channel.getLogo());
            statement.setString(8, channel.getSeason());
            statement.setString(9, channel.getEpisodeNum());
            statement.setString(10, channel.getDescription());
            statement.setString(11, channel.getReleaseDate());
            statement.setString(12, channel.getRating());
            statement.setString(13, channel.getDuration());
            statement.setString(14, channel.getExtraJson());
            statement.setLong(15, cachedAt);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute insert query", e);
        }
    }

    @Override
    Channel populate(ResultSet resultSet) {
        Channel channel = new Channel();
        channel.setDbId(nullSafeString(resultSet, "id"));
        channel.setChannelId(nullSafeString(resultSet, "channelId"));
        channel.setName(nullSafeString(resultSet, "name"));
        channel.setCmd(nullSafeString(resultSet, "cmd"));
        channel.setLogo(nullSafeString(resultSet, "logo"));
        channel.setSeason(nullSafeString(resultSet, "season"));
        channel.setEpisodeNum(nullSafeString(resultSet, "episodeNum"));
        channel.setDescription(nullSafeString(resultSet, "description"));
        channel.setReleaseDate(nullSafeString(resultSet, "releaseDate"));
        channel.setRating(nullSafeString(resultSet, "rating"));
        channel.setDuration(nullSafeString(resultSet, "duration"));
        channel.setExtraJson(nullSafeString(resultSet, "extraJson"));
        return channel;
    }

    private String safeCategoryId(String categoryId) {
        return categoryId == null ? "" : categoryId.trim();
    }
}
