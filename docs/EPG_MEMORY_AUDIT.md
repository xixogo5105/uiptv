# Desktop EPG memory audit — 14 October 2026

## Live evidence

Inspected the installed `/Applications/UIPTV.app` process (PID 38149) after the user launched it. This process contained the previous implementation, not these working-tree fixes.

- RSS initially 2,591,024 KiB (2.47 GiB).
- Java heap initially 2,051,066 KiB used, 2,359,296 KiB committed; maximum heap 12 GiB.
- A live-object histogram triggered collection: heap usage dropped to 416,278 KiB (406.5 MiB), while RSS remained around 2.64 GiB. Much of the peak was temporary allocation and committed/native memory, rather than live Java objects alone.
- Retained objects included 303,913 `EpgProgramme` instances, 11 cached XMLTV feeds, approximately 132 MiB of byte arrays and 160 MiB of integer arrays. Array totals include other application objects and cannot all be attributed to EPG.
- A 30-second JFR recording sampled roughly 262 MiB of integer-array allocation in JavaFX Metal effect/render-texture creation. This demonstrates rendering allocation pressure, not a proven rendering leak.
- Samples also identified repeated regex compilation during XMLTV channel-name matching. Matching now reuses compiled patterns and timestamp formatters. Visible-row completions are also batched over 100 ms, avoiding a complete viewport rebuild for every fast cached channel result.
- Native memory tracking was disabled in the installed JVM, so a native/VLC/GPU breakdown was unavailable. A single histogram does not establish an unbounded leak.

Diagnostic recordings were kept in `/tmp`; they are not committed or included in this report because raw diagnostics can contain application data.

## Changes

1. Channel catalog responses enrich from memory only. Desktop card population no longer performs its own blocking guide enrichment.
2. Desktop guide listing publishes channels before EPG work; visible rows hydrate their schedules afterward. Pending XMLTV rows remain visible while other rows fill in. Existing server guide-list behavior remains compatible.
3. XMLTV cache retention has a conservative estimated 64 MiB budget, retaining the existing 32-entry ceiling for small regional feeds. Parsing stops at an estimated 32 MiB retained-text/object budget per feed. These estimates are not exact JVM heap accounting.
4. XMLTV text is consumed incrementally, retaining at most 512 characters for names/titles and 2,048 for descriptions. Long descriptions are deliberately shortened. Existing XML security, decompressed input, programme-count and timeout limits remain active.
5. Native-provider schedules have an estimated 16 MiB cache budget and bounded titles/descriptions, in addition to the existing entry, input and concurrency limits.
6. Decoded image caching has a 48 MiB pixel budget, in addition to the existing 512-entry limit. This budget excludes images still referenced by visible nodes and GPU copies.
7. Memory-cache locks no longer cover network requests, discovery or XML parsing. Slow loads are serialized separately to bound concurrent parser memory; workers waiting for a load lock can be interrupted when navigation cancels them.
8. Desktop guide disposal cancels its catalog worker and releases rows/nodes. Channel-screen disposal stops EPG timers/workers and shuts down bookmark refresh work.
9. All packaged desktop launch profiles use a 512 MiB maximum Java heap and a 30-second G1 periodic collection interval to encourage idle heap reclamation. Builds can override `uiptv.desktop.max.heap`. This is a Java heap limit, not a total-process memory limit; VLC, JavaFX, GPU buffers, threads and other native allocations are additional.

No provider/local identity translation, database schema, account credentials or mobile changes were made as part of this audit. The pre-existing mobile edits were left intact.

## Validation

- Provider identity contract: 4 tests passed before refactoring.
- Full desktop Maven reactor tests with `-DargLine=-Xmx512m`: 990 passed (587 core, 88 API server, 315 desktop), before final lock/matcher and disposal refinements.
- Focused EPG, large-feed, lock-contention, guide UI, image-budget and channel UI tests with `-DargLine=-Xmx256m`: 69 passed after final lock/matcher refinements.
- Final desktop guide and channel-screen tests with a 256 MiB heap: 31 passed after lifecycle cleanup and batched rendering refinements.
- Web EPG gating: 3 tests passed.
- Large-feed regression loads eight synthetic feeds of 3,000 programmes with long descriptions and verifies memory eviction before the entry-count ceiling. The contention regression holds a local HTTP response open and requires memory-only enrichment to finish independently.

## Remaining measurement

The installed running app was not replaced or restarted. Measure a rebuilt app with the same accounts, EPG sources, window size and thumbnail settings, then repeat with embedded VLC playback. Capture RSS and post-collection live heap after startup, after navigation, after 30–60 minutes idle and after playback stops. The requested approximately 500 MB idle / 1 GB with VLC targets are not yet verified. If RSS continues growing while live heap stays flat, enable native-memory tracking at startup and investigate JavaFX rendering/native player allocations separately.
