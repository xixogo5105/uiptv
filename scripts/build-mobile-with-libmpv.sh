#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[uiptv-libmpv] %s\n' "$*"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
code_root="$(cd "$repo_root/.." && pwd)"
mobile_dir="$repo_root/mobile"
libmpv_dir="${UIPTV_LIBMPV_DIR:-$code_root/libmpv-android}"
libmpv_url="${UIPTV_LIBMPV_URL:-https://github.com/jarnedemeulemeester/libmpv-android.git}"
mobile_aar="$mobile_dir/androidApp/libs/libmpv-android-api24.aar"
libmpv_aar="$libmpv_dir/libmpv/build/outputs/aar/libmpv-release.aar"

gradle_tasks=("$@")
if [ "${#gradle_tasks[@]}" -eq 0 ]; then
  gradle_tasks=(":androidApp:assembleDebug")
fi

patch_files=(
  "buildscripts/build.sh"
  "buildscripts/include/path.sh"
  "buildscripts/include/depinfo.sh"
  "buildscripts/scripts/lua.sh"
  "buildscripts/scripts/shaderc.sh"
  "libmpv/build.gradle.kts"
)

detect_android_home() {
  if [ -n "${ANDROID_HOME:-}" ]; then
    return
  fi

  local local_properties="$mobile_dir/local.properties"
  if [ -f "$local_properties" ]; then
    local sdk_dir
    sdk_dir="$(awk -F= '$1 == "sdk.dir" { print substr($0, index($0, "=") + 1) }' "$local_properties" | tail -n 1)"
    if [ -n "$sdk_dir" ]; then
      export ANDROID_HOME="$sdk_dir"
      log "Using Android SDK from mobile/local.properties: $ANDROID_HOME"
      return
    fi
  fi

  log "ANDROID_HOME is not set. libmpv download.sh will create/use its own SDK under the libmpv checkout."
}

ensure_libmpv_checkout() {
  if [ ! -d "$libmpv_dir/.git" ]; then
    log "Cloning libmpv-android into $libmpv_dir"
    git clone "$libmpv_url" "$libmpv_dir"
    return
  fi

  if [ "${UIPTV_LIBMPV_SKIP_PULL:-0}" = "1" ]; then
    log "Skipping libmpv git pull because UIPTV_LIBMPV_SKIP_PULL=1"
    return
  fi

  log "Updating libmpv-android checkout"
  (
    cd "$libmpv_dir"
    # These files are owned by the API24 compatibility patch below. Reset them
    # before pulling so the sibling checkout can be updated repeatedly.
    git checkout -- "${patch_files[@]}"
    git pull --ff-only
  )
}

link_existing_android_sdk() {
  if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
    return
  fi

  local sdkmanager_path=""
  if [ -d "$ANDROID_HOME/cmdline-tools" ]; then
    sdkmanager_path="$(find "$ANDROID_HOME/cmdline-tools" -path '*/bin/sdkmanager' -type f -print -quit)"
  fi
  if [ -z "$sdkmanager_path" ] && [ -x "$ANDROID_HOME/tools/bin/sdkmanager" ]; then
    sdkmanager_path="$ANDROID_HOME/tools/bin/sdkmanager"
  fi
  if [ -z "$sdkmanager_path" ]; then
    log "Android SDK at $ANDROID_HOME has no sdkmanager; libmpv download.sh will create its own SDK copy."
    return
  fi

  local sdk_dir="$libmpv_dir/buildscripts/sdk"
  mkdir -p "$sdk_dir"

  for sdk_name in android-sdk-linux android-sdk-mac; do
    local sdk_link="$sdk_dir/$sdk_name"
    if [ ! -e "$sdk_link" ]; then
      ln -s "$ANDROID_HOME" "$sdk_link"
      log "Linked $sdk_link -> $ANDROID_HOME"
    fi
  done
}

patch_libmpv_for_api24() {
  log "Applying Android 7/API 24 libmpv build patch"

  perl -0pi -e 's/local apilvl=26/local apilvl=24/g' "$libmpv_dir/buildscripts/build.sh"
  if ! grep -q 'opt/homebrew' "$libmpv_dir/buildscripts/build.sh"; then
    perl -0pi -e 's/(\t\tln -s \. "\$prefix_dir\/local"\n)/$1\t\tmkdir -p "\$prefix_dir\/opt"\n\t\tln -s .. "\$prefix_dir\/opt\/homebrew"\n/' "$libmpv_dir/buildscripts/build.sh"
  fi

  perl -0pi -e 's/v_cmake=4\.1\.2/v_cmake=3.22.1/g' "$libmpv_dir/buildscripts/include/depinfo.sh"

  perl -0pi -e 's#sdk/android-sdk-linux/ndk/#sdk/android-sdk-\$os/ndk/#g' "$libmpv_dir/buildscripts/include/path.sh"

  perl -0pi -e 's/# TO_BIN=\/dev\/null disables installing lua & luac\nmake INSTALL=\$\{INSTALL:-install\} INSTALL_TOP="\$prefix_dir" TO_BIN=\/dev\/null install/# INSTALL_EXEC=true and TO_BIN= disable installing lua \& luac.\nmake INSTALL=\${INSTALL:-install} INSTALL_TOP="\$prefix_dir" INSTALL_EXEC=true TO_BIN= install/g' "$libmpv_dir/buildscripts/scripts/lua.sh"

  perl -0pi -e 's/APP_PLATFORM=android-26/APP_PLATFORM=android-24/g' "$libmpv_dir/buildscripts/scripts/shaderc.sh"

  perl -0pi -e 's/minSdk = 26/minSdk = 24/g; s/version = "4\.1\.2"/version = "3.22.1"/g' "$libmpv_dir/libmpv/build.gradle.kts"
}

build_libmpv() {
  if [ "${UIPTV_LIBMPV_SKIP_DOWNLOAD:-0}" = "1" ]; then
    log "Skipping libmpv download/patch because UIPTV_LIBMPV_SKIP_DOWNLOAD=1"
  else
    log "Downloading/preparing libmpv native dependencies"
    (
      cd "$libmpv_dir/buildscripts"
      IN_CI="${IN_CI:-0}" ./download.sh
      ./patch.sh
    )
  fi

  if [ "${UIPTV_LIBMPV_SKIP_NATIVE_BUILD:-0}" = "1" ]; then
    log "Skipping libmpv native build because UIPTV_LIBMPV_SKIP_NATIVE_BUILD=1"
  else
    log "Building libmpv AAR for all Android ABIs"
    (
      cd "$libmpv_dir/buildscripts"
      IN_CI="${IN_CI:-0}" ./build.sh
    )
  fi

  if [ ! -f "$libmpv_aar" ]; then
    printf 'Expected libmpv AAR was not created: %s\n' "$libmpv_aar" >&2
    exit 1
  fi
}

copy_aar() {
  mkdir -p "$(dirname "$mobile_aar")"
  cp "$libmpv_aar" "$mobile_aar"
  log "Copied $libmpv_aar -> $mobile_aar"
}

build_mobile() {
  log "Running mobile Gradle tasks: ${gradle_tasks[*]}"
  (
    cd "$mobile_dir"
    ./gradlew "${gradle_tasks[@]}"
  )
}

detect_android_home
ensure_libmpv_checkout
link_existing_android_sdk
patch_libmpv_for_api24
build_libmpv
copy_aar
build_mobile
