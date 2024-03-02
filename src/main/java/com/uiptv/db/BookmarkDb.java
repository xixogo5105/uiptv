package com.uiptv.db;

import com.uiptv.model.Bookmark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.BOOKMARK_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;
import static com.uiptv.util.StringUtils.isNotBlank;

public class BookmarkDb extends BaseDb {
    private static BookmarkDb instance;


    public static synchronized BookmarkDb get() {
        if (instance == null) {
            instance = new BookmarkDb();
        }
        return instance;
    }

    public BookmarkDb() {
        super(BOOKMARK_TABLE);
    }

    public List<Bookmark> getBookmarks() {
        return getAll();
    }

    public Bookmark getBookmarkById(Bookmark b) {
        List<Bookmark> bookmarks = getAll(" where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?", new String[]{b.getAccountName(), b.getCategoryTitle(), b.getChannelId(), b.getChannelName()});
        return (bookmarks != null && !bookmarks.isEmpty()) ? bookmarks.get(0) : null;
    }

    public void save(Bookmark bookmark) {
        delete(bookmark);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(BOOKMARK_TABLE))) {
            statement.setString(1, bookmark.getAccountName());
            statement.setString(2, bookmark.getCategoryTitle());
            statement.setString(3, bookmark.getChannelId());
            statement.setString(4, bookmark.getChannelName());
            statement.setString(5, bookmark.getCmd());
            statement.setString(6, bookmark.getServerPortalUrl());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
    }

    public void delete(Bookmark bookmark) {
        if (isNotBlank(bookmark.getDbId())) {
            delete(bookmark.getDbId());
        }
        deleteBookmark(bookmark);

    }

    private void deleteBookmark(Bookmark b) {
        //accountName,categoryTitle, channelId, channelName);
        String sql = "DELETE FROM " + BOOKMARK_TABLE.getTableName() + " where accountName=? AND categoryTitle=? AND channelId=? AND channelName=?";
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, b.getAccountName());
            statement.setString(2, b.getCategoryTitle());
            statement.setString(3, b.getChannelId());
            statement.setString(4, b.getChannelName());
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute delete query");
        }
    }

    @Override
    Bookmark populate(ResultSet resultSet) {
        Bookmark bookmark = new Bookmark(
                nullSafeString(resultSet, "accountName"),
                nullSafeString(resultSet, "categoryTitle"),
                nullSafeString(resultSet, "channelId"),
                nullSafeString(resultSet, "channelName"),
                nullSafeString(resultSet, "cmd"),
                nullSafeString(resultSet, "serverPortalUrl"));
        bookmark.setDbId(nullSafeString(resultSet, "id"));
        return bookmark;
    }
}
