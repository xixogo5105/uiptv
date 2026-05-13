# UIPTV Mobile

This is the Android-first Kotlin Multiplatform workspace for UIPTV mobile.

## Modules

- `shared`: Kotlin Multiplatform module for shared Compose UI, sync contracts, and Android database migration support. It targets Android plus iOS framework outputs.
- `androidApp`: Android APK wrapper that renders the shared Compose UI and packages desktop migration SQL files from `core/src/main/resources` as app assets.

## Local Requirements

- Android SDK with API 36 installed.
- `ANDROID_HOME` set, or `sdk.dir=/path/to/android/sdk` in `mobile/local.properties`.
- Xcode command-line tools are required only for iOS framework linking.

## Useful Commands

```bash
./gradlew :shared:metadataCommonMainClasses
./gradlew :androidApp:assembleDebug
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

The Android APK output will be under `androidApp/build/outputs/apk/debug/` after `assembleDebug`.
