DROP TABLE IF EXISTS removed_rss_accounts;
DROP TABLE IF EXISTS removed_rss_categories;
DROP TABLE IF EXISTS removed_rss_bookmarks;

CREATE TEMP TABLE removed_rss_accounts AS
SELECT CAST(id AS TEXT) AS accountId, accountName
FROM Account
WHERE type = 'RSS_FEED';

CREATE TEMP TABLE removed_rss_categories AS
SELECT CAST(id AS TEXT) AS rowId, categoryId, accountId
FROM Category
WHERE accountType = 'RSS_FEED'
   OR accountId IN (SELECT accountId FROM removed_rss_accounts);

CREATE TEMP TABLE removed_rss_bookmarks AS
SELECT CAST(id AS TEXT) AS bookmarkId
FROM Bookmark
WHERE accountName IN (SELECT accountName FROM removed_rss_accounts);

DELETE FROM BookmarkOrder
WHERE bookmark_db_id IN (SELECT bookmarkId FROM removed_rss_bookmarks);

DELETE FROM Bookmark
WHERE id IN (SELECT bookmarkId FROM removed_rss_bookmarks);

DELETE FROM Channel
WHERE categoryId IN (SELECT rowId FROM removed_rss_categories)
   OR categoryId IN (SELECT categoryId FROM removed_rss_categories);

DELETE FROM Category
WHERE accountType = 'RSS_FEED'
   OR accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM PublishedM3uChannelSelection
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM PublishedM3uCategorySelection
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM PublishedM3uSelection
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM VodWatchState
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM VodChannel
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM VodCategory
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts)
   OR accountType = 'RSS_FEED';

DELETE FROM SeriesWatchingNowSnapshot
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM SeriesWatchState
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM SeriesEpisode
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM SeriesChannel
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM SeriesCategory
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts)
   OR accountType = 'RSS_FEED';

DELETE FROM AccountInfo
WHERE accountId IN (SELECT accountId FROM removed_rss_accounts);

DELETE FROM Account
WHERE id IN (SELECT accountId FROM removed_rss_accounts)
   OR type = 'RSS_FEED';

DROP TABLE IF EXISTS ThemeCssOverride;

DROP TABLE IF EXISTS Configuration_rebuilt_0199;
CREATE TABLE Configuration_rebuilt_0199
(
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
    cacheExpiryDays TEXT,
    enableThumbnails TEXT,
    wideView TEXT,
    languageLocale TEXT,
    tmdbReadAccessToken TEXT,
    filterLockHash TEXT,
    uiZoomPercent TEXT,
    autoRunServerOnStartup TEXT DEFAULT '0',
    vlcNetworkCachingMs TEXT,
    vlcLiveCachingMs TEXT,
    publishedM3uCategoryMode TEXT,
    enableVlcHttpUserAgent TEXT DEFAULT '1',
    enableVlcHttpForwardCookies TEXT DEFAULT '1',
    resolveChainAndDeepRedirects TEXT DEFAULT '0',
    filterLockUnlockDurationMinutes TEXT DEFAULT '15'
);

INSERT INTO Configuration_rebuilt_0199
(
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
    cacheExpiryDays,
    enableThumbnails,
    wideView,
    languageLocale,
    tmdbReadAccessToken,
    filterLockHash,
    uiZoomPercent,
    autoRunServerOnStartup,
    vlcNetworkCachingMs,
    vlcLiveCachingMs,
    publishedM3uCategoryMode,
    enableVlcHttpUserAgent,
    enableVlcHttpForwardCookies,
    resolveChainAndDeepRedirects,
    filterLockUnlockDurationMinutes
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
    cacheExpiryDays,
    enableThumbnails,
    wideView,
    languageLocale,
    tmdbReadAccessToken,
    filterLockHash,
    uiZoomPercent,
    autoRunServerOnStartup,
    vlcNetworkCachingMs,
    vlcLiveCachingMs,
    publishedM3uCategoryMode,
    enableVlcHttpUserAgent,
    enableVlcHttpForwardCookies,
    resolveChainAndDeepRedirects,
    filterLockUnlockDurationMinutes
FROM Configuration;

DROP TABLE Configuration;
ALTER TABLE Configuration_rebuilt_0199 RENAME TO Configuration;

DROP TABLE IF EXISTS removed_rss_bookmarks;
DROP TABLE IF EXISTS removed_rss_categories;
DROP TABLE IF EXISTS removed_rss_accounts;
