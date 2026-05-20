package com.uiptv.service;

import com.uiptv.db.DatabaseAccessException;
import com.uiptv.db.SQLConnection;
import com.uiptv.model.Account;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.uiptv.db.DatabaseUtils.DbTable.CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.SERIES_EPISODE_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.VOD_CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.DbTable.VOD_CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.validatedTableName;

public class CategoryCacheRemovalService {
    private static final String ALL_CATEGORY_SENTINEL = "all";
    private static final String DELETE_FROM = "DELETE FROM ";
    private static final String WHERE_ACCOUNT_CATEGORY_IN = " WHERE accountId=? AND categoryId IN (";
    private static final String WHERE_ACCOUNT_TYPE_ID_IN = " WHERE accountId=? AND accountType=? AND id IN (";

    public CategoryCacheRemovalService() {
    }

    public static CategoryCacheRemovalService getInstance() {
        return new CategoryCacheRemovalService();
    }

    public CategoryCacheRemovalResult removeCachedCategories(Account account, Collection<String> categoryDbIds) {
        if (account == null || account.getDbId() == null || account.getDbId().isBlank()) {
            return new CategoryCacheRemovalResult(0, 0, 0, Account.AccountAction.itv);
        }

        Account.AccountAction mode = account.getAction() == null ? Account.AccountAction.itv : account.getAction();
        List<String> requestedIds = normalizeCategoryDbIds(categoryDbIds);
        if (requestedIds.isEmpty()) {
            return new CategoryCacheRemovalResult(0, 0, 0, mode);
        }

        try (Connection conn = SQLConnection.connect()) {
            return removeCachedCategoriesInTransaction(conn, account, mode, requestedIds);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to remove cached categories", e);
        }
    }

    private CategoryCacheRemovalResult removeCachedCategoriesInTransaction(
            Connection conn,
            Account account,
            Account.AccountAction mode,
            List<String> requestedIds
    ) throws SQLException {
        conn.setAutoCommit(false);
        try {
            CategorySelection selection = loadSelectedCategories(conn, account, mode, requestedIds);
            if (selection.rows().isEmpty()) {
                conn.commit();
                return new CategoryCacheRemovalResult(requestedIds.size(), 0, 0, mode);
            }

            int removedItems = switch (mode) {
                case vod -> removeVodCache(conn, account, selection);
                case series -> removeSeriesCache(conn, account, selection);
                case itv -> removeLiveCache(conn, selection);
            };
            int removedCategories = removeCategoryRows(conn, account, mode, selection.rowIds());
            conn.commit();
            return new CategoryCacheRemovalResult(requestedIds.size(), removedCategories, removedItems, mode);
        } catch (SQLException e) {
            conn.rollback();
            throw new DatabaseAccessException("Unable to remove cached categories", e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private List<String> normalizeCategoryDbIds(Collection<String> categoryDbIds) {
        if (categoryDbIds == null || categoryDbIds.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String categoryDbId : categoryDbIds) {
            if (categoryDbId == null) {
                continue;
            }
            String normalized = categoryDbId.trim();
            if (!normalized.isEmpty() && !ALL_CATEGORY_SENTINEL.equalsIgnoreCase(normalized)) {
                ids.add(normalized);
            }
        }
        return List.copyOf(ids);
    }

    private CategorySelection loadSelectedCategories(Connection conn, Account account, Account.AccountAction mode, List<String> rowIds) throws SQLException {
        String table = categoryTable(mode);
        String sql = "SELECT id, categoryId FROM " + table
                + WHERE_ACCOUNT_TYPE_ID_IN + placeholders(rowIds.size()) + ")";
        List<CategoryRow> rows = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, mode.name());
            bindValues(statement, 3, rowIds);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CategoryRow(rs.getString("id"), rs.getString("categoryId")));
                }
            }
        }
        return new CategorySelection(rows);
    }

    private int removeLiveCache(Connection conn, CategorySelection selection) throws SQLException {
        String sql = DELETE_FROM + validatedTableName(CHANNEL_TABLE)
                + " WHERE categoryId IN (" + placeholders(selection.rowIds().size()) + ")";
        return executeUpdate(conn, sql, selection.rowIds());
    }

    private int removeVodCache(Connection conn, Account account, CategorySelection selection) throws SQLException {
        String sql = DELETE_FROM + validatedTableName(VOD_CHANNEL_TABLE)
                + WHERE_ACCOUNT_CATEGORY_IN + placeholders(selection.providerIds().size()) + ")";
        return executeUpdate(conn, sql, prepend(account.getDbId(), selection.providerIds()));
    }

    private int removeSeriesCache(Connection conn, Account account, CategorySelection selection) throws SQLException {
        List<String> params = prepend(account.getDbId(), selection.providerIds());
        String channelSql = DELETE_FROM + validatedTableName(SERIES_CHANNEL_TABLE)
                + WHERE_ACCOUNT_CATEGORY_IN + placeholders(selection.providerIds().size()) + ")";
        String episodeSql = DELETE_FROM + validatedTableName(SERIES_EPISODE_TABLE)
                + WHERE_ACCOUNT_CATEGORY_IN + placeholders(selection.providerIds().size()) + ")";
        return executeUpdate(conn, channelSql, params) + executeUpdate(conn, episodeSql, params);
    }

    private int removeCategoryRows(Connection conn, Account account, Account.AccountAction mode, List<String> rowIds) throws SQLException {
        String sql = DELETE_FROM + categoryTable(mode)
                + WHERE_ACCOUNT_TYPE_ID_IN + placeholders(rowIds.size()) + ")";
        List<String> params = new ArrayList<>(rowIds.size() + 2);
        params.add(account.getDbId());
        params.add(mode.name());
        params.addAll(rowIds);
        return executeUpdate(conn, sql, params);
    }

    private int executeUpdate(Connection conn, String sql, List<String> params) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindValues(statement, 1, params);
            return statement.executeUpdate();
        }
    }

    private void bindValues(PreparedStatement statement, int startIndex, List<String> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setString(startIndex + i, values.get(i));
        }
    }

    private List<String> prepend(String first, List<String> rest) {
        List<String> params = new ArrayList<>(rest.size() + 1);
        params.add(first);
        params.addAll(rest);
        return params;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private String categoryTable(Account.AccountAction mode) {
        return switch (mode) {
            case vod -> validatedTableName(VOD_CATEGORY_TABLE);
            case series -> validatedTableName(SERIES_CATEGORY_TABLE);
            case itv -> validatedTableName(CATEGORY_TABLE);
        };
    }

    private record CategoryRow(String rowId, String providerId) {
    }

    private record CategorySelection(List<CategoryRow> rows) {
        private List<String> rowIds() {
            return rows.stream()
                    .map(CategoryRow::rowId)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
        }

        private List<String> providerIds() {
            return rows.stream()
                    .map(CategoryRow::providerId)
                    .map(value -> value == null ? "" : value.trim())
                    .distinct()
                    .toList();
        }
    }
}
