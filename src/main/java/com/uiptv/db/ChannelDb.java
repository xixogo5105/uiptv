package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.CHANNEL_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class ChannelDb extends BaseDb {
    private static ChannelDb instance;


    public static synchronized ChannelDb get() {
        if (instance == null) {
            instance = new ChannelDb();
        }
        return instance;
    }

    public ChannelDb() {
        super(CHANNEL_TABLE);
    }

    public List<Channel> getChannels(String dbId) {
        return getAll(" WHERE categoryId=?", new String[]{dbId});
    }

    public Channel getChannelById(String dbId, String categoryId) {
        List<Channel> channels = getAll(" WHERE id=? AND categoryId=?", new String[]{dbId, categoryId});
        return (channels != null && !channels.isEmpty()) ? channels.get(0) : null;
    }

    public void saveAll(List<Channel> channels, String dbCategoryId, Account account) {
        Category category = new CategoryDb().getCategoryById(dbCategoryId, account);
        deleteAll(category.getDbId());
        channels.forEach(c -> insert(c, category));
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
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
    }

    public void deleteByAccount(String accountId) {
        List<Category> categories = CategoryDb.get().getAllAccountCategories(accountId);
        if (categories != null) categories.forEach(category -> {
            deleteAll(category.getDbId());
        });
    }

    private static void deleteAll(String categoryId) {
        String sql = "DELETE FROM " + CHANNEL_TABLE.getTableName() + " WHERE categoryId=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, categoryId);
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete all query");
        }
    }

    @Override
    Channel populate(ResultSet resultSet) {
        Channel c = new Channel(nullSafeString(resultSet, "channelId"), nullSafeString(resultSet, "name"), nullSafeString(resultSet, "number"), nullSafeString(resultSet, "cmd"), nullSafeString(resultSet, "cmd_1"), nullSafeString(resultSet, "cmd_2"), nullSafeString(resultSet, "cmd_3"), nullSafeString(resultSet, "logo"), safeInteger(resultSet, "censored"), safeInteger(resultSet, "status"), safeInteger(resultSet, "hd"));
        c.setDbId(nullSafeString(resultSet, "id"));
        c.setCategoryId(nullSafeString(resultSet, "categoryId"));
        return c;
    }

}
