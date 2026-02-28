package com.uiptv.db;

import com.uiptv.model.SeriesWatchState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_WATCH_STATE_TABLE;
import static com.uiptv.db.SQLConnection.connect;

public class SeriesWatchStateDb extends BaseDb {
    private static SeriesWatchStateDb instance;

    public SeriesWatchStateDb() {
        super(SERIES_WATCH_STATE_TABLE);
    }

    public static synchronized SeriesWatchStateDb get() {
        if (instance == null) {
            instance = new SeriesWatchStateDb();
        }
        return instance;
    }

    public SeriesWatchState getBySeries(String accountId, String categoryId, String seriesId) {
        List<SeriesWatchState> rows = getAll(" WHERE accountId=? AND mode=? AND categoryId=? AND seriesId=?",
                new String[]{accountId, "series", categoryId, seriesId});
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<SeriesWatchState> getBySeries(String accountId, String seriesId) {
        return getAll(" WHERE accountId=? AND mode=? AND seriesId=?",
                new String[]{accountId, "series", seriesId});
    }

    public List<SeriesWatchState> getByAccount(String accountId, String categoryId) {
        return getAll(" WHERE accountId=? AND mode=? AND categoryId=?", new String[]{accountId, "series", categoryId});
    }

    public List<SeriesWatchState> getByAccount(String accountId) {
        return getAll(" WHERE accountId=? AND mode=?", new String[]{accountId, "series"});
    }

    public void upsert(SeriesWatchState state) {
        String updateSql = "UPDATE " + SERIES_WATCH_STATE_TABLE.getTableName()
                + " SET episodeId=?, episodeName=?, season=?, episodeNum=?, updatedAt=?, source=?,"
                + " seriesCategorySnapshot=?, seriesChannelSnapshot=?, seriesEpisodeSnapshot=?"
                + " WHERE accountId=? AND mode=? AND categoryId=? AND seriesId=?";
        String insertSql = "INSERT INTO " + SERIES_WATCH_STATE_TABLE.getTableName()
                + " (accountId, mode, categoryId, seriesId, episodeId, episodeName, season, episodeNum, updatedAt, source,"
                + " seriesCategorySnapshot, seriesChannelSnapshot, seriesEpisodeSnapshot)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = connect();
             PreparedStatement update = conn.prepareStatement(updateSql);
             PreparedStatement insert = conn.prepareStatement(insertSql)) {
            update.setString(1, state.getEpisodeId());
            update.setString(2, state.getEpisodeName());
            update.setString(3, state.getSeason());
            update.setInt(4, state.getEpisodeNum());
            update.setLong(5, state.getUpdatedAt());
            update.setString(6, state.getSource());
            update.setString(7, state.getSeriesCategorySnapshot());
            update.setString(8, state.getSeriesChannelSnapshot());
            update.setString(9, state.getSeriesEpisodeSnapshot());
            update.setString(10, state.getAccountId());
            update.setString(11, state.getMode());
            update.setString(12, state.getCategoryId());
            update.setString(13, state.getSeriesId());
            int updated = update.executeUpdate();
            if (updated == 0) {
                insert.setString(1, state.getAccountId());
                insert.setString(2, state.getMode());
                insert.setString(3, state.getCategoryId());
                insert.setString(4, state.getSeriesId());
                insert.setString(5, state.getEpisodeId());
                insert.setString(6, state.getEpisodeName());
                insert.setString(7, state.getSeason());
                insert.setInt(8, state.getEpisodeNum());
                insert.setLong(9, state.getUpdatedAt());
                insert.setString(10, state.getSource());
                insert.setString(11, state.getSeriesCategorySnapshot());
                insert.setString(12, state.getSeriesChannelSnapshot());
                insert.setString(13, state.getSeriesEpisodeSnapshot());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to save series watch state", e);
        }
    }

    public void clear(String accountId, String categoryId, String seriesId) {
        String sql = "DELETE FROM " + SERIES_WATCH_STATE_TABLE.getTableName() + " WHERE accountId=? AND mode=? AND categoryId=? AND seriesId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, "series");
            statement.setString(3, categoryId);
            statement.setString(4, seriesId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to clear series watch state", e);
        }
    }

    public void deleteByAccount(String accountId) {
        String sql = "DELETE FROM " + SERIES_WATCH_STATE_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to delete watch state by account", e);
        }
    }

    public void clearAllSeries() {
        String sql = "DELETE FROM " + SERIES_WATCH_STATE_TABLE.getTableName() + " WHERE mode=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, "series");
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to clear all series watch state", e);
        }
    }

    @Override
    SeriesWatchState populate(ResultSet resultSet) {
        SeriesWatchState state = new SeriesWatchState();
        state.setDbId(nullSafeString(resultSet, "id"));
        state.setAccountId(nullSafeString(resultSet, "accountId"));
        state.setMode(nullSafeString(resultSet, "mode"));
        state.setCategoryId(nullSafeString(resultSet, "categoryId"));
        state.setSeriesId(nullSafeString(resultSet, "seriesId"));
        state.setEpisodeId(nullSafeString(resultSet, "episodeId"));
        state.setEpisodeName(nullSafeString(resultSet, "episodeName"));
        state.setSeason(nullSafeString(resultSet, "season"));
        state.setEpisodeNum(safeInteger(resultSet, "episodeNum"));
        try {
            state.setUpdatedAt(resultSet.getLong("updatedAt"));
        } catch (SQLException ignored) {
            state.setUpdatedAt(0L);
        }
        state.setSource(nullSafeString(resultSet, "source"));
        state.setSeriesCategorySnapshot(nullSafeString(resultSet, "seriesCategorySnapshot"));
        state.setSeriesChannelSnapshot(nullSafeString(resultSet, "seriesChannelSnapshot"));
        state.setSeriesEpisodeSnapshot(nullSafeString(resultSet, "seriesEpisodeSnapshot"));
        return state;
    }
}
