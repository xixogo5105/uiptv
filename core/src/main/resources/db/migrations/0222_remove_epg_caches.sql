-- EPG is fetched live for one channel. No guide state is persisted.
DROP TABLE IF EXISTS epg_change_history;
DROP TABLE IF EXISTS epg_change_state;
DROP TABLE IF EXISTS epg_programme;
DROP TABLE IF EXISTS epg_alias;
DROP TABLE IF EXISTS epg_channel_mapping;
DROP TABLE IF EXISTS epg_channel_icon;
DROP TABLE IF EXISTS epg_shared_source;
DROP TABLE IF EXISTS epg_source;
DROP TABLE IF EXISTS epg_name_exclusion;
DROP TABLE IF EXISTS source_blacklist;
UPDATE Configuration SET epgPlaybackOnly='1', globalEpgSource='', epgMappingsJson='', epgRefreshIntervalHours='', epgSchedulerIntervalMinutes='', globalEpgRefreshIntervalHours='';
