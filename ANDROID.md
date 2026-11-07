# UIPTV for Android

Download the [universal Android APK](https://github.com/xixogo5105/uiptv/releases/latest). It supports Android 7.0 (API 24) and newer, including ARM 32-bit, ARM 64-bit, x86, and x86_64 devices.

Android can be used independently by adding Stalker Portal, Xtreme Codes, or M3U accounts in the app. If you already use UIPTV Desktop, pairing is the recommended setup: maintain accounts and the organised library on desktop, then pull them to Android when you want to watch on a phone or tablet.

## Pair Android with UIPTV Desktop

1. Run UIPTV Desktop on the computer that holds your library and start its web server.
2. On Android, open **Settings → Desktop sync**. Enter the desktop computer’s LAN IP address and sync port (default `8888`). Enable HTTPS if the desktop server uses it.
3. Tap **Test connection**, then **Sync now**.
4. Approve the request on the desktop when prompted. Android shows the verification code and sync progress.
5. Refresh the account catalogue on Android after sync, then browse Live, VOD, or Series.

Sync is a one-way pull from desktop to Android. Android does not push its changes back to desktop. Keep both devices reachable on the same network, and back up important data before sync or restore operations.

See the [full Android user guide](mobile/USER_GUIDE.md) for standalone setup, playback controls, backups, guide settings, troubleshooting, and current limitations. Read the [1.1.1 release notes](https://github.com/xixogo5105/uiptv/blob/v1.1.1/release_notes.md) or get the [v1.1.1 release](https://github.com/xixogo5105/uiptv/releases/tag/v1.1.1).
