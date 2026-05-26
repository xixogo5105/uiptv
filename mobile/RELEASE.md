# UIPTV Android Release Checklist

## Release Ownership

Do not publish Android-only GitHub Releases. The mobile project can build debug and release APKs for verification, but public GitHub Releases are owned by the repository-root `release.sh` flow.

Run `./release.sh X.Y.Z` from the repository root to publish a release. The script updates Maven and Android versions, creates the annotated `vX.Y.Z` tag, and the main build workflow publishes one combined desktop plus Android release.

## Local Verification

Run from `mobile/`:

```bash
../scripts/build-mobile-with-libmpv.sh :shared:allTests :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin
./gradlew :androidApp:connectedDebugAndroidTest
```

Run the shared desktop contract tests from the repo root:

```bash
./mvnw -pl uiptv-shared test
```

## Manual Smoke Test

- Pair with desktop using Configuration -> Test -> Pull.
- Add one local account and save it.
- Refresh one account cache and refresh all accounts.
- Browse Live, VOD, and Series.
- Add and remove a bookmark.
- Open a stream with the player picker.
- Remember a default player and clear it from Configuration.
- Check large font mode on a phone viewport.
- Check a tablet-width viewport; navigation should move to the side rail.

## Signing

Release signing is configured through environment variables. Do not commit keystores or passwords.

```bash
export UIPTV_ANDROID_KEYSTORE=/absolute/path/uiptv-release.jks
export UIPTV_ANDROID_KEYSTORE_PASSWORD=...
export UIPTV_ANDROID_KEY_ALIAS=...
export UIPTV_ANDROID_KEY_PASSWORD=...
../scripts/build-mobile-with-libmpv.sh :androidApp:assembleRelease
```

If these variables are missing, the release build falls back to debug signing so CI can still assemble the artifact.

## Artifacts

- Debug APK: `androidApp/build/outputs/apk/debug/`
- Release APK: `androidApp/build/outputs/apk/release/`

The first distribution target is a downloadable APK. Play Store release needs a separate policy, signing, privacy, and listing pass.
