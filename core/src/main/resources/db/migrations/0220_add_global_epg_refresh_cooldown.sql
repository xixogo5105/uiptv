--@add_column Configuration globalEpgRefreshIntervalHours TEXT DEFAULT '6'
--@add_column epg_source last_attempt_at INTEGER NOT NULL DEFAULT 0
UPDATE epg_source
SET last_attempt_at = CASE
    WHEN warning <> '' AND retry_at > refreshed_at THEN MAX(refreshed_at, retry_at - 300000)
    ELSE refreshed_at
END
WHERE last_attempt_at = 0;
