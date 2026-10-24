CREATE TABLE IF NOT EXISTS source_blacklist (
    source_id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT 0
);
