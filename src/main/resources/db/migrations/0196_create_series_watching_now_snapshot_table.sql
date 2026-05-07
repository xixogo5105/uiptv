CREATE TABLE IF NOT EXISTS SeriesWatchingNowSnapshot (
    id INTEGER PRIMARY KEY,
    accountId TEXT,
    categoryId TEXT,
    seriesId TEXT,
    categoryDbId TEXT,
    seriesTitle TEXT,
    seriesPoster TEXT,
    episodesJson TEXT,
    updatedAt INTEGER
);
