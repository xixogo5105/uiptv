# Release Notes

## New Features

*   **Embedded Media Player:** The application now includes an embedded media player. VLC is the default player, but requires VLC to be installed and in the system's PATH. A lite/limited player is available as a fallback.
*   **About Page and Update Mechanism:** A new "About" page has been added, which includes a mechanism to check for updates. (This will be improved in future releases).
*   **ARM and x86 Releases:** We now provide separate releases for ARM and x86 architectures.

## Improvements

*   **UI Changes:** Collapsible/expandable panels have been replaced with tabs to improve user experience and save horizontal space.
*   **YouTube and RSS Fixes:** Fixes for RSS and YouTube have been implemented. Playing YouTube videos/streams now requires `yt-dlp` to be installed and in the system's PATH.
*   When there is only one category, the channels of this category are now automatically loaded.

## Fixes

*   Miscellaneous bug fixes since release 7.
*   Fixed some SQL sync issues.
*   Fixed some volume slider issues.
*   Fixed Null Pointer Exception.
*   Fixed an issue with passing arguments to an external player.
*   Hardware decoding fixes for VLC, and some mute/unmute fixes.

## Build

*   **GraalVM Support Removed:** GraalVM support has been removed from the project.
*   **JPackage Builder:** The project now uses `jpackage` to create application bundles.
*   The project now requires JDK 17 to compile.
*   Updated various dependencies.
