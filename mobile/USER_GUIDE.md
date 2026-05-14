# UIPTV Android User Guide

## Pair With Desktop

1. Open the desktop UIPTV app on the same network.
2. In Android, open Configuration.
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

## Browse And Play

- Channels has account, Live/VOD/Series, category, and search filters.
- Bookmarks contains saved channels and VOD/Series entries.
- Watching Now shows VOD and Series entries after playback has started.
- On first playback, choose Native, a detected external player, or the system chooser.
- Use Remember to make that player the default. Configuration can clear it later.

Native playback uses Media3 and can handle HLS/DASH plus license-URL Widevine/ClearKey metadata. External players receive a normal Android `ACTION_VIEW` stream handoff.

## Current Limits

- Android-to-desktop export is not part of v1.
- True resume position is not stored yet because the shared desktop-compatible watch-state tables do not have position/duration columns.
- Inline ClearKey JSON and Kodi inputstream metadata are detected but not opened by the native player yet.
