# Embedded VLC stream lifecycle

## Findings (18 October 2026)

The previous implementation detached the picture on close, but serialized every
native start, release and subsequent initialization through one global queue. A
stalled `media().play()` or synchronous `controls().stop()` therefore delayed all
later streams. `PlaybackUIService` awaited that queue before requesting the next
provider URL. Its loading method showed only a spinner inside a hidden container,
and provider resolution exceptions could leave that spinner without an error.
Reload/reconnect also had an independent background path whose late response could
reopen a closed player.

Relevant upstream contracts:

- [vlcj 4 threading model](https://capricasoftware.co.uk/tutorials/vlcj/4/thread-model):
  callbacks execute on libVLC threads; do not call back into libVLC or block them.
- [vlcj 4.12.1 MediaApi](https://github.com/caprica/vlcj/blob/vlcj-4.12.1/src/main/java/uk/co/caprica/vlcj/player/base/MediaApi.java):
  `play` prepares media then invokes controls. `start` additionally waits for a
  playing/error event. Replacing media can itself enter native teardown.
- [VLC 3 media player implementation](https://github.com/videolan/vlc/blob/3.0.x/lib/media_player.c):
  stop locks the input, stops/joins it and terminates output resources before
  returning. Java Future interruption is not a safe native cancellation contract.

## Current behavior

- Show the embedded region and loading feedback immediately, before initialization
  or provider URL resolution. Provider failures and empty URLs show an inline error.
- Each native session owns its factory, player, render gate and serial command queue.
  Retirement immediately revokes its UI identity and disables frame publication.
  A fresh session initializes independently before obtaining a short-lived URL.
- Send an independent native stop so a blocked start can be woken. Retirement waits
  for both the start queue and that stop to finish before its final stop and release.
  Never release a player while a stop/start is still using it.
- Native initialization, controls, audio state and metadata reads run off JavaFX and
  off native callback threads. UI actions use cached playing/track/codec information.
- Close and channel changes invalidate provider/reload results and delayed retries.
  Interrupt cancellable Java work; ignore late completions from older requests.
- A 45-second deadline applies to each loading phase (provider resolution and then
  media startup). Expiry cancels pending resolution, retires the session and leaves
  an error and controls visible. This is not an inactivity deadline for playing video.
- At most four native sessions may hold resources per player instance. If native
  shutdown never returns, further allocation fails promptly and shows an error;
  capacity becomes available again after cleanup completes. This bounds resources
  while accommodating the reported two or three stuck streams.

A permanently wedged native plugin cannot be safely killed inside the JVM. Stronger
recovery from that class of failure requires an out-of-process player. No forced
native thread interruption, concurrent release, or VLC 4-only stop API is used.

## Validation

`VlcPlaybackLifecycleTest` covers blocked start/stop, initialization while closing,
bounded allocation and recovered capacity, late reload cancellation, immediate
loading/error visibility, and startup deadline handling. Provider identity remains
covered by `ProviderIdentityContractTest`.

Native VLC network recovery is checked manually in the application. It is not an
automated test because exercising the native player opens a visible JavaFX window;
automated tests must remain headless. Lifecycle regression coverage remains in
`VlcPlaybackLifecycleTest`. Manual validation with one VLC build does not establish
compatibility with every provider, RTSP/UDP source, platform, or native VLC build.
A previously failing provider account should still be exercised in the normal
application.
