# UIPTV Android User Guide

UIPTV for Android is a standalone IPTV app for phones and tablets. It can manage provider accounts on its own. If you already use UIPTV Desktop, pairing the Android app with your desktop library is usually the quickest way to get started.

Get the universal APK from the [latest GitHub release](https://github.com/xixogo5105/uiptv/releases/latest). It supports Android 7.0 (API 24) and newer, with ARM 32-bit, ARM 64-bit, x86, and x86_64 native libraries.

## Recommended setup: pull your desktop library

The desktop app is the convenient place to maintain accounts and organise a shared library. Android initiates a one-way pull from desktop; it does not send Android changes back to desktop.

1. Install and open UIPTV Desktop on the computer that already has your accounts and library.
2. Start the desktop web server from its server settings. Keep desktop running while you pair and sync.
3. Find that computer’s local IP address. Use its LAN address, such as `192.168.1.20`, rather than `localhost` or `127.0.0.1`.
4. On Android, open **Settings → Desktop sync**. Enter the computer’s IP address and the desktop sync port (the default is `8888`). Enable HTTPS only if the desktop server is configured for HTTPS.
5. Tap **Test connection**. If it connects, tap **Sync now**.
6. Approve the request on the desktop when prompted. Android displays the verification code and sync progress while it waits.
7. When sync completes, refresh the account catalogue on Android, then browse and play.

Both devices need network access to each other. If the connection test fails, check the IP address, port, desktop server state, and firewall rules. Guest Wi-Fi or client isolation can prevent devices on the same Wi-Fi from communicating.

Sync is a one-way import into Android, with portable configuration fields selected for mobile use. The desktop remains the source of truth for maintaining the household library. Back up important data before syncing or restoring a database.

## Use Android independently

You can also set up Android without a desktop installation:

1. Open **Accounts** and add a Stalker Portal, Xtreme Codes, or M3U account.
2. Refresh the account to load its categories and catalogue.
3. Browse Live, VOD, or Series; use search, filters, and bookmarks to organise playback.
4. In **Settings → Playback**, select the embedded player or an installed external player. The embedded mpv player is included in the app; external players must be installed separately.
5. Use **Settings → Backup and restore** to protect or restore the Android app’s local data.

## Browse and play

- **Accounts** opens the selected account’s Live, VOD, or Series catalogue. Refresh an account before browsing if it has no cached catalogue.
- **Bookmarks** keeps saved channels, movies, and series close at hand.
- **Watching Now** lists VOD and Series after playback has started. Recent playback is available from the history view.
- Choose an available player when prompted. Use **Remember** to make it the default; change or clear the choice under **Settings → Playback**.
- Embedded playback supports the formats and stream behavior provided by mpv and Android’s media stack. External players may handle particular provider streams differently.
- On the embedded player, **Fit** preserves the full frame, **Crop** fills the display and trims edges, and **Stretch** fills the display without preserving proportions.
- Live channels show a live progress indicator and cannot be seeked like on-demand video.

## Programme guide

Enable the guide in **Settings → Programme guide** and configure an account guide source if needed. Android loads guide data for playback or when you explicitly open a channel’s guide. Guide data is not retained as a catalogue cache.

## Troubleshooting

- **Desktop connection fails:** ensure the desktop server is running; use the desktop’s LAN IP and configured port; check firewall and Wi-Fi isolation; match the HTTPS setting to the server.
- **No channels appear:** refresh the account on Android and wait for the refresh to finish. Syncing account details does not copy the desktop’s entire cached channel catalogue.
- **A stream reconnects:** Android may retry startup when a provider endpoint is intermittent. The player shows a reconnecting status and clears it once playback starts. If retries fail, try another player or refresh the account.
- **External player is missing:** install a compatible Android player, then choose it from the player picker or playback settings.
- **Restore or sync problems:** keep a recent backup and check the displayed error before retrying.

## Current limits

- Sync runs from desktop to Android; Android does not push account or bookmark edits back to desktop.
- Android stores its own playback state and local data. It does not currently share true resume position with desktop.
- EPG is fetched on demand and is not cached for catalogue cards.
- Some provider-specific DRM or Kodi inputstream metadata may require an external player.

For release details, see the [1.1.1 release notes](https://github.com/xixogo5105/uiptv/blob/v1.1.1/release_notes.md). For build and developer information, see [mobile/README.md](README.md).
