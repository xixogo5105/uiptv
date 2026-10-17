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
9. Release packaged desktop launch profiles use a 512 MiB maximum Java heap and a 30-second G1 periodic collection interval to encourage idle heap reclamation. Builds can override `uiptv.desktop.max.heap`. This is a Java heap limit, not a total-process memory limit; VLC, JavaFX, GPU buffers, threads and other native allocations are additional.

No provider/local identity translation, database schema, account credentials or mobile changes were made as part of this audit. The pre-existing mobile edits were left intact.

## Validation

- Provider identity contract: 4 tests passed before refactoring.
- Full desktop Maven reactor tests with `-DargLine=-Xmx512m`: 990 passed (587 core, 88 API server, 315 desktop), before final lock/matcher and disposal refinements.
- Focused EPG, large-feed, lock-contention, guide UI, image-budget and channel UI tests with `-DargLine=-Xmx256m`: 69 passed after final lock/matcher refinements.
- Final desktop guide and channel-screen tests with a 256 MiB heap: 31 passed after lifecycle cleanup and batched rendering refinements.
- Web EPG gating: 3 tests passed.
- Large-feed regression loads eight synthetic feeds of 3,000 programmes with long descriptions and verifies memory eviction before the entry-count ceiling. The contention regression holds a local HTTP response open and requires memory-only enrichment to finish independently.

## Remaining measurement

The packaged release artifact was subsequently measured on macOS after a clean launch. The process settled below the agreed 900 MiB ceiling after JavaFX texture allocation; the exact result is recorded in the final measurement below. A separate embedded-VLC playback run remains a useful platform smoke test because VLC and GPU surfaces are native allocations outside the Java heap.

## Follow-up: large regional feeds and EPG-only filtering

The first memory guard could reject a feed below the 200 MiB download limit because its expanded programme strings exceeded the 32 MiB per-feed estimate. Schedules now compress in bounded 512 KiB batches while parsing, without retaining the whole expanded guide. Only requested channels expand and normalize, with a bounded per-feed cache of expanded schedules. Catalog memory-only reads never trigger expansion. Missing stop times and overlaps are resolved across all chunks for a requested channel.

A read-only check parsed all 12 actual cached XMLTV files with a 256 MiB heap, including 66.3, 67.8 and 73.6 MiB feeds. All succeeded; the largest retained estimate was 3.8 MiB. Source URLs and programme contents were not included in probe output. The compressed-data, input-size, deadline and programme-count limits still protect unusually large or malformed feeds.

EPG-only mode no longer includes confirmed empty channels because they are native providers, because every schedule is empty, or because any source has a warning. Pending channels remain visible with an explicit loading state while their guides are checked; resolved empty rows are hidden. When hiding rows exposes new pending rows, hydration continues into the newly visible viewport. Show all channels displays empty schedules in a rounded, vertically aligned programme-cell surface in both themes.

## Responsive guide and final validation

Guide panes below 900 pixels now show virtualized Now/Next channel cards instead of a cropped multi-hour timeline. Cards provide View Guide, which opens a separately owned native popup containing a horizontally virtualized per-channel programme list. Popup contents are released when it closes; scene detachment, account-context changes and disposal close it. Wide guide panes continue to use the timeline. Both light and dark styles include aligned empty-schedule surfaces.

A full low-heap run exposed player-control icons being decoded at their original resolution even though they display at 20 pixels. They now decode at 40 pixels to allow double-density rendering without retaining full-size PNG buffers.

Final full Maven reactor validation with a 256 MiB heap: 994 tests passed (588 core, 88 API server, 318 desktop). Guide UI tests cover strict empty-row filtering despite source warnings/native-provider flags, explicit pending/loading rows, compact Now/Next content, popup ownership, full schedule population, cleanup and switching back to the wide timeline. Native-window preview tests keep the JavaFX test runtime alive after closing their preview stage. The actual-feed probe described above passed independently. Total-process idle and VLC RSS targets still require a fresh run of the rebuilt app; a heap cap is not a total RSS cap.

## Final presentation: original virtual cards at every width

The responsive standalone guide/timeline described above was subsequently removed at the user's request. Both bookmark and account browsing now always use their original virtual grids and show all channels. Enabling EPG appends a bounded Now/Next summary inside visible live cards, plus View Guide in the existing right-click and ellipsis menus. Disabling EPG restores the original cards/heights/menu contents and stops guide timers and workers. The existing player-adjacent Now/Next panel is retained. The removed standalone guide source and its obsolete UI tests were replaced by tests of actual bookmark/channel cards and the retained player panel.

Each grid owns one cancellable loader, observes its scroll viewport and resolves at most 16 visible cards per batch. Detached cards are removed from its binding registry. A card retains only Now/Next records, not the full multi-day schedule. Native/XMLTV source cache lifetimes still govern network refresh; minute UI updates reuse cached sources. View Guide loads only the selected channel's full schedule into a virtualized horizontal popup and clears its content on close. The popup avoids JavaFX's fixed-cell-size height interaction that previously added unwanted vertical scrolling.

Refresh EPG now lives only in Settings beside the cache controls. It clears in-memory and disk guide caches off the UI thread, then signals active cards and the player panel to refresh on demand. There is no browse-screen refresh button or Show all channels switch.

Final virtual-card validation: 991 tests passed in the complete Maven desktop reactor with a 256 MiB heap (588 core, 88 API, 315 desktop). Coverage includes original grids in both EPG modes, Now/Next card content, right-click/ellipsis guide availability, channels without guides remaining visible, viewport-only background resolution, detached-binding cleanup, retained player-panel layouts, Settings cache clearing without eager fetches, bookmark/provider identity and popup layouts in both themes. Obsolete timeline-screen tests were removed along with the timeline class. Narrow and wide popup previews were rendered and inspected; the horizontal programme cards no longer cause unwanted vertical scrolling. Total process RSS with VLC remains a live-run measurement, not something implied by the test heap limit.

Final lifecycle refinements also passed the focused card/bookmark/channel/player UI suite with a 256 MiB heap. Hidden views cancel guide work and release summary nodes; returning to a view or resizing queues its visible cards again. Player-adjacent guide requests are cancelled on channel changes or detachment, and disabling EPG clears retained programme text.

## Critical review and boundary hardening — 17 October 2026

The release review also covered the API and remote-sync trust boundaries. JSON/text request bodies are capped at 8 MiB, remote-sync transfers at 256 MiB, file responses stream from disk, and image/cache loads reject oversized local or inline payloads before allocation. Remote-sync session identifiers are URL-encoded, temporary files are removed after failed bounded transfers, and diagnostic logging redacts provider credentials, tokens, MACs, cookies and authorization values. The existing SSRF, path-traversal, XML entity, database identity and JavaFX-thread safeguards were rechecked and retained.

Focused regression coverage was added for request-body limits, streamed file responses, URL redaction and bounded remote-sync copies. The full reactor build remains the release gate; a passing unit test alone does not replace the live RSS/VLC smoke measurement described above.

## Release-readiness follow-up — 17 October 2026

The installed desktop process was measured separately from the packaged DMG process. The old installed process reached approximately 1.5 GiB physical footprint during embedded playback, while its Java heap was only about 185 MiB after collection; native graphics and IOSurface allocations were the dominant retained regions.

The release player no longer wraps vlcj callback buffers in JavaFX `PixelBuffer` objects. It uses one bounded latest-frame copy and one reusable `WritableImage`, with a stop gate and serialized native release. The default packaged heap cap is now 512 MiB, native HiDPI rendering remains enabled, ordinary thumbnail and compact episode decoding is capped at 96×96 with an 8 MiB/128-entry budget, and media artwork has a separate 512×768 profile capped at 16 MiB/12 entries. These are process-budget controls, not substitutes for native footprint measurement.

The final packaged-DMG measurement and full-repository validation are recorded below. A real provider stream should still be included in the final smoke test on each target platform; an idle catalogue-only measurement must not be presented as a VLC playback measurement.

The final macOS aarch64 DMG was launched from a clean mount on 17 October 2026. After startup texture allocation settled, `footprint` reported 840 MiB physical footprint, with a 1,168 MiB transient peak during initial allocation. CPU settled around 2–4% in the sampled idle period. `jcmd GC.heap_info` reported 118 MiB used from a 512 MiB reserved heap. The graphics regions were bounded and the process did not remain in the allocation/Full-GC loop reproduced with the former 160 MiB heap.

## Strict visibility and full-width card/player follow-up

A batch previously checked visibility only when assembled and accepted an unavailable scroll viewport. It now requires real card/viewport dimensions, intersects the scene and every scroll ancestor, and rechecks each queued card on the JavaFX thread immediately before background resolution. Visible summaries are reused through the next programme boundary or 30 minutes; minute progress updates do not resolve sources again. Reattached cards restore their cached summaries.

EPG-enabled cards now group thumbnail/title/account into a compact top-aligned header and place the guide summary beneath it across the entire card width. Minimum EPG card heights were reduced from 210 to 168 pixels (150 for plain channel rows), while EPG-off cards retain their original node structure and sizing.

The current VLC implementation was verified to use an ImageViewVideoSurface, not the older heavyweight Canvas mentioned in AGENTS.md. A Guide control in wide mode/fullscreen can therefore use an inline JavaFX overlay like the existing control-bar hint. Guide availability reuses the player panel's summary, preserves XMLTV metadata in the playing-channel context and loads the full schedule only on explicit open. Closing, resizing or channel changes cancel the overlay request and clear its schedule nodes; no additional Stage/Popup is used for the player overlay.

The final strict-viewport/player-guide reactor run passed 992 tests with a 256 MiB heap (588 core, 88 API, 316 desktop). Additional assertions check full-width thumbnail/plain card geometry, named wide/fullscreen guide availability, XMLTV metadata preservation, no new native windows for the inline panel, X cleanup and fitting within a reduced-height player. Light/dark card, wide popup and inline player previews were rendered and inspected. Hidden or minimized windows no longer qualify as visible card viewports, and results are rechecked before constructing summary nodes. XMLTV source files are still fetched and parsed as whole feeds; the compact cache and per-visible-channel expansion bound their memory cost.

## Persistent table cache follow-up

Current desktop/server cards read bounded Now/Next results from the main SQLite database, with no separate EPG database. Refresh parsing is independent of card display and disabled along with all EPG timers when the feature is off. Programme descriptions are compressed in storage and omitted from card queries. Channel icon bytes use the existing bounded image cache. See `EPG_CACHE.md`; synthetic query measurements are emitted by `EpgPersistentCacheTest` into `core/target/epg-cache-benchmark.txt`. Overall live RSS targets still require a measured app/player session and are not implied by these query timings.
