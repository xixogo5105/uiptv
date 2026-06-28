ALTER TABLE Configuration ADD COLUMN vlcNoVideoTitleShow TEXT DEFAULT '1';
ALTER TABLE Configuration ADD COLUMN vlcQuiet TEXT DEFAULT '1';
ALTER TABLE Configuration ADD COLUMN vlcHttpReconnect TEXT DEFAULT '1';
ALTER TABLE Configuration ADD COLUMN vlcAdaptiveUseAccess TEXT DEFAULT '1';
ALTER TABLE Configuration ADD COLUMN vlcVout TEXT;
ALTER TABLE Configuration ADD COLUMN vlcAvcodecHw TEXT;