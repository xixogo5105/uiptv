-- Public guide inventory is independent of accounts and cached channel mappings.
CREATE TABLE IF NOT EXISTS epg_shared_source (
    source_id TEXT PRIMARY KEY,
    priority INTEGER NOT NULL DEFAULT 100,
    countries TEXT NOT NULL DEFAULT '',
    languages TEXT NOT NULL DEFAULT ''
);
