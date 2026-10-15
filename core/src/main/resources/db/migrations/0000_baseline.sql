CREATE TABLE IF NOT EXISTS Configuration
(
    id
    INTEGER
    PRIMARY
    KEY,
    playerPath1
    TEXT,
    playerPath2
    TEXT,
    playerPath3
    TEXT,
    defaultPlayerPath
    TEXT,
    filterCategoriesList
    TEXT,
    filterChannelsList
    TEXT,
    pauseFiltering
    TEXT,
    serverPort
    TEXT,
    embeddedPlayer
    TEXT,
    cacheExpiryDays
    TEXT,
    enableThumbnails
    TEXT,
    wideView
    TEXT,
    languageLocale
    TEXT,
    tmdbReadAccessToken
    TEXT,
    filterLockHash
    TEXT,
    uiZoomPercent
    TEXT,
    autoRunServerOnStartup
    TEXT
    default
    '0',
    httpsServerEnabled
    TEXT
    default
    '0',
    httpsServerPort
    TEXT,
    vlcNetworkCachingMs
    TEXT,
    vlcLiveCachingMs
    TEXT,
    publishedM3uCategoryMode
    TEXT,
    enableVlcHttpUserAgent
    TEXT
    default
    '1',
    enableVlcHttpForwardCookies
    TEXT
    default
    '1',
    resolveChainAndDeepRedirects
    TEXT
    default
       '0',
    filterLockUnlockDurationMinutes
    TEXT
    default
       '15',
    vlcNoVideoTitleShow
    TEXT
    default
       '1',
    vlcQuiet
    TEXT
    default
       '1',
    vlcHttpReconnect
    TEXT
    default
       '1',
    vlcAdaptiveUseAccess
    TEXT
    default
       '1',
    vlcVout
    TEXT,
    vlcAvcodecHw
    TEXT,
    lightweightModeEnabled
    TEXT
    default
    '0',
    showBookmarkAndAccountCounts
    TEXT
    default
    '1',
    themeMode
    TEXT
    default
    'light',
    epgEnabled TEXT default '0',
    globalEpgSource TEXT default '',
    epgMappingsJson TEXT default '{}',
    epgRefreshIntervalHours TEXT default '2'
);

CREATE TABLE IF NOT EXISTS Account
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountName
    TEXT
    NOT
    NULL
    UNIQUE,
    username
    TEXT,
    password
    TEXT,
    xtremeCredentialsJson
    TEXT,
    url
    TEXT,
    macAddress
    TEXT,
    macAddressList
    TEXT,
    serialNumber
    TEXT,
    deviceId1
    TEXT,
    deviceId2
    TEXT,
    signature
    TEXT,
    epg
    TEXT,
    m3u8Path
    TEXT,
    type
    TEXT,
    serverPortalUrl
    TEXT,
    pinToTop
    TEXT,
    resolveChainAndDeepRedirects
    TEXT
    default
    '0',
    httpMethod
    TEXT,
    timezone
    TEXT,
    parentalLock
    TEXT
    default
       '1'
);

CREATE TABLE IF NOT EXISTS AccountInfo
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT
    NOT
    NULL
    UNIQUE,
    expireDate
    TEXT,
    accountStatus
    TEXT,
    accountBalance
    TEXT,
    tariffName
    TEXT,
    tariffPlan
    TEXT,
    defaultTimezone
    TEXT,
    profileJson
    TEXT,
    passHash
    TEXT,
    parentPasswordHash
    TEXT,
    passwordHash
    TEXT,
    settingsPasswordHash
    TEXT,
    accountPagePasswordHash
    TEXT,
    allowedStbTypesJson
    TEXT,
    allowedStbTypesForLocalRecordingJson
    TEXT,
    preferredStbType
    TEXT
);

CREATE TABLE IF NOT EXISTS Bookmark
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountName
    TEXT,
    categoryTitle
    TEXT,
    channelId
    TEXT,
    channelName
    TEXT,
    logo
    TEXT,
    cmd
    TEXT,
    serverPortalUrl
    TEXT,
    categoryId
    TEXT,
    accountAction
    TEXT,
    drmType
    TEXT,
    drmLicenseUrl
    TEXT,
    clearKeysJson
    TEXT,
    inputstreamaddon
    TEXT,
    manifestType
    TEXT,
    categoryJson
    TEXT,
    channelJson
    TEXT,
    vodJson
    TEXT,
    seriesJson
    TEXT
);

CREATE TABLE IF NOT EXISTS Category
(
    id
    INTEGER
    PRIMARY
    KEY,
    categoryId
    TEXT
    NOT
    NULL,
    accountId
    TEXT,
    accountType
    TEXT,
    title
    TEXT,
    alias
    TEXT,
    url
    TEXT,
    activeSub
    INTEGER,
    censored
    INTEGER
);

CREATE TABLE IF NOT EXISTS Channel
(
    id
    INTEGER
    PRIMARY
    KEY,
    channelId
    TEXT
    NOT
    NULL,
    categoryId
    TEXT,
    name
    TEXT,
    number
    TEXT,
    cmd
    TEXT,
    cmd_1
    TEXT,
    cmd_2
    TEXT,
    cmd_3
    TEXT,
    logo
    TEXT,
    censored
    INTEGER,
    status
    INTEGER,
    hd
    INTEGER,
    drmType
    TEXT,
    drmLicenseUrl
    TEXT,
    clearKeysJson
    TEXT,
    inputstreamaddon
    TEXT,
    manifestType
    TEXT,
    extraJson TEXT
);

CREATE TABLE IF NOT EXISTS VodCategory
(
    id
    INTEGER
    PRIMARY
    KEY,
    categoryId
    TEXT
    NOT
    NULL,
    accountId
    TEXT,
    accountType
    TEXT,
    title
    TEXT,
    alias
    TEXT,
    url
    TEXT,
    activeSub
    INTEGER,
    censored
    INTEGER,
    extraJson
    TEXT,
    cachedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS VodChannel
(
    id
    INTEGER
    PRIMARY
    KEY,
    channelId
    TEXT
    NOT
    NULL,
    categoryId
    TEXT,
    accountId
    TEXT,
    name
    TEXT,
    number
    TEXT,
    cmd
    TEXT,
    cmd_1
    TEXT,
    cmd_2
    TEXT,
    cmd_3
    TEXT,
    logo
    TEXT,
    censored
    INTEGER,
    status
    INTEGER,
    hd
    INTEGER,
    drmType
    TEXT,
    drmLicenseUrl
    TEXT,
    clearKeysJson
    TEXT,
    inputstreamaddon
    TEXT,
    manifestType
    TEXT,
    extraJson
    TEXT,
    cachedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS VodWatchState
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT,
    categoryId
    TEXT,
    vodId
    TEXT,
    vodName
    TEXT,
    vodCmd
    TEXT,
    vodLogo
    TEXT,
    updatedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesCategory
(
    id
    INTEGER
    PRIMARY
    KEY,
    categoryId
    TEXT
    NOT
    NULL,
    accountId
    TEXT,
    accountType
    TEXT,
    title
    TEXT,
    alias
    TEXT,
    url
    TEXT,
    activeSub
    INTEGER,
    censored
    INTEGER,
    extraJson
    TEXT,
    cachedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesChannel
(
    id
    INTEGER
    PRIMARY
    KEY,
    channelId
    TEXT
    NOT
    NULL,
    categoryId
    TEXT,
    accountId
    TEXT,
    name
    TEXT,
    number
    TEXT,
    cmd
    TEXT,
    cmd_1
    TEXT,
    cmd_2
    TEXT,
    cmd_3
    TEXT,
    logo
    TEXT,
    censored
    INTEGER,
    status
    INTEGER,
    hd
    INTEGER,
    drmType
    TEXT,
    drmLicenseUrl
    TEXT,
    clearKeysJson
    TEXT,
    inputstreamaddon
    TEXT,
    manifestType
    TEXT,
    extraJson
    TEXT,
    cachedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesEpisode
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT,
    categoryId
    TEXT,
    seriesId
    TEXT,
    channelId
    TEXT
    NOT
    NULL,
    name
    TEXT,
    cmd
    TEXT,
    logo
    TEXT,
    season
    TEXT,
    episodeNum
    TEXT,
    description
    TEXT,
    releaseDate
    TEXT,
    rating
    TEXT,
    duration
    TEXT,
    extraJson
    TEXT,
    cachedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS SeriesWatchState
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT,
    mode
    TEXT,
    categoryId
    TEXT,
    seriesId
    TEXT,
    episodeId
    TEXT,
    episodeName
    TEXT,
    season
    TEXT,
    episodeNum
    INTEGER,
    updatedAt
    INTEGER,
    source
    TEXT,
    seriesCategorySnapshot
    TEXT,
    seriesChannelSnapshot
    TEXT,
    seriesEpisodeSnapshot
    TEXT
);

CREATE TABLE IF NOT EXISTS SeriesWatchingNowSnapshot
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT,
    categoryId
    TEXT,
    seriesId
    TEXT,
    categoryDbId
    TEXT,
    seriesTitle
    TEXT,
    seriesPoster
    TEXT,
    episodesJson
    TEXT,
    updatedAt
    INTEGER
);

CREATE TABLE IF NOT EXISTS BookmarkCategory
(
    id
    INTEGER
    PRIMARY
    KEY,
    name
    TEXT
    NOT
    NULL
);

CREATE TABLE IF NOT EXISTS BookmarkOrder
(
    id
    INTEGER
    PRIMARY
    KEY,
    bookmark_db_id
    TEXT
    NOT
    NULL,
    category_id
    TEXT,
    display_order
    INTEGER
);

CREATE TABLE IF NOT EXISTS PublishedM3uSelection
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT
    NOT
    NULL
    UNIQUE
);

CREATE TABLE IF NOT EXISTS PublishedM3uCategorySelection
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT
    NOT
    NULL,
    categoryName
    TEXT
    NOT
    NULL,
    selected
    TEXT
    NOT
    NULL
);

CREATE TABLE IF NOT EXISTS PublishedM3uChannelSelection
(
    id
    INTEGER
    PRIMARY
    KEY,
    accountId
    TEXT
    NOT
    NULL,
    categoryName
    TEXT
    NOT
    NULL,
    channelId
    TEXT
    NOT
    NULL,
    selected
    TEXT
    NOT
    NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_series_watch_unique
    ON SeriesWatchState (accountId, mode, categoryId, seriesId);

CREATE UNIQUE INDEX IF NOT EXISTS idx_vod_watch_unique
    ON VodWatchState (accountId, categoryId, vodId);

-- EPG cache shares the application database; excluded from account/catalog clear operations.
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

-- Literal EPG matching exclusions. Annotation rules preserve HD/SD names;
-- quality rules apply only after exact and annotation-only matching fail.
CREATE TABLE IF NOT EXISTS epg_name_exclusion (
    term TEXT NOT NULL,
    scope TEXT NOT NULL CHECK (scope IN ('annotation','quality')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    PRIMARY KEY (term, scope)
) WITHOUT ROWID;
INSERT OR IGNORE INTO epg_name_exclusion (term,scope,enabled) VALUES
    ('1080i','annotation',1),
    ('1080i','quality',1),
    ('1080p','annotation',1),
    ('1080p','quality',1),
    ('1086p','annotation',1),
    ('1086p','quality',1),
    ('1088p','annotation',1),
    ('1088p','quality',1),
    ('1280p','annotation',1),
    ('1280p','quality',1),
    ('1440p','annotation',1),
    ('1440p','quality',1),
    ('144p','annotation',1),
    ('144p','quality',1),
    ('160p','annotation',1),
    ('160p','quality',1),
    ('180p','annotation',1),
    ('180p','quality',1),
    ('200p','annotation',1),
    ('200p','quality',1),
    ('214p','annotation',1),
    ('214p','quality',1),
    ('2160p','annotation',1),
    ('2160p','quality',1),
    ('224p','annotation',1),
    ('224p','quality',1),
    ('240p','annotation',1),
    ('240p','quality',1),
    ('268p','annotation',1),
    ('268p','quality',1),
    ('270p','annotation',1),
    ('270p','quality',1),
    ('288p','annotation',1),
    ('288p','quality',1),
    ('2k','quality',1),
    ('300p','annotation',1),
    ('300p','quality',1),
    ('320p','annotation',1),
    ('320p','quality',1),
    ('349p','annotation',1),
    ('349p','quality',1),
    ('352p','annotation',1),
    ('352p','quality',1),
    ('360p','annotation',1),
    ('360p','quality',1),
    ('368p','annotation',1),
    ('368p','quality',1),
    ('3840p','annotation',1),
    ('3840p','quality',1),
    ('384p','annotation',1),
    ('384p','quality',1),
    ('392p','annotation',1),
    ('392p','quality',1),
    ('396p','annotation',1),
    ('396p','quality',1),
    ('400p','annotation',1),
    ('400p','quality',1),
    ('404p','annotation',1),
    ('404p','quality',1),
    ('406p','annotation',1),
    ('406p','quality',1),
    ('410p','annotation',1),
    ('410p','quality',1),
    ('412p','annotation',1),
    ('412p','quality',1),
    ('416p','annotation',1),
    ('416p','quality',1),
    ('420p','annotation',1),
    ('420p','quality',1),
    ('4320p','annotation',1),
    ('4320p','quality',1),
    ('432p','annotation',1),
    ('432p','quality',1),
    ('440p','annotation',1),
    ('440p','quality',1),
    ('450p','annotation',1),
    ('450p','quality',1),
    ('472p','annotation',1),
    ('472p','quality',1),
    ('477p','annotation',1),
    ('477p','quality',1),
    ('478p','annotation',1),
    ('478p','quality',1),
    ('480i','annotation',1),
    ('480i','quality',1),
    ('480p','annotation',1),
    ('480p','quality',1),
    ('486p','annotation',1),
    ('486p','quality',1),
    ('4k','quality',1),
    ('50 fps','annotation',1),
    ('504p','annotation',1),
    ('504p','quality',1),
    ('50fps','annotation',1),
    ('512p','annotation',1),
    ('512p','quality',1),
    ('532p','annotation',1),
    ('532p','quality',1),
    ('536p','annotation',1),
    ('536p','quality',1),
    ('540p','annotation',1),
    ('540p','quality',1),
    ('548p','annotation',1),
    ('548p','quality',1),
    ('552p','annotation',1),
    ('552p','quality',1),
    ('562p','annotation',1),
    ('562p','quality',1),
    ('568p','annotation',1),
    ('568p','quality',1),
    ('576i','annotation',1),
    ('576i','quality',1),
    ('576p','annotation',1),
    ('576p','quality',1),
    ('580p','annotation',1),
    ('580p','quality',1),
    ('582p','annotation',1),
    ('582p','quality',1),
    ('600p','annotation',1),
    ('600p','quality',1),
    ('614p','annotation',1),
    ('614p','quality',1),
    ('616p','annotation',1),
    ('616p','quality',1),
    ('620p','annotation',1),
    ('620p','quality',1),
    ('640p','annotation',1),
    ('640p','quality',1),
    ('642p','annotation',1),
    ('642p','quality',1),
    ('648p','annotation',1),
    ('648p','quality',1),
    ('672p','annotation',1),
    ('672p','quality',1),
    ('684p','annotation',1),
    ('684p','quality',1),
    ('712p','annotation',1),
    ('712p','quality',1),
    ('714p','annotation',1),
    ('714p','quality',1),
    ('718p','annotation',1),
    ('718p','quality',1),
    ('720p','annotation',1),
    ('720p','quality',1),
    ('726p','annotation',1),
    ('726p','quality',1),
    ('751p','annotation',1),
    ('751p','quality',1),
    ('768p','annotation',1),
    ('768p','quality',1),
    ('8k','quality',1),
    ('900p','annotation',1),
    ('900p','quality',1),
    ('960p','annotation',1),
    ('960p','quality',1),
    ('backup','annotation',1),
    ('backup line test','annotation',1),
    ('fhd','quality',1),
    ('fhd@30fps','annotation',1),
    ('fhd@60fps','annotation',1),
    ('geo-blocked','annotation',1),
    ('h.264','annotation',1),
    ('h.264','quality',1),
    ('h.265','annotation',1),
    ('h.265','quality',1),
    ('h264','annotation',1),
    ('h264','quality',1),
    ('h265','annotation',1),
    ('h265','quality',1),
    ('hd','quality',1),
    ('hevc','annotation',1),
    ('hevc','quality',1),
    ('not 24/7','annotation',1),
    ('not 24/7'||char(59)||'geo-blocked','annotation',1),
    ('raw','annotation',1),
    ('sd','quality',1),
    ('shd','quality',1),
    ('uhd','quality',1),
    ('vip','annotation',1);
