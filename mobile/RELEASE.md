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

Run the desktop contract tests from the repo root:

```bash
./mvnw -pl core test
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

Every public Android release must use the same persistent signing keystore. Do not commit keystores or passwords, and do not rotate or recreate the key after the first stable-key release: Android only accepts in-place updates signed by the installed app's certificate.

```bash
export UIPTV_ANDROID_KEYSTORE=/absolute/path/uiptv-release.jks
export UIPTV_ANDROID_KEYSTORE_PASSWORD=...
export UIPTV_ANDROID_KEY_ALIAS=...
export UIPTV_ANDROID_KEY_PASSWORD=...
../scripts/build-mobile-with-libmpv.sh :androidApp:assembleRelease
```

The GitHub Actions `build_mobile_release` job requires these repository secrets:

- `UIPTV_ANDROID_KEYSTORE_BASE64`: base64 encoding of the JKS keystore file
- `UIPTV_ANDROID_KEYSTORE_PASSWORD`
- `UIPTV_ANDROID_KEY_ALIAS`
- `UIPTV_ANDROID_KEY_PASSWORD`

The workflow decodes the keystore to the runner's temporary directory and fails before building if any secret is missing. To encode a local keystore for the first secret on macOS, run `base64 < uiptv-release.jks | tr -d '\n'` and save its output directly as the GitHub secret; never paste the encoded value into the repository or chat. Add the secrets in the repository's **Settings -> Secrets and variables -> Actions**. Keep a secure backup of the keystore and passwords.

To verify the GitHub secrets without publishing a release, use **Actions -> Build -> Run workflow** on `main`. The manually triggered run builds and uploads a temporary release APK artifact but does not create a GitHub Release. The signing preparation step checks the keystore password and alias; the APK build also checks the key password. A successful run confirms the remote secret values can sign the artifact.

Local release builds still fall back to debug signing when the signing variables are absent; do not distribute those locally signed APKs as updates.

The existing 1.1.1 APK was built before this persistent signing setup. Its temporary CI debug key cannot be recovered from the repository, so users who installed that APK may need to uninstall it once when moving to the first APK signed with the permanent key. That uninstall removes local app data.

## Artifacts

- Debug APK: `androidApp/build/outputs/apk/debug/`
- Release APK: `androidApp/build/outputs/apk/release/`

The first distribution target is a downloadable APK. Play Store release needs a separate policy, signing, privacy, and listing pass.
