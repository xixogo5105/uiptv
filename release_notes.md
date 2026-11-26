# UIPTV 1.1.3

Release date: 26 November 2026

UIPTV 1.1.3 adds advertising to the Android app, alongside Android playback, desktop player, catalogue, and release workflow improvements since 1.1.1.

## Highlights

- **Android ads:** Added Google AdMob ads with consent controls and a 60-minute display cooldown (maximum one ad per hour).
- **Android release build:** GitHub Actions builds a signed universal APK using the production AdMob configuration and attaches the APK directly to the GitHub Release.
- **Android playback and usability:** Improved playback stability, HLS and HTTPS handling, player lifecycle recovery, account and bookmark behavior, and guide presentation.
- **Desktop player:** Improved macOS picture-in-picture support, VLC video surface handling, stream metadata, and player controls.
- **Catalogue and account handling:** Improved M3U parsing, account refresh behavior, bookmark matching, and Watching Now cards.
- **Build and dependency updates:** Updated Android Gradle and Maven/JavaFX dependencies and expanded ARM64 Linux package builds.

## Android app

Download the universal APK from the Assets section below. Android builds include native libraries for ARM and x86 device architectures. The release APK is signed using the configured Android release key.

Ads are served through Google AdMob. Ad availability depends on the app's AdMob status, configuration, consent, network, and inventory.

## Upgrade notes

- Back up your desktop database before upgrading, especially before applying a database sync between installations.
- Android sync remains a pull from desktop. Keep UIPTV Desktop running, start its server, and approve the request on desktop before importing the snapshot.
- Android can also be set up independently by adding accounts directly.

For detailed setup, see the [Android quick start](https://github.com/xixogo5105/uiptv/blob/main/ANDROID.md) and [Android user guide](https://github.com/xixogo5105/uiptv/blob/main/mobile/USER_GUIDE.md).
