package com.uiptv.db;

import com.uiptv.model.PublishedM3uCategorySelection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings("java:S6548")
public class PublishedM3uCategorySelectionDb extends BaseDb {
    private static PublishedM3uCategorySelectionDb instance;

    private PublishedM3uCategorySelectionDb() {
        super(PUBLISHED_M3U_CATEGORY_SELECTION_TABLE);
    }

    public static synchronized PublishedM3uCategorySelectionDb get() {
        if (instance == null) {
            instance = new PublishedM3uCategorySelectionDb();
        }
        return instance;
    }

    @Override
    PublishedM3uCategorySelection populate(ResultSet resultSet) {
        PublishedM3uCategorySelection selection = new PublishedM3uCategorySelection();
        selection.setDbId(nullSafeString(resultSet, "id"));
        selection.setAccountId(nullSafeString(resultSet, "accountId"));
        selection.setCategoryName(nullSafeString(resultSet, "categoryName"));
        selection.setSelected(safeBoolean(resultSet, "selected"));
        return selection;
    }

    public List<PublishedM3uCategorySelection> getAllSelections() {
        return getAll(" ORDER BY id", new String[]{});
    }

    public void replaceSelections(Connection conn, List<PublishedM3uCategorySelection> selections) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.getTableName())) {
            delete.executeUpdate();
        }
        if (selections == null || selections.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = conn.prepareStatement(insertTableSql(PUBLISHED_M3U_CATEGORY_SELECTION_TABLE))) {
            for (PublishedM3uCategorySelection selection : selections) {
                if (selection == null || isBlank(selection.getAccountId()) || isBlank(selection.getCategoryName())) {
                    continue;
                }
                insert.setString(1, selection.getAccountId());
                insert.setString(2, selection.getCategoryName());
                insert.setString(3, selection.isSelected() ? "1" : "0");
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public void deleteByAccountId(String accountId) {
        if (isBlank(accountId)) {
            return;
        }
        String sql = "DELETE FROM " + PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to delete published M3U category selection", e);
        }
    }
}
