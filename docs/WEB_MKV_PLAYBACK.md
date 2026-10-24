# Browser MKV playback

The web SPA uses `javascript/mkv-player.js` for non-DRM Matroska streams. The API
returns `mediaInfo.strategy = "mkv"`; the SPA also recognizes `.mkv` paths and the
Stalker `stream=filename.mkv` parameter. Media is fetched through the existing
same-origin `/proxy-stream` endpoint. The proxy does not transcode media.

Mediabunny 1.62.1 parses Matroska and writes incremental fragmented MP4 into
Media Source Extensions. Its ESM build is pinned in the import map in
`index.html` and loaded only when an MKV starts. The AC-3 extension uses that
same module instance. Both packages are MPL-2.0 and are loaded from jsDelivr;
media stays between the provider, UIPTV proxy, and the viewing browser.

- Video packets are copied unchanged. The browser must support the original
  video codec through MSE; unsupported video fails before media attachment.
- Compatible audio is copied unchanged. When AC-3/E-AC-3 cannot be played
  directly, the lazy-loaded `@mediabunny/ac3` WASM decoder feeds a browser AAC
  encoder, or Opus if AAC encoding is unavailable. This fallback downmixes to
  stereo at 48 kHz and 128 kbit/s. It does not transcode video.
- The primary video and audio tracks are selected. Embedded subtitles and
  alternate audio selection are not exposed by this adapter.
- Reading uses an 8 MiB input cache. Producers pause around 30 seconds ahead of
  playback, and old buffered data is evicted on subsequent appends.
- Seeking outside the buffer restarts from a preceding video keyframe and the
  requested audio position, retaining the original timestamps. Efficient random
  access depends on the provider's byte-range support and the MKV index.
- Stopping or switching aborts HTTP reads, disposes the demuxer/decoders, cancels
  outputs, removes the seek listener, and revokes the media object URL.

The existing live MPEG-TS, HLS, DASH and native MP4 paths are unchanged. There is
no full-video WASM fallback. Error labels live in every core language bundle and
are mirrored into `epg-labels.json`, which the SPA already loads for its locale.

Validation includes the API format-inspection tests, the JavaScript MKV adapter
and routing tests, and browser smoke tests with H.264/AAC and HEVC/AC-3 samples.
