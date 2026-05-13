PRAGMA foreign_keys=off;

CREATE TABLE IF NOT EXISTS Configuration_new (
    id INTEGER PRIMARY KEY,
    playerPath1 TEXT,
    playerPath2 TEXT,
    playerPath3 TEXT,
    defaultPlayerPath TEXT,
    filterCategoriesList TEXT,
    filterChannelsList TEXT,
    pauseFiltering TEXT,
    darkTheme TEXT,
    serverPort TEXT,
    embeddedPlayer TEXT,
    enableFfmpegTranscoding TEXT,
    cacheExpiryDays TEXT,
    enableThumbnails TEXT,
    wideView TEXT
);

INSERT INTO Configuration_new (
    id,
    playerPath1,
    playerPath2,
    playerPath3,
    defaultPlayerPath,
    filterCategoriesList,
    filterChannelsList,
    pauseFiltering,
    darkTheme,
    serverPort,
    embeddedPlayer,
    enableFfmpegTranscoding,
    cacheExpiryDays,
    enableThumbnails,
    wideView
)
SELECT
    id,
    playerPath1,
    playerPath2,
    playerPath3,
    defaultPlayerPath,
    filterCategoriesList,
    filterChannelsList,
    pauseFiltering,
    darkTheme,
    serverPort,
    embeddedPlayer,
    enableFfmpegTranscoding,
    cacheExpiryDays,
    enableThumbnails,
    wideView
FROM Configuration;

DROP TABLE Configuration;
ALTER TABLE Configuration_new RENAME TO Configuration;

PRAGMA foreign_keys=on;
