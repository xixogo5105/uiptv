# Electronic programme guide

EPG starts **disabled** on fresh installs and upgrades. On desktop, open **Settings → Enable EPG** and save. The setting survives restarts and database backup/restore. The local-network web UI follows `/config`; it has no independent enable switch. It rechecks configuration while visible, at most once a minute. Android has its own **Config → Enable EPG on this device** switch, stored locally in DataStore. Syncing a desktop database does not turn Android EPG on.

When disabled, the relevant client skips guide downloads, native schedule requests and parsing, and hides EPG presentation. Sources and mappings remain saved. No worldwide guide is bundled or downloaded by default.

The optional **playback-only EPG** mode is configured on desktop alongside the main EPG switch, and Android has its own local setting. The web UI follows the desktop values exposed by its read-only `/config` API; it has no EPG status, enable/disable control or playback-only toggle of its own. When the desktop setting is enabled, playback-only mode keeps catalogue, bookmark and account cards in their plain compact layout, performs no background guide refreshes or card lookups, and fetches guide data only when a live item is opened in the embedded player. The web player and cards render the JSON API's current/next data. Disabling the main EPG switch also disables this mode.

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

Desktop gear **Enable EPG / Disable EPG** and Settings update the same persistent feature setting. Settings also offers **playback-only EPG** directly below the main switch; it is unavailable while EPG is disabled. The web header exposes none of these controls or status labels: it reads the desktop values through `/config` and renders only the JSON API data. In full EPG mode, bookmarks and account channel lists use their normal virtual cards and add Now/Next titles, times and progress when a schedule is available. In playback-only mode, those cards remain plain and no card or background guide calls run; the playing item alone loads its latest guide data when playback starts. There is no separate web browse guide screen or web-side Show all channels control. The existing Now/Next panel beneath the player remains available in narrow layouts. With EPG disabled, the original cards, heights and menus are restored. Refresh EPG lives in desktop Settings beside the cache refresh controls; it clears guide caches and full-mode consumers fetch replacements on demand.

Double-click a channel or programme to play on desktop; the web uses single-click playback. A desktop single click selects programme details. Both guides retain six-hour navigation, **Now**, zoom, search/category filters, logos controlled by the thumbnail setting, playing indicators and virtualized rows. Background updates preserve scroll position. Client times render locally from UTC epoch milliseconds.

Desktop Now/Next remains adjacent to the video in compact mode, with wrapping titles and expandable programme details. No new control-bar overlay was added: the existing adjacent panel preserves small-screen playback controls and avoids the heavyweight VLC surface.

Web channel cards and the compact player show Now/Next automatically when enabled. The SPA reads the enabled and playback-only flags from the desktop `/config` response and obtains programme data from JSON endpoints; it cannot change those settings. XMLTV sources are resolved by the backend. `/config` exposes the enabled flags, never the global source or local filesystem path, and `/playerEpg` enforces the setting itself. There is no web-side EPG status/toggle UI.

Android Live and bookmark rows show Now/Next, local times and progress. **Live Guide** opens a channel-first list; **View Guide** opens a channel schedule with full titles/descriptions and mapping controls. Wide guide windows show the channel list and selected schedule side by side. Lists use lazy rendering and stable item keys. Shared minute ticks and repository caching keep work outside recomposition; leaving a screen, changing channels or backgrounding the app cancels the view's loads. Loading, no match, unavailable source and stale cached data have distinct presentation. The embedded mpv player's transient control panel also shows compact Now/Next, refreshed on channel changes and programme boundaries. **View Guide** opens a side drawer from the right; swiping inward from the far left or far right edge of the video opens it on that side, with the on-air title, local times, progress, full description and Up next. Swiping outward toward the same edge, tapping outside the drawer or tapping **Close guide** dismisses it. The drawer is shown even while the guide is still loading and respects player locking and the mobile EPG enable switch. External players retain their own UI.

## Provider compatibility, safety and caching

Xtream tries `get_simple_data_table`, then `get_short_epg`; Stalker tries authenticated `get_short_epg`, then `get_epg_info`. Existing authentication, cookies, URL encoding and HTTP helpers are reused. Stalker sessions are refreshed after an unauthorized response; unsupported actions and invalid data fall back softly. Portals vary, so a supported account does not guarantee every channel has EPG.

Native schedules normalize epoch seconds/milliseconds, offset-bearing timestamps and provider local dates into milliseconds. A provider timezone is used only for dates without an explicit offset. Native entries are bounded to 2,000 programmes per channel and a seven-day window. Desktop requests have at most two concurrent native loads and share in-flight work; Android serializes repository loads. Native channel schedules, including unavailable results, cache for 30 minutes, with usable previous data retained after a failed refresh. Caches are isolated by account/provider/credential context and bounded to 256 channel schedules. Desktop native loads run only in the enabled background importer, prioritizing bookmarks and rotating through cached live channels. Cards and player guides read persisted rows.

XMLTV parsing is streaming, disables external entities and DTD loading, limits decompressed input to 200 MiB for desktop and Android sources (URLs and local files), and retained programmes to 200,000, and retains approximately the previous day through the next seven days. Desktop and Android share a DST/identity/missing-stop fixture to pin matching/time parity. Desktop schedules persist in the main SQLite database; imports use bounded, temporary compressed storage. Full-mode Settings controls the background scheduler check interval (1, 2, 3, 5, 10, 15, 30 or 60 minutes; default 3 minutes) separately from each source's 1–24-hour freshness interval. Playback-only mode does not run this background importer. Failed refreshes retain the previous records and retry after five minutes. Playlist declarations cache for fifteen minutes (30 minutes after failure). Source errors are reported without logging credential-bearing URLs in the EPG layer.

Guide schedules and mappings are persisted in bounded SQLite tables in the existing application database. The in-memory layer is limited to visible summaries, active player data and bounded provider/image caches. Sources, enablement, playback-only mode and overrides are durable. No universal worldwide source is supplied.

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

Settings accepts multiple global XMLTV sources, one URL or local file per line. Put regional feeds (for example India, UK, USA and Australia) in priority order. Existing single-source settings remain compatible and sync uses the same configuration field. Desktop schedules prefer embedded/provider, account and then global sources. Specific programme titles override generic placeholders for overlapping intervals. Duplicate names within a feed remain ambiguous. Unique matches can ignore standalone quality labels such as HD or 4K while retaining region names.

The desktop gear and Settings enable controls update the same persistent EPG flag. Refresh EPG clears transient guide, discovery and provider caches and reloads schedules, preserving accounts, bookmarks and saved mappings. Oversized feeds report a warning; use regional feeds instead of an oversized worldwide feed.

### Cache storage

Desktop EPG records live in dedicated tables within the existing application database. No separate EPG cache directory or INI TTL setting is needed. Settings controls the scheduler check interval and the separate source freshness interval, and provides Refresh EPG to clear and rebuild the cache. Android stores validated downloaded XMLTV privately in its app files directory, with a 24-hour lifetime and reuse after process restart.

The desktop XML reader uses bounded 8 MiB general/total entity-text budgets to support large feeds with many standard XML character escapes. DTD and external entity resolution remain disabled. XML processing-limit and I/O failures are reported separately from malformed XML.

On mobile, XMLTV URLs pointing to desktop loopback (`localhost`, `127.0.0.1`, or `::1`) use the configured desktop sync host for fetching, preserving their port, credentials and query string. The saved source remains unchanged. The desktop service must be reachable on that port. Standard external XMLTV DOCTYPE declarations are ignored without fetching the DTD; internal DTD declarations remain rejected.

Desktop full-mode refresh runs in the background at the scheduler interval selected in Settings, only while full EPG mode is enabled. Native schedules refresh earlier if their coverage is ending, subject to the configured source freshness and retry times. Minute clock/progress updates reuse persisted timestamps. Playback-only mode makes one player-scoped request when the embedded player starts or changes channel and does not maintain a guide timeline or background refresh loop. Mobile full mode refreshes through its existing repository lifecycle; playback-only mobile mode follows the same player-only rule. Manual Refresh EPG resets the cache for source changes or provider corrections.

### Desktop loading and memory limits

Channel catalogs render without waiting for XMLTV discovery, downloads or parsing. In full EPG mode, visible virtual cards read persisted Now/Next summaries in the background; detached and off-screen cards do not initiate guide queries. In playback-only mode, cards remain plain and the player is the only guide entry point. XMLTV imports compress schedules while parsing, then persist them and release the parsed feed. Native request state has a 16 MiB budget, and decoded cached images a 48 MiB pixel budget. XMLTV parsing bounds compressed data per feed and shortens descriptions to 2,048 characters. The 200 MiB decompressed-input limit remains active. Full schedules load on demand when View Guide is selected.

Packaged desktop launchers cap the Java heap at 512 MiB and enable periodic idle collection. The build property `uiptv.desktop.max.heap` overrides the heap setting. Native JavaFX/VLC memory is additional; the release acceptance ceiling is 900 MiB total process footprint. Ordinary logos, channels, bookmarks and compact episode cards use a bounded 96×96 profile; VOD/series artwork uses a separate bounded 512×768 profile. See [the desktop memory audit](docs/EPG_MEMORY_AUDIT.md) for measurements and validation limits.

### Virtual-card guide details

EPG adds details inside the existing virtual cards at every width; it does not replace bookmark or account browsing. View Guide in the existing context/“…” menu opens a separately owned native popup. Its horizontally virtualized programme cards use the same badge, title, time, progress and description styling as the panel beneath the player. All available schedule entries remain accessible by horizontal scrolling or arrow controls. The popup adapts from one card on narrow windows to several cards across wider windows, closes on Escape or outside interaction, and releases its contents on close. The separate browse timeline and its refresh/show-all controls have been removed.

### Strict viewport loading and player overlay

Guide loading requires a measured, visible scroll viewport and a non-empty card rectangle. Cards are checked again on the JavaFX thread immediately before each background lookup, so a queued card cannot start a lookup after scrolling off-screen. Now/Next summaries are reused until a programme transition or the 30-minute refresh point; minute progress updates reuse those summaries. Cards returning from the detached cache restore their summaries without another lookup.

With EPG enabled, a compact header keeps the thumbnail beside the title/account; the summary below spans the full card width. Plain-text mode uses the same full-width summary below its text header. EPG-off cards retain their original structure.

When the playing channel has a guide, wide player mode and fullscreen include a View Guide control after the up/down controls; narrow mode omits it because the adjacent EPG panel already provides the details. In full EPG mode, the control reuses the player-adjacent summary's availability. In playback-only mode, the player request supplies the current context and the control opens that selected channel's guide. The guide is an inline JavaFX overlay, not another native window; Escape or the close control releases it, and changing channels clears the old schedule.

## Persistent database cache (current desktop/server behavior)

EPG cache records now use separate tables in the existing application database. Cards query persisted summaries in full mode; the background importer runs only when full EPG mode is enabled. The source freshness interval remains 1–24 hours, default 2 hours, while the scheduler check interval is saved separately as 1, 2, 3, 5, 10, 15, 30 or 60 minutes, default 3 minutes. Playback-only mode bypasses the importer and performs guide work only for the playing item. Cache updates no longer rebuild channel/bookmark pages. XMLTV channel icons are cached as metadata and loaded through the existing image cache only as a fallback. See [EPG_CACHE.md](docs/EPG_CACHE.md) for source priority, generic-title handling, migrations, refresh/clear semantics and tests.
