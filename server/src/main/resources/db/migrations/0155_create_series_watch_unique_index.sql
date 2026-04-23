CREATE UNIQUE INDEX IF NOT EXISTS idx_series_watch_unique ON SeriesWatchState (accountId, mode, categoryId, seriesId);
