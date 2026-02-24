package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.VOD_CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class VodCategoryDb extends BaseDb {
    private static VodCategoryDb instance;

    public VodCategoryDb() {
        super(VOD_CATEGORY_TABLE);
    }

    public static synchronized VodCategoryDb get() {
        if (instance == null) {
            instance = new VodCategoryDb();
        }
        return instance;
    }

    public List<Category> getCategories(Account account) {
        return getAll(" WHERE accountType=? AND accountId=?", new String[]{account.getAction().name(), account.getDbId()});
    }

    public boolean isFresh(Account account, long maxAgeMs) {
        String sql = "SELECT MAX(cachedAt) FROM " + VOD_CATEGORY_TABLE.getTableName() + " WHERE accountId=? AND accountType=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, account.getAction().name());
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

    public void saveAll(List<Category> categories, Account account) {
        deleteByAccount(account.getDbId());
        long cachedAt = System.currentTimeMillis();
        categories.forEach(c -> insert(c, account, cachedAt));
    }

    public void deleteByAccount(String accountId) {
        String sql = "DELETE FROM " + VOD_CATEGORY_TABLE.getTableName() + " WHERE accountId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query", e);
        }
    }

    private void insert(Category category, Account account, long cachedAt) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(VOD_CATEGORY_TABLE))) {
            statement.setString(1, category.getCategoryId());
            statement.setString(2, account.getDbId());
            statement.setString(3, account.getAction().name());
            statement.setString(4, category.getTitle());
            statement.setString(5, category.getAlias());
            statement.setString(6, null);
            statement.setInt(7, category.isActiveSub() ? 1 : 0);
            statement.setInt(8, category.getCensored());
            statement.setString(9, category.getExtraJson());
            statement.setLong(10, cachedAt);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute insert query", e);
        }
    }

    @Override
    Category populate(ResultSet resultSet) {
        Category c = new Category(nullSafeString(resultSet, "categoryId"), nullSafeString(resultSet, "title"), nullSafeString(resultSet, "alias"), "1".equals(nullSafeString(resultSet, "activeSub")), safeInteger(resultSet, "censored"));
        c.setDbId(nullSafeString(resultSet, "id"));
        c.setAccountId(nullSafeString(resultSet, "accountId"));
        c.setAccountType(nullSafeString(resultSet, "accountType"));
        c.setExtraJson(nullSafeString(resultSet, "extraJson"));
        return c;
    }
}
