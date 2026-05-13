INSERT INTO BookmarkOrder (bookmark_db_id, category_id, display_order) SELECT id, categoryId, 0 FROM Bookmark WHERE id NOT IN (SELECT bookmark_db_id FROM BookmarkOrder);
