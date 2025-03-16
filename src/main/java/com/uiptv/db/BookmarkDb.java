package com.uiptv.db;

            import com.uiptv.model.Bookmark;
            import com.uiptv.model.BookmarkCategory;

            import java.sql.Connection;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.util.ArrayList;
            import java.util.List;

            import static com.uiptv.db.DatabaseUtils.DbTable.BOOKMARK_TABLE;
            import static com.uiptv.db.DatabaseUtils.DbTable.BOOKMARK_CATEGORY_TABLE;
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

                public List<Bookmark> getBookmarksByCategory(String categoryId) {
                    return getAll(" where categoryId=?", new String[]{categoryId});
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
                        statement.setString(7, bookmark.getCategoryId()); // New field
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
                    bookmark.setCategoryId(nullSafeString(resultSet, "categoryId")); // New field
                    return bookmark;
                }

                public void saveCategory(BookmarkCategory category) {
                    try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(BOOKMARK_CATEGORY_TABLE))) {
                        statement.setString(1, category.getName());
                        statement.execute();
                    } catch (SQLException e) {
                        throw new RuntimeException("Unable to execute query");
                    }
                }

                public void deleteCategory(BookmarkCategory category) {
                    String sql = "DELETE FROM " + BOOKMARK_CATEGORY_TABLE.getTableName() + " where id=?";
                    try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql)) {
                        statement.setString(1, category.getId());
                        statement.execute();
                    } catch (SQLException e) {
                        throw new RuntimeException("Unable to execute delete query");
                    }
                }
                public List<BookmarkCategory> getAllCategories() {
                    List<BookmarkCategory> categories = new ArrayList<>();
                    String sql = "SELECT * FROM " + BOOKMARK_CATEGORY_TABLE.getTableName();
                    try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            BookmarkCategory category = new BookmarkCategory(
                                    resultSet.getString("id"),
                                    resultSet.getString("name")
                            );
                            categories.add(category);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException("Unable to execute query", e);
                    }
                    return categories;
                }
            }