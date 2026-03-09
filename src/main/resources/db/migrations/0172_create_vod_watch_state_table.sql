CREATE TABLE IF NOT EXISTS VodWatchState (
    id INTEGER PRIMARY KEY,
    accountId TEXT,
    categoryId TEXT,
    vodId TEXT,
    vodName TEXT,
    vodCmd TEXT,
    vodLogo TEXT,
    updatedAt INTEGER
);
