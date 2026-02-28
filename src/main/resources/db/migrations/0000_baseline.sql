CREATE TABLE IF NOT EXISTS Configuration (
    id INTEGER PRIMARY KEY,
    playerPath1 TEXT,
    playerPath2 TEXT,
    playerPath3 TEXT,
    defaultPlayerPath TEXT,
    filterCategoriesList TEXT,
    filterChannelsList TEXT,
    pauseFiltering TEXT,
    fontFamily TEXT,
    fontSize TEXT,
    fontWeight TEXT,
    darkTheme TEXT,
    serverPort TEXT,
    embeddedPlayer TEXT,
    enableFfmpegTranscoding TEXT,
    cacheExpiryDays TEXT
);

CREATE TABLE IF NOT EXISTS Account (
    id INTEGER PRIMARY KEY,
    accountName TEXT NOT NULL UNIQUE,
    username TEXT,
    password TEXT,
    url TEXT,
    macAddress TEXT,
    macAddressList TEXT,
    serialNumber TEXT,
    deviceId1 TEXT,
    deviceId2 TEXT,
    signature TEXT,
    epg TEXT,
    m3u8Path TEXT,
    type TEXT,
    serverPortalUrl TEXT,
    pinToTop TEXT,
    httpMethod TEXT,
    timezone TEXT
);

CREATE TABLE IF NOT EXISTS Bookmark (
    id INTEGER PRIMARY KEY,
    accountName TEXT,
    categoryTitle TEXT,
    channelId TEXT,
    channelName TEXT,
    cmd TEXT,
    serverPortalUrl TEXT,
    categoryId TEXT,
    accountAction TEXT,
    drmType TEXT,
    drmLicenseUrl TEXT,
    clearKeysJson TEXT,
    inputstreamaddon TEXT,
    manifestType TEXT,
    categoryJson TEXT,
    channelJson TEXT,
    vodJson TEXT,
    seriesJson TEXT
);

CREATE TABLE IF NOT EXISTS Category (
    id INTEGER PRIMARY KEY,
    categoryId TEXT NOT NULL,
    accountId TEXT,
    accountType TEXT,
    title TEXT,
    alias TEXT,
    url TEXT,
    activeSub INTEGER,
    censored INTEGER
);

CREATE TABLE IF NOT EXISTS Channel (
    id INTEGER PRIMARY KEY,
    channelId TEXT NOT NULL,
    categoryId TEXT,
    name TEXT,
    number TEXT,
    cmd TEXT,
    cmd_1 TEXT,
    cmd_2 TEXT,
    cmd_3 TEXT,
    logo TEXT,
    censored INTEGER,
    status INTEGER,
    hd INTEGER,
    drmType TEXT,
    drmLicenseUrl TEXT,
    clearKeysJson TEXT,
    inputstreamaddon TEXT,
    manifestType TEXT
);

CREATE TABLE IF NOT EXISTS VodCategory (
    id INTEGER PRIMARY KEY,
    categoryId TEXT NOT NULL,
    accountId TEXT,
    accountType TEXT,
    title TEXT,
    alias TEXT,
    url TEXT,
    activeSub INTEGER,
    censored INTEGER,
    extraJson TEXT,
    cachedAt INTEGER
);

CREATE TABLE IF NOT EXISTS VodChannel (
    id INTEGER PRIMARY KEY,
    channelId TEXT NOT NULL,
    categoryId TEXT,
    accountId TEXT,
    name TEXT,
    number TEXT,
    cmd TEXT,
    cmd_1 TEXT,
    cmd_2 TEXT,
    cmd_3 TEXT,
    logo TEXT,
    censored INTEGER,
    status INTEGER,
    hd INTEGER,
    drmType TEXT,
    drmLicenseUrl TEXT,
    clearKeysJson TEXT,
    inputstreamaddon TEXT,
    manifestType TEXT,
    extraJson TEXT,
    cachedAt INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesCategory (
    id INTEGER PRIMARY KEY,
    categoryId TEXT NOT NULL,
    accountId TEXT,
    accountType TEXT,
    title TEXT,
    alias TEXT,
    url TEXT,
    activeSub INTEGER,
    censored INTEGER,
    extraJson TEXT,
    cachedAt INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesChannel (
    id INTEGER PRIMARY KEY,
    channelId TEXT NOT NULL,
    categoryId TEXT,
    accountId TEXT,
    name TEXT,
    number TEXT,
    cmd TEXT,
    cmd_1 TEXT,
    cmd_2 TEXT,
    cmd_3 TEXT,
    logo TEXT,
    censored INTEGER,
    status INTEGER,
    hd INTEGER,
    drmType TEXT,
    drmLicenseUrl TEXT,
    clearKeysJson TEXT,
    inputstreamaddon TEXT,
    manifestType TEXT,
    extraJson TEXT,
    cachedAt INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesEpisode (
    id INTEGER PRIMARY KEY,
    accountId TEXT,
    categoryId TEXT,
    seriesId TEXT,
    channelId TEXT NOT NULL,
    name TEXT,
    cmd TEXT,
    logo TEXT,
    season TEXT,
    episodeNum TEXT,
    description TEXT,
    releaseDate TEXT,
    rating TEXT,
    duration TEXT,
    extraJson TEXT,
    cachedAt INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesWatchState (
    id INTEGER PRIMARY KEY,
    accountId TEXT,
    mode TEXT,
    categoryId TEXT,
    seriesId TEXT,
    episodeId TEXT,
    episodeName TEXT,
    season TEXT,
    episodeNum INTEGER,
    updatedAt INTEGER,
    source TEXT,
    seriesCategorySnapshot TEXT,
    seriesChannelSnapshot TEXT,
    seriesEpisodeSnapshot TEXT
);

CREATE TABLE IF NOT EXISTS BookmarkCategory (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS BookmarkOrder (
    id INTEGER PRIMARY KEY,
    bookmark_db_id TEXT NOT NULL,
    category_id TEXT,
    display_order INTEGER
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_series_watch_unique
ON SeriesWatchState (accountId, mode, categoryId, seriesId);
