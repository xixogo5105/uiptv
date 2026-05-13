ALTER TABLE Configuration ADD COLUMN vlcNetworkCachingMs TEXT;
ALTER TABLE Configuration ADD COLUMN vlcLiveCachingMs TEXT;
ALTER TABLE Configuration ADD COLUMN enableVlcHttpUserAgent TEXT default '1';
ALTER TABLE Configuration ADD COLUMN enableVlcHttpForwardCookies TEXT default '1';
