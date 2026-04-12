package com.uiptv.db;

import com.uiptv.model.PublishedM3uSelection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static com.uiptv.db.DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isBlank;

public class PublishedM3uSelectionDb extends BaseDb {
    private static PublishedM3uSelectionDb instance;

    private PublishedM3uSelectionDb() {
        super(PUBLISHED_M3U_SELECTION_TABLE);
    }

    public static synchronized PublishedM3uSelectionDb get() {
        if (instance == null) {
            instance = new PublishedM3uSelectionDb();
        }
        return instance;
    }

    @Override
    PublishedM3uSelection populate(ResultSet resultSet) {
        PublishedM3uSelection selection = new PublishedM3uSelection();
        selection.setDbId(nullSafeString(resultSet, "id"));
        selection.setAccountId(nullSafeString(resultSet, "accountId"));
        return selection;
    }

    public List<PublishedM3uSelection> getAllSelections() {
        return getAll(" ORDER BY id", new String[]{});
    }

    public void replaceSelections(Set<String> accountIds) {
        try (Connection conn = connect()) {
            replaceSelectionsInTransaction(conn, accountIds);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to replace published M3U selections", e);
        }
    }

    private void replaceSelectionsInTransaction(Connection conn, Set<String> accountIds) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + PUBLISHED_M3U_SELECTION_TABLE.getTableName())) {
                delete.executeUpdate();
            }

            if (accountIds != null && !accountIds.isEmpty()) {
                try (PreparedStatement insert = conn.prepareStatement(insertTableSql(PUBLISHED_M3U_SELECTION_TABLE))) {
                    for (String accountId : accountIds) {
                        if (isBlank(accountId)) {
                            continue;
                        }
                        insert.setString(1, accountId);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    public void deleteByAccountId(String accountId) {
        if (isBlank(accountId)) {
            return;
        }
        String sql = "DELETE FROM " + PUBLISHED_M3U_SELECTION_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to delete published M3U selection", e);
        }
    }
}
