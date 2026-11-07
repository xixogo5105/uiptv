# UIPTV 1.1.1 — Changes since the previous release

UIPTV 1.1.1 brings the first Android app into the combined desktop release and includes a broad round of desktop, web, playback, EPG, sync, and reliability improvements since 0.1.11.

## Android app

- Added a standalone Android app for phones and tablets, with Live TV, VOD, Series, account management, bookmarks, recent playback, filters, backups, and programme-guide settings.
- Added a universal APK for Android 7.0 (API 24) and newer, containing native libraries for ARM 32-bit, ARM 64-bit, x86, and x86_64.
- Added desktop-to-Android database sync. Android requests a one-way pull; the desktop user approves it before supported accounts, bookmarks, and portable configuration are imported. Android can also be configured independently.
- Added embedded mpv playback, external-player selection, video scaling, live indicators, EPG controls, reconnect retries, and software-decoder recovery.
- Pinned the Android native player build to libmpv-android source containing mpv 0.42.0. The CI cache records and verifies the pinned revision so an older AAR is not silently reused.
- Improved Android HLS and HTTPS detection, startup stability, player lifecycle cleanup, favourites, bookmarks, series browsing, and recent-history behavior.
- Added a more useful reconnecting status during automatic retries, with the final playback failure message reserved for retries that are exhausted.

See the [Android quick start](ANDROID.md) and [full Android user guide](mobile/USER_GUIDE.md).

## Desktop and web

- Improved embedded playback controls, playback indicators, channel surfing, fullscreen EPG visibility, guide layout, and programme-time labels.
- Reworked EPG around live, account-scoped requests. Playback or an explicit Guide action loads the guide; catalogue cards do not retain guide data. Removed the external Python EPG Builder and its packaging and bridge.
- Improved favourites and bookmarks across desktop, web, and mobile, including account-level catalogue-cache controls and clearer refresh results.
- Refined Watching Now, series details and episode navigation, recent playback, account browsing, and responsive layouts on the web app.
- Improved web playback routing, HLS and Cloudflare-protected M3U handling, player request headers, and remote playlist behavior.
- Continued work on published M3U selection, category modes, account/category/channel hierarchy, and request-host playlist URLs.
- Improved parental-lock handling, Stalker account identity and expiry behavior, bulk account import naming, and provider catalogue parsing.
- Added or improved translations, theme-aware player controls, settings help, and layout behavior on small screens.

## Sync, data safety, and reliability

- Hardened local and remote database synchronization, validation, snapshot handling, and configuration-column parity.
- Added bounded request and transfer handling, streamed large responses, improved sensitive-value redaction, and strengthened proxy and API edge cases.
- Improved cache reload coordination, failure reporting, and refresh behavior while retaining existing cached data after unsuccessful refreshes.
- Expanded desktop and mobile automated coverage and resolved static-analysis findings across playback, EPG, provider parsing, proxy, and lightweight UI code.

## Release packages and compatibility

- Published Windows x86_64, macOS x86_64 and ARM64, Linux x86_64 DEB and RPM, Linux x86_64 and ARM64 AppImage ZIPs, and the universal Android APK from one GitHub release.
- The Windows x86_64 package is expected to run on Windows 11 ARM64 through Windows x64 emulation. It is not a native ARM64 build. See [Microsoft’s Windows on Arm emulation documentation](https://learn.microsoft.com/en-us/windows/arm/apps-on-arm-x86-emulation).
- Fixed release automation to update every Maven module version, including Lightweight UI, and added release-time checks for matching Android versionCode and versionName.
- AppImage packaging now creates its output directory and publishes ZIP archives for both Linux architectures.

## Download and setup

- [Download the universal Android APK](https://github.com/xixogo5105/uiptv/releases/download/v1.1.1/UIPTV-android-universal-apk-UIPTV-android-universal.apk).
- [View all v1.1.1 release assets](https://github.com/xixogo5105/uiptv/releases/tag/v1.1.1).
- For the recommended multi-device setup, maintain accounts and your library on desktop, then approve a one-way pull from Android. See [ANDROID.md](ANDROID.md).
- Back up important desktop data before upgrading or syncing. Android sync pulls from desktop; Android changes are not pushed back to desktop.
