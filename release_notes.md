# UIPTV 1.1.1

Release date: 7 November 2026

UIPTV 1.1.1 is the first combined desktop and Android release. The Android app is distributed as a universal APK and works independently; pairing it with UIPTV Desktop is the recommended way to bring an existing library to a phone or tablet.

## Highlights

- **New Android app:** browse and play Live TV, VOD, and Series; manage bookmarks and recent playback; choose embedded mpv or an installed external player; and configure guide, appearance, filters, backup, and playback settings.
- **Desktop-to-Android sync:** securely connect to a running desktop server, approve a one-way pull on desktop, and bring supported accounts, bookmarks, and portable configuration to Android. The desktop remains the recommended library hub; Android can also be set up and used on its own.
- **Latest Android playback engine:** release APK is built with pinned libmpv-android source containing mpv 0.42.0, packaged for ARM 32-bit, ARM 64-bit, x86, and x86_64 devices.
- **More reliable Android playback:** improved HLS and HTTPS handling, startup retries, stream lifecycle stability, decoder recovery, and clearer reconnect messaging.
- **Cross-platform EPG improvements:** live, account-scoped guide loading; clearer programme times and status; more reliable identity matching; and better fullscreen visibility and layout across desktop, web, and Android.
- **Better catalogue and playback controls:** improved channel surfing, favourites, bookmarks, Watching Now and series navigation, player controls, and responsive web playback.
- **Safer sync and data handling:** hardened database sync validation, transfer limits, snapshot handling, and clearer progress and completion feedback.
- **Linux AppImages:** x86_64 and ARM64 AppImages are distributed as ZIP archives. Debian and RPM packages remain available for x86_64.
- **Windows on ARM:** the Windows x86_64 release is expected to run on Windows 11 ARM64 through Windows x64 emulation. It is not a native ARM64 build, so performance may differ. [Microsoft documents x64 app emulation on Windows 11 Arm](https://learn.microsoft.com/en-us/windows/arm/apps-on-arm-x86-emulation).

## Desktop changes

- Improved embedded player controls, EPG layout, fullscreen guide access, playback indicators, and channel surfing.
- Refined desktop and web browsing for favourites, recent playback, Watching Now, series details, and small screens.
- Added or improved account-level catalogue cache controls, reload reporting, M3U/Cloudflare handling, provider imports, and Stalker account identity behavior.
- Continued hardening of local API, playlist proxy, database synchronization, migrations, and resource handling.
- Updated packaging and release automation so all Maven modules receive the same release version and native packages are staged from their module output.

## Android app

Android is new in this release. Start with the [Android quick start](https://github.com/xixogo5105/uiptv/blob/main/ANDROID.md), then see the [full Android user guide](https://github.com/xixogo5105/uiptv/blob/main/mobile/USER_GUIDE.md) for setup, desktop pairing, playback, backups, known limitations, and troubleshooting. [Download the universal APK](https://github.com/xixogo5105/uiptv/releases/download/v1.1.1/UIPTV-android-universal-apk-UIPTV-android-universal.apk).

For the complete cross-platform summary of changes since the previous release, read <a href="https://github.com/xixogo5105/uiptv/blob/main/release.md" target="_blank" rel="noopener">release.md</a>.

## Upgrade notes

- Back up your desktop database before upgrading, especially before applying a database sync between installations.
- Android sync is a pull from desktop. Keep UIPTV Desktop running, start its server, and approve the displayed request on the desktop before Android imports the snapshot.
- Android can work independently by adding accounts directly. Sync is recommended when you already maintain accounts and bookmarks on desktop.

## Verification and maintenance

This release includes broad desktop and mobile test coverage, Android host tests, Android device instrumentation coverage, and cross-platform fixes. Dependency updates include JavaFX 27.0.1, Android Gradle Plugin 9.5.1, and the pinned mpv 0.42.0 Android native build.
