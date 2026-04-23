CREATE UNIQUE INDEX IF NOT EXISTS idx_vod_watch_unique
ON VodWatchState (accountId, categoryId, vodId);
