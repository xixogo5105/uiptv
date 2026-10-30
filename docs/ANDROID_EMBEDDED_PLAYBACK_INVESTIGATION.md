# Android embedded playback investigation — 27 October 2026

## Scope and conclusion

Investigated short periodic pauses in FOX CRICKET 4K* on the attached Huawei
AGS2-W09 (Android 8), reported smooth in external MX Player. Installed release
build 508 was examined before changes; diagnostic build 512 was built, tested,
and installed with app data preserved.

After installing build 512 and replaying the same channel, the user confirmed
"Looks smooth now". This result and the measurements support the presentation/UI
work as a contributor. The exact offending operation has not been isolated by
individual reversions, and this short session does not prove all streams fixed. The 50 fps stream also runs on a
60 Hz-only display, which can cause cadence judder even with zero decoder drops.

## What was measured

- Before changes, the player was actively reading HLS segments through
  `EmbeddedHlsProxy`. Normal segment downloads must not be confused with a
  runaway request loop. The proxy uses a persistent OkHttp client/connection pool
  for upstream requests; loopback connections closing does not prove repeated
  upstream TLS negotiation.
- A recent log sample contained 23 app messages across approximately 104 seconds:
  18 native HLS messages and five GC messages. This does not support aggressive
  logging as the primary cause during that sample.
- GC runs lasted about 110–145 ms in total, but their reported stop-the-world
  pauses were only about 0.4–0.6 ms. Total concurrent GC duration is not a video
  freeze measurement.
- Thread snapshots showed active RenderThread, SurfaceTexture and Mali graphics
  work. EPG coroutine worker threads had little accumulated CPU time. Snapshot
  CPU values are not a controlled before/after benchmark.
- Historical graphics statistics included slow UI frames, but mixed earlier
  navigation/startup with playback and cannot establish a playback stall cause.
- In build 512, 14 valid diagnostic samples spanned 65.128 wall-clock seconds;
  playback advanced 65.140 seconds. Buffered duration ranged from 8.661 to
  18.347 seconds; decoder drops stayed at zero and output drops remained at one
  from startup. No sampled cache pause occurred. Diagnostic UI-ticker delay
  ranged from 1 to 17 ms. Five-second samples cannot exclude every brief event.
- A separate steady-state graphics window contained 935 frames, zero janky
  frames, zero missed vsyncs and zero slow UI-thread frames. This is Android UI
  timing, not proof that every decoded frame was presented evenly.
- The stream reports 50 fps. Android display information exposes just one mode:
  1200×1920 at approximately 60 Hz. Refresh-rate matching cannot select a 50 Hz
  mode on this device. The channel name alone does not establish stream resolution.
- A system scheduling trace was attempted but the device could not allocate the
  trace buffer. No conclusions are drawn from that failed trace.

The longer capture contained 32 valid samples across 155.255 seconds;
playback advanced 155.260 seconds. Buffer duration stayed between 7.488 and
18.347 seconds; decoder drops remained zero and output drops stayed at the
single startup drop. The final graphics window contained 5,439 frames, two
janky frames (0.04%), zero missed vsyncs and zero slow UI-thread frames. The
user reported smooth playback during this experiment. Temporary diagnostics
were disabled afterward.

## Code findings and changes

`MpvEmbeddedPlayerActivity` previously forwarded every `time-pos` observation to
its UI thread. Each property callback updated seek-bar appearance and rebuilt
its play/pause drawable, even when controls were hidden. Its one-second progress
poll also synchronously queried native state and codec metadata, then reapplied
its TextureView matrix. Reconfiguration events scheduled multiple groups of
three more metadata refreshes.

The change caches clock observations off the UI queue, presents progress once
per second only when controls are visible, avoids rebuilding an unchanged
play/pause icon, reads metadata on coalesced reconfiguration notifications, and
reapplies the video transform only when track details change (or actual surface/
zoom/output changes). Clock updates still clear the startup spinner while the
controls are hidden. Opening controls immediately renders the latest clock.
App and Media3 logs are disabled unless a debug build is explicitly opted in;
WorkManager logs are suppressed in every build. The bundled MPV JNI wrapper is
built without Android log output, and terminal/config-file output is disabled.
The AAR build checks all four JNI binaries before stamping the quiet-logging
marker; app builds reject older AARs. libmpv warning events remain
available to the internal decoder-recovery callback and are not forwarded through
the app logger in release; the explicit debug launch switch raises MPV verbosity
and forwards its events through the gated app logger for local diagnostics. Hardware decoding, audio output and buffer policy were held constant
for this comparison. There are no new settings or user-facing labels.

App log output is disabled in release builds and in debug builds unless the app
is launched with the explicit `com.uiptv.mobile.extra.ENABLE_APP_LOGS` boolean
extra. With that extra set on a debug build, one background playback sample every
five seconds records clock, cache duration/speed, cache pause state, decoder/output
drop counters, A/V sync, source/output fps and decoder/output modes. Samples
exclude stream URLs and provider credentials. Native diagnostic reads share the
lifecycle queue with release so a queued read does not run after native destruction.

```sh
adb shell am force-stop com.uiptv.mobile
adb shell am start -n com.uiptv.mobile/.android.MainActivity --ez com.uiptv.mobile.extra.ENABLE_APP_LOGS true
adb logcat -s UIPTV-Mpv:D UIPTV-MpvEmbedded:D UIPTV-Playback:D UIPTV-Native:D UIPTV-WatchHistory:W UIPTV-Recent:W UIPTVRemoteSync:V UiptvMigration:W UIPTV-Metadata:W
```

EPG source inspection found one request per selected channel, with cancellation
on stop/channel switch, and no recurring refresh timer in the embedded player.
A single progressive request can still download/parse XMLTV while playback is
running. The inspection does not prove that EPG was disabled on this device or
exclude one-off guide contention; it does rule out the hypothesized timer in
this player. Future tests should compare EPG enabled/disabled only if a pause
coincides with guide activity.

## Research and next experiments

1. **Presentation path.** Android recommends SurfaceView over TextureView for
   lower power and more accurate frame timing. TextureView video is composed
   through the UI/GPU path, which is affected by UI work. Upstream mpv-android
   itself uses SurfaceView. If pauses remain, compare a SurfaceView implementation
   on this same device and stream while preserving the existing decoder/audio
   configuration. Validate fit/crop/stretch, overlays, lifecycle and rotation;
   do not simply replace the view without those checks.
2. **Cadence.** Compare 25/50 fps and 30/60 fps content on the fixed 60 Hz screen.
   Distinguish regular pan judder from an isolated long freeze. Enabling expensive
   GPU interpolation by default would be speculative on this device and could
   worsen performance.
3. **Transport.** If buffer duration approaches zero during a reported pause,
   measure HLS segment first-byte/download timing and HTTP failures, then test a
   startup/rebuffer cushion. `cache-secs=20` is a ceiling, not a promise that
   playback starts with 20 seconds buffered. Current `cache-pause=no`,
   `cache-pause-initial=no`, `cache-pause-wait=0` favor immediate playback and
   offer no deliberate refill cushion. Healthy sampled buffering does not
   justify changing those defaults in this experiment.
4. **Local recording comparison.** Play a short local copy of the affected
   stream through embedded playback and MX Player to separate network delivery
   from timestamps/presentation. Use one provider connection at a time and
   preserve stream privacy. This comparison has not yet been performed.

Primary references, read 27 October 2026:

- [Android: surface types](https://developer.android.com/media/media3/ui/surface)
- [AOSP: TextureView composition and SurfaceView](https://source.android.com/docs/core/graphics/arch-tv)
- [mpv-android SurfaceView implementation](https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt)
- [mpv issue 1177: motion judder, including MX Player comparison](https://github.com/mpv-android/mpv-android/issues/1177)
- [mpv issue 1194: UI interactions and frame drops](https://github.com/mpv-android/mpv-android/issues/1194)
- [mpv issue 1320: stutter despite hardware decoding, other players smooth](https://github.com/mpv-android/mpv-android/issues/1320)
- [mpv stable manual: buffering and diagnostics](https://mpv.io/manual/stable/)
- [FFmpeg HTTP protocol/connection options](https://ffmpeg.org/ffmpeg-protocols.html)

Issue reports describe other devices and content. They support test hypotheses,
not a diagnosis of this Huawei. Suggested interpolation settings in issue
comments are not an established upstream fix.

## Validation

- `./mvnw -pl core -Dtest=ProviderIdentityContractTest test`: 4 passed.
- Android `:androidApp:testDebugUnitTest`: 12 passed (stream resolver and proxy).
- Android `:androidApp:assembleRelease`: passed; build 512 installed successfully.
- Android `:androidApp:compileDebugAndroidTestKotlin`: passed.
- Added opt-in device smoke coverage that injects clock observations and checks
  hidden controls are not rebuilt. Compiled, not executed; device smoke tests
  remain separate from headless unit/build runs.
- Manual same-stream playback on the attached device produced the metrics above.
- No commit or push was performed for this investigation.


## Failed-stream cleanup (2026-10-30)

HLS proxy shutdown cancels registered calls and closes client sockets without
interrupting OkHttp worker threads. Socket/call registration and the shutdown
snapshot share a lock, so a request cannot miss cancellation during teardown.
Each new playback load retires its predecessor's proxy; stopping the activity
also retires the proxy, preventing old-stream downloads from lingering behind
another playback session. Startup retries remain capped and generation guarded.
