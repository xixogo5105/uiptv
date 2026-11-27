# Flutter libmpv Investigation

## Selected integration

The desktop Flutter project uses:

- `media_kit` 1.2.6
- `media_kit_video` 2.0.1
- `media_kit_libs_video` 1.0.7

The packages were selected after inspecting their implementation rather than
README claims. `media_kit` loads and creates native libmpv through Dart FFI.
`media_kit_video` provides a Flutter `Video` widget backed by a native video
controller and a Flutter texture.

The native implementation uses the libmpv client handle and creates a native
video output for Windows, Linux, macOS and iOS. It is not an external `mpv`
process wrapper.

## Platform support

The aggregate `media_kit_libs_video` package installs platform-specific native
library packages:

- Windows: `media_kit_libs_windows_video`
- macOS: `media_kit_libs_macos_video`
- Linux: `media_kit_libs_linux`

These packages must still be packaged and redistributed, and codec/licence
terms must be reviewed before release. The native packages are currently
dependencies, not yet verified distribution artifacts.

## Current implementation

`desktop_flutter/lib/player/uiptv_player.dart` owns a `Player` and
`VideoController` and exposes play, pause, stop, volume and stream URL methods.
`desktop_flutter/lib/screens/uiptv_desktop_shell.dart` embeds the
`Video` widget inside the UIPTV window.

The player accepts HTTP headers through `Media.httpHeaders`, keeping stream
credentials in the native player path instead of Flutter source code.

## Verification status

- Flutter and Dart toolchains verified on Apple Silicon macOS.
- macOS debug build: PASS.
- macOS release build: PASS (ARM64, `--no-tree-shake-icons`).
- Current-host macOS debug build: PASS on 2026-11-27 after the settings,
  backup, cache-reload, and screen changes in this migration pass.
- Public HLS fixture reached an embedded 1920x1080 native Metal texture:
  PASS.
- Production stream `https://jmp2.uk/stvp-IN4000600Q`:
  - HTTP/CDN redirect chain: PASS.
  - FFprobe detected H.264/AAC variants from 640x360 to 1920x1080: PASS.
  - 10-second FFmpeg decode: PASS.
  - macOS native in-window libmpv run created a 1920x1080 Metal texture:
    PASS.
  - final video-frame/sound observation in macOS UI: NOT TESTED/remaining
    manual acceptance check.
- `flutter analyze`: PASS.
- Flutter API client tests: PASS.
- Packaged macOS release artifact with the Java runtime and libmpv dependency
  bundle: NOT TESTED in this migration pass.
- Windows native build: NOT TESTED; appropriate Windows runner required.
- Linux native build: NOT TESTED; appropriate Linux runner required.
- Visible playback, channel zapping, tracks, seek, fullscreen, hardware decode,
  resizing and native cleanup: NOT TESTED until native build toolchains and a
  safe test stream are available.
