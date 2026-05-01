CREATE TABLE IF NOT EXISTS PublishedM3uCategorySelection
(
    id INTEGER PRIMARY KEY,
    accountId TEXT NOT NULL,
    categoryName TEXT NOT NULL,
    selected TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS PublishedM3uChannelSelection
(
    id INTEGER PRIMARY KEY,
    accountId TEXT NOT NULL,
    categoryName TEXT NOT NULL,
    channelId TEXT NOT NULL,
    selected TEXT NOT NULL
);
