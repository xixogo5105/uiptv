# Electronic programme guide

EPG starts **disabled** on fresh installs and upgrades. On desktop, open **Settings → Enable EPG** and save. The setting survives restarts and database backup/restore. The local-network web UI follows `/config`; it has no independent enable switch. It rechecks configuration while visible, at most once a minute. Android has its own **Config → Enable EPG on this device** switch, stored locally in DataStore. Syncing a desktop database does not turn Android EPG on.

When disabled, the relevant client skips guide downloads, native schedule requests and parsing, and hides EPG presentation. Sources and mappings remain saved. No worldwide guide is bundled or downloaded by default.

## Sources and matching

For each live channel, try these sources in order:

1. **Channel/embedded/provider:** a saved guide-ID override corrects XMLTV association; M3U `tvg-id` and playlist-header guide declarations identify the embedded guide; Stalker and Xtream supply native per-channel schedules when supported.
2. **Account:** the existing **EPG** field in account settings.
3. **Global:** **Global EPG sources** in desktop Settings or Android Config. This is the same shared Configuration value, carried by database sync. Android can edit it without a desktop running.

Account and global sources accept an XMLTV HTTP/HTTPS URL or local file, including gzip. Desktop has a file browser; Android also accepts readable content URIs. A desktop filesystem path may not exist on Android: use a shared HTTP/HTTPS source, or select a source accessible to the device. Updating the shared global source carries that value back in a later database sync.

An empty, unavailable, malformed or unmatched source falls through to the next source. Programmes must have valid times, a title, a matching channel, and a current or upcoming broadcast. An HTTP 200 containing other channels is not a match. A usable higher-priority schedule wins as a whole; unrelated feeds are never merged to fill gaps. Guide failure does not change playback commands or prevent ordinary browsing.

M3U header discovery recognizes `x-tvg-url`, `url-tvg` and `tvg-url`, in that order. Relative URLs resolve against the playlist. If an attribute lists comma-separated URLs, the first is used; failure then proceeds to account/global fallback. Embedded sources precede a manually entered account source. `tvg-id` is an identity mapping, not a source URL.

Xtream uses the cached `epg_channel_id` for XMLTV association and `stream_id` for native API requests and playback. Stalker recognizes cached `xmltv_id`/`epg_id` where present and uses the portal channel identity for API operations. Local SQLite channel row IDs are never guide IDs. Without an explicit override, a unique case-insensitive XMLTV display name can match; region names are retained; standalone quality labels can be ignored only for unique matches. Ambiguous names do not match.

On desktop, right-click a guide channel and choose **Guide ID override**. On Android, open **View Guide** on a live/bookmark row and enter the XMLTV channel ID. Leave it empty to restore provider mapping. Overrides use stable account identity plus provider channel identity, survive catalogue cache replacement, and travel with configuration backups/sync. An override uses exact matching, without a display-name fallback to an unrelated channel.

## Guide and player views

Desktop gear **Enable EPG / Disable EPG** and Settings update the same persistent feature setting. Bookmarks shows the current group's live channels; an account guide uses the selected category and search filters. Native schedules load for visible rows, rather than the entire catalogue. **Show all channels** retains unmatched XMLTV-only channels; ordinary lists always retain all channels. Native-provider guide rows stay present while loading and when unmatched, so filtering does not automatically pull successive unseen channels into the viewport and request the whole catalogue.

Double-click a channel or programme to play on desktop; the web uses single-click playback. A desktop single click selects programme details. Both guides retain six-hour navigation, **Now**, zoom, search/category filters, logos controlled by the thumbnail setting, playing indicators and virtualized rows. Background updates preserve scroll position. Client times render locally from UTC epoch milliseconds.

Desktop Now/Next remains adjacent to the video in compact mode, with wrapping titles and expandable programme details. No new control-bar overlay was added: the existing adjacent panel preserves small-screen playback controls and avoids the heavyweight VLC surface.

Web channel cards and the compact player show Now/Next automatically when enabled. **View Guide** is navigation; **View channel list** returns to the cards. XMLTV sources are resolved by the backend. `/config` exposes the enabled flag, never the global source or local filesystem path. `/liveGuide` and `/playerEpg` enforce the setting themselves.

Android Live and bookmark rows show Now/Next, local times and progress. **Live Guide** opens a channel-first list; **View Guide** opens a channel schedule with full titles/descriptions and mapping controls. Wide guide windows show the channel list and selected schedule side by side. Lists use lazy rendering and stable item keys. Shared minute ticks and repository caching keep work outside recomposition; leaving a screen, changing channels or backgrounding the app cancels the view's loads. Loading, no match, unavailable source and stale cached data have distinct presentation. The embedded mpv player's transient control panel also shows compact Now/Next, refreshed on channel changes and programme boundaries. External players retain their own UI.

## Provider compatibility, safety and caching

Xtream tries `get_simple_data_table`, then `get_short_epg`; Stalker tries authenticated `get_short_epg`, then `get_epg_info`. Existing authentication, cookies, URL encoding and HTTP helpers are reused. Stalker sessions are refreshed after an unauthorized response; unsupported actions and invalid data fall back softly. Portals vary, so a supported account does not guarantee every channel has EPG.

Native schedules normalize epoch seconds/milliseconds, offset-bearing timestamps and provider local dates into milliseconds. A provider timezone is used only for dates without an explicit offset. Native entries are bounded to 2,000 programmes per channel and a seven-day window. Desktop requests have at most two concurrent native loads and share in-flight work; Android serializes repository loads. Successful schedules cache for five minutes, negative results for one minute, with usable previous data retained after a failed refresh. Caches are isolated by account/provider/credential context and bounded to 256 channel schedules. Desktop/web native loads follow visible guide rows or the playing channel.

XMLTV parsing is streaming, disables external entities/DTDs, limits decompressed input to 200 MiB for desktop and Android sources (URLs and local files), and retained programmes to 200,000, and retains approximately the previous day through the next seven days. Desktop and Android share a DST/identity/missing-stop fixture to pin matching/time parity. Up to 32 parsed sources cache in memory. Desktop downloaded XMLTV feeds also persist on disk using `epg.tmp.cache.dir` (absolute directory, with a system temporary-directory fallback) and `epg.cache.ttl` (default `24h`, using the thumbnail TTL hour/day conventions). Fresh disk feeds are reused after restart without downloading. Only validated XMLTV replaces an existing cached feed. Refresh EPG clears both parsed memory and owned disk feed files; failures retain the previous guide and retry after five minutes. Playlist declarations cache for fifteen minutes (five minutes after failure). Source errors are reported without logging credential-bearing URLs in the EPG layer.

Guide caches remain bounded **in memory**, not SQLite-persistent. This keeps the change small and avoids introducing another cross-platform cache schema; offline guide data survives only within the running process. Sources, enablement and overrides are durable. No universal worldwide source is supplied.

## Validation

```sh
./mvnw -pl javafx-app -am test
node --test api-server/src/test/javascript/epg-gating.test.cjs
cd mobile
./gradlew :shared:allTests -Puiptv.enableIosTargets=false
ANDROID_SERIAL=<test-device> ./gradlew :androidApp:connectedDebugAndroidTest \
  -Puiptv.enableIosTargets=false -Puiptv.isolatedTestApp=true
```

The isolated test-app option installs under a separate package, preserving the normal UIPTV installation and its data. Tests use isolated databases, local HTTP fixtures and illustrative schedules; no IPTV subscription is required. The shared XMLTV fixture includes the repeated hour at the autumn DST change, ambiguous names and missing stop times. Resolver tests cover source precedence, unauthorized/unsupported providers, cache replacement, identity separation, default-off settings, API privacy/gating and migration/sync parity.

### Global EPG sources and refresh

Settings accepts multiple global XMLTV sources, one URL or local file per line. Put regional feeds (for example India, UK, USA and Australia) in priority order. Existing single-source settings remain compatible and sync uses the same configuration field. Each channel uses the first source with a usable matching schedule after provider/account sources; schedules are not combined. Duplicate names within a feed remain ambiguous. Unique matches can ignore standalone quality labels such as HD or 4K while retaining region names.

The desktop gear and Settings enable controls update the same persistent EPG flag. Refresh EPG clears transient guide, discovery and provider caches and reloads schedules, preserving accounts, bookmarks and saved mappings. Oversized feeds report a warning; use regional feeds instead of an oversized worldwide feed.

### Desktop INI cache settings

```ini
epg.tmp.cache.dir=/Volumes/backup/uiptv-database/epg/
epg.cache.ttl=24h
```

The TTL applies to XMLTV feed caching, not native Stalker/Xtream per-channel requests. URLs and credentials are hashed into disk filenames. The cache contains decompressed XMLTV, so reserve disk space accordingly. Android stores validated downloaded XMLTV privately in its app files directory, with a 24-hour lifetime and reuse after process restart. Refresh EPG clears its memory and disk caches. Desktop INI paths are machine-specific.
