CREATE TABLE IF NOT EXISTS epg_source (
    source_id TEXT PRIMARY KEY,
    refreshed_at INTEGER NOT NULL,
    retry_at INTEGER NOT NULL,
    warning TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS epg_programme (
    source_id TEXT NOT NULL,
    guide_channel_id TEXT NOT NULL,
    starts_at INTEGER NOT NULL,
    stops_at INTEGER NOT NULL CHECK (stops_at > starts_at),
    title TEXT NOT NULL,
    description BLOB NOT NULL,
    PRIMARY KEY (source_id, guide_channel_id, starts_at)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS epg_programme_expiry ON epg_programme(stops_at);
CREATE TABLE IF NOT EXISTS epg_alias (
    source_id TEXT NOT NULL,
    kind INTEGER NOT NULL,
    name TEXT NOT NULL,
    guide_channel_id TEXT NOT NULL,
    PRIMARY KEY (source_id, kind, name)
) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS epg_channel_mapping (
    account_id TEXT NOT NULL,
    provider_channel_id TEXT NOT NULL,
    signature TEXT NOT NULL,
    source_id TEXT NOT NULL,
    guide_channel_id TEXT NOT NULL,
    priority INTEGER NOT NULL,
    PRIMARY KEY (account_id, provider_channel_id, source_id)
) WITHOUT ROWID;

CREATE TABLE IF NOT EXISTS epg_channel_icon (
    source_id TEXT NOT NULL,
    guide_channel_id TEXT NOT NULL,
    icon_url TEXT NOT NULL,
    PRIMARY KEY (source_id, guide_channel_id)
) WITHOUT ROWID;
