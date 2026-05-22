# UIPTV Mobile

This is the Android-first Kotlin Multiplatform workspace for UIPTV mobile.

## Modules

- `shared`: Kotlin Multiplatform module for shared Compose UI, sync contracts, account/cache contracts, and Android database migration support. It targets Android plus deferred iOS framework outputs.
- `androidApp`: Android APK wrapper that renders the shared Compose UI, packages desktop migration SQL files from `core/src/main/resources` as app assets, and wires Android DataStore, account storage, cache maintenance, and pull-from-desktop remote sync.

## Local Requirements

- End users installing a built APK do not need Android SDK, Gradle, or any other build tools. The APK packages the required libmpv and FFmpeg native libraries.
- Developers compiling from source need Android SDK with API 36 installed.
- `ANDROID_HOME` set, or `sdk.dir=/path/to/android/sdk` in `mobile/local.properties`.
- Xcode command-line tools are required only for iOS framework linking. iOS targets are deferred and only enabled automatically when `xcrun xcodebuild -version` works, or explicitly with `-Puiptv.enableIosTargets=true`.

### libmpv Dependency

The default Gradle profile uses the published `dev.jdtech.mpv:libmpv` dependency from Maven Central and targets Android 8/API 26+ devices. This is the fast path used by normal local builds and CI.

An Android 7/API 24 experimental profile is still available when needed. It rebuilds libmpv locally, copies the generated AAR into the app, and compiles with `-Puiptv.libmpv.profile=api24Local`:

```bash
./scripts/build-mobile-with-libmpv.sh :androidApp:assembleDebug
```

Run this command from the repository root. The script clones or updates `../libmpv-android`, applies the API 24 compatibility patch, builds `libmpv-release.aar`, copies it to `mobile/androidApp/libs/libmpv-android-api24.aar`, then runs the requested Gradle tasks.

To update the Android 7 libmpv build later, run the same script. It pulls the sibling checkout before rebuilding. The generated `mobile/androidApp/libs/` directory is intentionally ignored by git.

### Android SDK Setup

The simplest setup on every desktop platform is Android Studio:

1. Install Android Studio.
2. Open SDK Manager.
3. Install Android SDK Platform 36.
4. Install SDK Tools: Android SDK Platform-Tools and Android SDK Build-Tools.
5. Point Gradle at the SDK with `ANDROID_HOME` or `mobile/local.properties`.

macOS example:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platforms;android-36" "platform-tools" "build-tools;36.0.0"
```

Linux example:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platforms;android-36" "platform-tools" "build-tools;36.0.0"
```

Windows PowerShell example:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
sdkmanager.bat "platforms;android-36" "platform-tools" "build-tools;36.0.0"
```

Instead of setting `ANDROID_HOME`, you can create `mobile/local.properties`:

```properties
sdk.dir=/absolute/path/to/android/sdk
```

On Windows, forward slashes are accepted:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

## Compile And Test

Run all mobile commands from the `mobile/` directory.

Check shared Kotlin code, coverage, Android compilation, APK packaging, and Android test compilation:

```bash
./gradlew :shared:allTests :shared:koverVerify :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin
```

Build a debug APK only:

```bash
./gradlew :androidApp:assembleDebug
```

Build a release APK:

```bash
./gradlew :androidApp:assembleRelease
```

Build the Android 7/API 24 experimental APK from the repository root:

```bash
./scripts/build-mobile-with-libmpv.sh :androidApp:assembleDebug
```

Run instrumented Android tests on an attached device or emulator:

```bash
./gradlew :androidApp:connectedDebugAndroidTest
```

Build the shared metadata classes without packaging an APK:

```bash
./gradlew :shared:metadataCommonMainClasses
```

Build the deferred iOS simulator framework on macOS:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 -Puiptv.enableIosTargets=true
```

## APK Outputs

The Android APK output is written under:

- Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Release APK: `androidApp/build/outputs/apk/release/androidApp-release.apk`

The current Gradle configuration does not enable ABI splits, so `assembleDebug` and `assembleRelease` produce one universal APK. That APK includes all native libraries provided by dependencies, including `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`, and is the safest artifact for phones, tablets, and emulators.

A separate APK per CPU architecture is not required unless we choose to reduce download size. If we decide that x86-only Android devices and x86 emulators are not a target, we can later filter or split the native libraries to ARM-only APKs. Until then, the universal APK is larger but more compatible.

## CI Build

`.github/workflows/mobile.yml` runs the default Android 8+/API 26 mobile checks on every push and pull request. It builds the debug APK and uploads it as a workflow artifact named `uiptv-mobile-debug-apk`.

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
