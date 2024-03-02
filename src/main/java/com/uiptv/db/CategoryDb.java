package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.CATEGORY_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class CategoryDb extends BaseDb {
    private static CategoryDb instance;


    public static synchronized CategoryDb get() {
        if (instance == null) {
            instance = new CategoryDb();
        }
        return instance;
    }

    public CategoryDb() {
        super(CATEGORY_TABLE);
    }

    public List<Category> getCategories(Account account) {
        return getAll(" WHERE accountType=? AND accountId=?", new String[]{account.getAction().name(), account.getDbId()});
    }

    public List<Category> getAllAccountCategories(String accountId) {
        return getAll(" WHERE accountId=?", new String[]{accountId});
    }

    public Category getCategoryById(String id, Account account) {
        return getById(id, " AND accountType='" + account.getAction().name() + "' AND accountId='" + account.getDbId() + "'");
    }

    public void saveAll(List<Category> categories, Account account) {
        deleteByAccount(account);
        categories.forEach(c -> insert(c, account));
    }

    public void deleteByAccount(Account account) {
        String sql = "DELETE FROM " + CATEGORY_TABLE.getTableName() + " WHERE accountId=? AND accountType=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, account.getDbId());
            statement.setString(2, account.getAction().name());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete all query");
        }
    }

    public void insert(Category category, Account account) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(CATEGORY_TABLE))) {
            statement.setString(1, category.getCategoryId());
            statement.setString(2, account.getDbId());
            statement.setString(3, account.getAction().name());
            statement.setString(4, category.getTitle());
            statement.setString(5, category.getAlias());
            statement.setInt(6, category.isActiveSub() ? 1 : 0);
            statement.setInt(7, category.getCensored());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
    }

    @Override
    Category populate(ResultSet resultSet) {
        Category c = new Category(nullSafeString(resultSet, "categoryId"), nullSafeString(resultSet, "title"), nullSafeString(resultSet, "alias"), "1".equals(nullSafeString(resultSet, "activeSub")), safeInteger(resultSet, "censored"));
        c.setDbId(nullSafeString(resultSet, "id"));
        return c;
    }
}
