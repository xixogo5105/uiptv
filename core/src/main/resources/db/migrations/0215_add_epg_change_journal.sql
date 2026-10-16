CREATE TABLE IF NOT EXISTS epg_change_state (
    id INTEGER PRIMARY KEY,
    revision INTEGER NOT NULL DEFAULT 0,
    database_generation TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS epg_change_history (
    revision INTEGER PRIMARY KEY,
    database_generation TEXT NOT NULL,
    kind TEXT NOT NULL,
    source_ids TEXT NOT NULL DEFAULT '',
    guide_ids TEXT NOT NULL DEFAULT '',
    account_provider_ids TEXT NOT NULL DEFAULT ''
);

INSERT OR IGNORE INTO epg_change_state(id, revision, database_generation)
VALUES (1, 0, lower(hex(randomblob(16))));
