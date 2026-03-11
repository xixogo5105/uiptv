package com.uiptv.db;

import com.uiptv.model.VodWatchState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.VOD_WATCH_STATE_TABLE;
import static com.uiptv.db.SQLConnection.connect;

public class VodWatchStateDb extends BaseDb {
    private static final String DELETE_FROM = "DELETE FROM ";
    private static VodWatchStateDb instance;

    public VodWatchStateDb() {
        super(VOD_WATCH_STATE_TABLE);
    }

    public static synchronized VodWatchStateDb get() {
        if (instance == null) {
            instance = new VodWatchStateDb();
        }
        return instance;
    }

    public VodWatchState getByVod(String accountId, String categoryId, String vodId) {
        List<VodWatchState> rows = getAll(
                " WHERE accountId=? AND categoryId=? AND vodId=?",
                new String[]{accountId, categoryId, vodId}
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<VodWatchState> getByVod(String accountId, String vodId) {
        return getAll(" WHERE accountId=? AND vodId=?", new String[]{accountId, vodId});
    }

    public List<VodWatchState> getByAccount(String accountId) {
        return getAll(" WHERE accountId=?", new String[]{accountId});
    }

    public void deleteByAccount(String accountId) {
        String sql = DELETE_FROM + VOD_WATCH_STATE_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to delete VOD watch state by account", e);
        }
    }

    public void upsert(VodWatchState state) {
        String updateSql = "UPDATE " + VOD_WATCH_STATE_TABLE.getTableName()
                + " SET vodName=?, vodCmd=?, vodLogo=?, updatedAt=?"
                + " WHERE accountId=? AND categoryId=? AND vodId=?";
        String insertSql = "INSERT INTO " + VOD_WATCH_STATE_TABLE.getTableName()
                + " (accountId, categoryId, vodId, vodName, vodCmd, vodLogo, updatedAt)"
                + " VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = connect();
             PreparedStatement update = conn.prepareStatement(updateSql);
             PreparedStatement insert = conn.prepareStatement(insertSql)) {
            update.setString(1, state.getVodName());
            update.setString(2, state.getVodCmd());
            update.setString(3, state.getVodLogo());
            update.setLong(4, state.getUpdatedAt());
            update.setString(5, state.getAccountId());
            update.setString(6, state.getCategoryId());
            update.setString(7, state.getVodId());
            int updated = update.executeUpdate();
            if (updated == 0) {
                insert.setString(1, state.getAccountId());
                insert.setString(2, state.getCategoryId());
                insert.setString(3, state.getVodId());
                insert.setString(4, state.getVodName());
                insert.setString(5, state.getVodCmd());
                insert.setString(6, state.getVodLogo());
                insert.setLong(7, state.getUpdatedAt());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to save VOD watch state", e);
        }
    }

    public void clear(String accountId, String categoryId, String vodId) {
        String sql = DELETE_FROM + VOD_WATCH_STATE_TABLE.getTableName() + " WHERE accountId=? AND categoryId=? AND vodId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, categoryId);
            statement.setString(3, vodId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to clear VOD watch state", e);
        }
    }

    public void clearAll() {
        String sql = DELETE_FROM + VOD_WATCH_STATE_TABLE.getTableName();
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to clear all VOD watch state", e);
        }
    }

    @Override
    VodWatchState populate(ResultSet resultSet) {
        VodWatchState state = new VodWatchState();
        state.setDbId(nullSafeString(resultSet, "id"));
        state.setAccountId(nullSafeString(resultSet, "accountId"));
        state.setCategoryId(nullSafeString(resultSet, "categoryId"));
        state.setVodId(nullSafeString(resultSet, "vodId"));
        state.setVodName(nullSafeString(resultSet, "vodName"));
        state.setVodCmd(nullSafeString(resultSet, "vodCmd"));
        state.setVodLogo(nullSafeString(resultSet, "vodLogo"));
        try {
            state.setUpdatedAt(resultSet.getLong("updatedAt"));
        } catch (SQLException _) {
            state.setUpdatedAt(0L);
        }
        return state;
    }
}
