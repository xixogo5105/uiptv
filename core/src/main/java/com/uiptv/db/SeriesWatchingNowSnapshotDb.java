package com.uiptv.db;

import com.uiptv.model.SeriesWatchingNowSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_WATCHING_NOW_SNAPSHOT_TABLE;
import static com.uiptv.db.SQLConnection.connect;

public class SeriesWatchingNowSnapshotDb extends BaseDb {
    private static final String DELETE_FROM = "DELETE FROM ";
    private static SeriesWatchingNowSnapshotDb instance;

    public SeriesWatchingNowSnapshotDb() {
        super(SERIES_WATCHING_NOW_SNAPSHOT_TABLE);
    }

    public static synchronized SeriesWatchingNowSnapshotDb get() {
        if (instance == null) {
            instance = new SeriesWatchingNowSnapshotDb();
        }
        return instance;
    }

    public SeriesWatchingNowSnapshot getBySeries(String accountId, String categoryId, String seriesId) {
        List<SeriesWatchingNowSnapshot> rows = getAll(
                " WHERE accountId=? AND categoryId=? AND seriesId=?",
                new String[]{accountId, categoryId, seriesId}
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<SeriesWatchingNowSnapshot> getBySeries(String accountId, String seriesId) {
        return getAll(" WHERE accountId=? AND seriesId=?", new String[]{accountId, seriesId});
    }

    public List<SeriesWatchingNowSnapshot> getByAccount(String accountId) {
        return getAll(" WHERE accountId=?", new String[]{accountId});
    }

    public void upsert(SeriesWatchingNowSnapshot snapshot) {
        String updateSql = "UPDATE " + SERIES_WATCHING_NOW_SNAPSHOT_TABLE.getTableName()
                + " SET categoryDbId=?, seriesTitle=?, seriesPoster=?, episodesJson=?, updatedAt=?"
                + " WHERE accountId=? AND categoryId=? AND seriesId=?";
        String insertSql = "INSERT INTO " + SERIES_WATCHING_NOW_SNAPSHOT_TABLE.getTableName()
                + " (accountId, categoryId, seriesId, categoryDbId, seriesTitle, seriesPoster, episodesJson, updatedAt)"
                + " VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = connect();
             PreparedStatement update = conn.prepareStatement(updateSql);
             PreparedStatement insert = conn.prepareStatement(insertSql)) {
            update.setString(1, snapshot.getCategoryDbId());
            update.setString(2, snapshot.getSeriesTitle());
            update.setString(3, snapshot.getSeriesPoster());
            update.setString(4, snapshot.getEpisodesJson());
            update.setLong(5, snapshot.getUpdatedAt());
            update.setString(6, snapshot.getAccountId());
            update.setString(7, snapshot.getCategoryId());
            update.setString(8, snapshot.getSeriesId());
            int updated = update.executeUpdate();
            if (updated == 0) {
                insert.setString(1, snapshot.getAccountId());
                insert.setString(2, snapshot.getCategoryId());
                insert.setString(3, snapshot.getSeriesId());
                insert.setString(4, snapshot.getCategoryDbId());
                insert.setString(5, snapshot.getSeriesTitle());
                insert.setString(6, snapshot.getSeriesPoster());
                insert.setString(7, snapshot.getEpisodesJson());
                insert.setLong(8, snapshot.getUpdatedAt());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to save series watching-now snapshot", e);
        }
    }

    public void clear(String accountId, String categoryId, String seriesId) {
        String sql = DELETE_FROM + SERIES_WATCHING_NOW_SNAPSHOT_TABLE.getTableName() + " WHERE accountId=? AND categoryId=? AND seriesId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, categoryId);
            statement.setString(3, seriesId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to clear series watching-now snapshot", e);
        }
    }

    public void clearAll() {
        String sql = DELETE_FROM + SERIES_WATCHING_NOW_SNAPSHOT_TABLE.getTableName();
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to clear all series watching-now snapshots", e);
        }
    }

    public void deleteByAccount(String accountId) {
        String sql = DELETE_FROM + SERIES_WATCHING_NOW_SNAPSHOT_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to delete series watching-now snapshots by account", e);
        }
    }

    @Override
    SeriesWatchingNowSnapshot populate(ResultSet resultSet) {
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setDbId(nullSafeString(resultSet, "id"));
        snapshot.setAccountId(nullSafeString(resultSet, "accountId"));
        snapshot.setCategoryId(nullSafeString(resultSet, "categoryId"));
        snapshot.setSeriesId(nullSafeString(resultSet, "seriesId"));
        snapshot.setCategoryDbId(nullSafeString(resultSet, "categoryDbId"));
        snapshot.setSeriesTitle(nullSafeString(resultSet, "seriesTitle"));
        snapshot.setSeriesPoster(nullSafeString(resultSet, "seriesPoster"));
        snapshot.setEpisodesJson(nullSafeString(resultSet, "episodesJson"));
        try {
            snapshot.setUpdatedAt(resultSet.getLong("updatedAt"));
        } catch (SQLException _) {
            snapshot.setUpdatedAt(0L);
        }
        return snapshot;
    }
}
