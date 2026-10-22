# UIPTV Android User Guide

## Interface And Appearance

The Android app uses Material Design 3 components and adapts its colors to the device's light or dark theme. Navigation is icon-only; on wide screens it can be hidden to give the current page more room. Search and add actions sit in the page header. Forms use outlined text fields and Material dropdowns; settings and account options use spaced Material switches. When adding or editing a Stalker account, the timezone field is a searchable dropdown containing the device's available timezone IDs. The embedded player keeps its dark controls and lock screen in either device theme.

## Pair With Desktop

1. Open the desktop UIPTV app on the same network.
2. In Android, open Settings → Desktop sync.
3. Enter the desktop host and port. The default port is `8888`.
4. Tap Test. If it connects, tap Pull.
5. Confirm the four-digit code on desktop when prompted.

Android imports accounts and bookmarks from desktop. It does not expose a sync server and it does not push local edits back to desktop in v1.

## Refresh Cache

Remote sync does not copy the full desktop channel cache in v1. After adding or syncing accounts:

1. Open Accounts.
2. Use Refresh All, or refresh one account from its row.
3. Keep the app open or let the refresh continue in the background.
4. Open Channels after the job completes.

Failed refreshes preserve the existing local cache.

Account rows show cached category and content counts. An account with no cached catalogue is labeled **No catalogue cached** instead of showing misleading zero counts.

## Browse And Play

- Accounts opens one catalogue stage at a time: content type, category, then channel or title list.
- Bookmarks contains saved channels and VOD/Series entries.
- Watching Now contains VOD and Series entries after playback has started and opens on Series by default.
- Series details and episode lists scroll together so the poster and summary do not take up a fixed portion of the episode view.
- On first playback, choose Native, a detected external player, or the system chooser.
- Use Remember to make that player the default. Settings can clear it later.

When EPG is enabled, live channel and bookmark rows show compact Now/Next information. Use the row's info action to open the full schedule in a bottom sheet; closing it returns to the same list position. There is no separate Live Guide screen. Loading, an unavailable source, stale saved data, and no matching schedule are shown as distinct states. Android refreshes through its existing guide repository and keeps its last saved data when a source fails.

Native playback uses Media3 and can handle HLS/DASH plus license-URL Widevine/ClearKey metadata. External players receive a normal Android `ACTION_VIEW` stream handoff.

The embedded mpv player uses compact Material-style controls on a dark surface, regardless of the Android light/dark theme. Its video scale menu offers:

- **Fit** — preserve the picture's proportions and show the full frame.
- **Crop** — preserve proportions while filling the display; the edges may be cropped.
- **Stretch** — fill the display without preserving proportions.

Live streams show a full, non-seekable progress track. On-demand video keeps its seekable timeline.

## Current Limits

- Android-to-desktop export is not part of v1.
- True resume position is not stored yet because the shared desktop-compatible watch-state tables do not have position/duration columns.
- Inline ClearKey JSON and Kodi inputstream metadata are detected but not opened by the native player yet.
- Thumbnail caching on Android uses the app-managed cache directory automatically. No `uiptv.ini` configuration is required or used on mobile.
