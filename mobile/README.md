# UIPTV Mobile

This is the Android-first Kotlin Multiplatform workspace for UIPTV mobile.

## Modules

- `shared`: Kotlin Multiplatform module for shared Compose UI, sync contracts, account/cache contracts, and Android database migration support. It targets Android plus deferred iOS framework outputs.
- `androidApp`: Android APK wrapper that renders the shared Compose UI, packages desktop migration SQL files from `core/src/main/resources` as app assets, and wires Android DataStore, account storage, cache maintenance, and pull-from-desktop remote sync.

## Local Requirements

- Android SDK with API 36 installed.
- `ANDROID_HOME` set, or `sdk.dir=/path/to/android/sdk` in `mobile/local.properties`.
- Xcode command-line tools are required only for iOS framework linking. iOS targets are deferred and only enabled automatically when `xcrun xcodebuild -version` works, or explicitly with `-Puiptv.enableIosTargets=true`.

## Useful Commands

```bash
./gradlew :shared:metadataCommonMainClasses
./gradlew :shared:allTests
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:compileDebugAndroidTestKotlin
./gradlew :androidApp:connectedDebugAndroidTest
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Puiptv.enableIosTargets=true
```

The Android APK output will be under `androidApp/build/outputs/apk/debug/` after `assembleDebug`.
The connected Android test task requires a running emulator or attached device.

## User And Release Docs

- [USER_GUIDE.md](USER_GUIDE.md): desktop pairing, sync, cache refresh, browsing, and player selection.
- [RELEASE.md](RELEASE.md): verification commands, manual smoke test, signing variables, and artifact paths.

Release signing is driven by environment variables so keystores and passwords stay outside the repository:

```bash
export UIPTV_ANDROID_KEYSTORE=/absolute/path/uiptv-release.jks
export UIPTV_ANDROID_KEYSTORE_PASSWORD=...
export UIPTV_ANDROID_KEY_ALIAS=...
export UIPTV_ANDROID_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

Without those variables, release builds use debug signing. This keeps CI buildable while making real release signing explicit.
