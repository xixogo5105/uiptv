CREATE INDEX IF NOT EXISTS idx_category_account_id ON Category(accountId);
CREATE INDEX IF NOT EXISTS idx_channel_channel_id_category_id ON Channel(channelId, categoryId);
CREATE INDEX IF NOT EXISTS idx_bookmark_lookup ON Bookmark(accountName, categoryTitle, channelId, channelName);
