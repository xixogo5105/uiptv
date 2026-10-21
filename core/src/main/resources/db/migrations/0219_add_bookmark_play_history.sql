CREATE TABLE IF NOT EXISTS BookmarkPlayHistory (
    id INTEGER PRIMARY KEY,
    accountName TEXT NOT NULL,
    accountAction TEXT NOT NULL,
    channelId TEXT NOT NULL,
    playCount INTEGER NOT NULL DEFAULT 0,
    lastPlayedAt INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bookmark_play_history_identity
    ON BookmarkPlayHistory(accountName, accountAction, channelId);
CREATE INDEX IF NOT EXISTS idx_bookmark_play_history_rank
    ON BookmarkPlayHistory(lastPlayedAt DESC, playCount DESC);
