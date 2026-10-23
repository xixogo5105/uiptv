# EPG card performance and reliability repair — 23 October 2026

Implemented on `dev`, starting from clean commit `9917acef`. The initial checkout was `main`; no existing changes were discarded. The work order was used as the implementation brief. No release version, schema, provider identity, source precedence, player implementation or catalogue design was changed.

## Findings and repairs

| Area / owning implementation | Observed defect or reviewed contract | Result |
| --- | --- | --- |
| Desktop `CardEpgSupport.displayPanel` | Missing programmes had been represented inconsistently after the earlier layout fix | The latest card follow-up restores the requested one-hour Now/Next placeholder timeline as view-only presentation; no placeholder is saved to the EPG cache or sent to a provider. Invalid provider timestamps still do not get replaced. |
| Desktop `displayLoading`, shared `clock` | Card loaders instantiated animated indicators; minute ticks rebuilt programme content | Static translated loading text; minute ticks update existing progress controls. Cached summaries survive lookup failure with stale status. |
| Desktop channel/bookmark grids | Recent row experiment had already been reverted | Preserved `ResponsiveCardGrid`, existing sizing, selection and virtualisation. Wrapping programme labels retain tooltips. No EPG-specific single-column mode added. |
| Web `cardProgramme`, time/progress/status helpers and templates | Fabricated programme objects made missing data appear available; bindings assumed non-null programmes | Explicit null absence, safe template guards, quiet translated status, valid active intervals required for progress. Card titles wrap up to two lines. |
| Web visible-card queue and lifecycle | Cooldowns depended on item object identity; settings polling ran every five seconds | Bounded 1,000-query summary/cooldown cache, sequential visible-only requests, programme-boundary deadlines, cancellation guards and background suspension. Mode/context generation checks remain. |
| Web clock and configuration | Progress was coupled to settings polling | One minute-boundary progress timer in full mode while visible; configuration polling once per minute while visible. Timers/listeners/requests cleaned up on unmount. Configuration labels already cache by locale. |
| Mobile `MobileEpgChannel` | Compact no-current text bypassed the shared status helper | Reuses `mobileEpgNowStatus`; rejects invalid timestamps and upstream missing-data titles. Existing list, detail interaction, shared foreground clock and caches remain. |
| `EpgRefreshJobService` and refresh dialog | Successfully staged feeds were called Saved before publication; cancellation text described older incremental semantics | Staging / Staged / Publishing / post-commit Completed; aggregate warning runs say Warnings. Cancellation text accurately describes discarding unpublished work. New labels and corrected cancellation text supplied in every core message bundle. |
| Core persistence and source loaders | Existing staging/atomicity and bounded fetching needed verification | Retained existing architecture. Staging rollback, cancellation, mapping failure, last-good data, duplicate sources, no sources and no matches are regression-tested. Native requests retain shared in-flight futures and a global maximum of two, which also bounds each host to two. Desktop cards read cached summaries rather than calling providers. |
| Mobile repository/cache | Source coalescing and account isolation needed review | Retained Android shared source deferred loads, two guide-load permits, bounded guide caches and account/provider-channel result keys. No new parsing or network calls in rendering. |

## Validation

Automated tests ran without an interactive terminal. JavaFX checks used unattached scenes/nodes; desktop and phone smoke runs were performed separately from the automated tests.

- Baseline `node --test api-server/src/test/javascript/*.test.cjs`: 34/35 passed. The recording asset/cache-version assertion in `playback-routing.test.cjs:67` failed before editing.
- Final `node --test api-server/src/test/javascript/epg-gating.test.cjs`: 19/19 passed.
- Full web suite after changes: 37/38 passed. The same recording asset assertion remains; it was not weakened or changed.
- `node --check api-server/src/main/resources/web/javascript/spa.js`: passed.
- Focused Maven EPG/XMLTV/provider-identity runs passed during implementation.
- Final `./mvnw -pl javafx-app -am package`: all 1,252 Java tests passed (772 core, 117 API, 7 lightweight UI, 356 JavaFX). Packaging then encountered an existing generated `javafx-app/target/dist/UIPTV.app`.
- The older generated app was moved to `/tmp/UIPTV-before-epg-repair-20261023-1459.app`, preserving it. `./mvnw -pl javafx-app -am -DskipTests package` passed on retry, producing the macOS app and DMG after the successful test phase.
- From `mobile/`, `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin`: passed, including 102 shared host tests, debug APK assembly and instrumentation source compilation. Existing compiler/deprecation warnings remain.
- `git diff --check`: passed.

Added behavioural checks cover missing/Now-only/Next-only data, invalid times and provider placeholders, unavailable versus stale results, mode gating, background suppression, staged progress and pre-publication cancellation preserving the previous guide and change revision. Existing mode, provider identity, rollback, duplicate-source and cache tests remain enabled.

The synthetic 1,000-channel web test marks exactly 12 cards visible: exactly 12 requests occur, maximum concurrency is one, rebinding a replacement item reuses its cached summary/cooldown, and a clock tick makes no request. A desktop test binds 1,000 detached cards and checks static loading presentation and a stopped shared clock. Another verifies a minute tick retains the same summary and progress nodes without a source lookup. These are lifecycle/request checks, not live CPU profiling.

The persistent-cache benchmark imports 48,000 programmes for 500 channels and writes query measurements to `core/target/epg-cache-benchmark.txt`. These timings do not measure total application CPU or native memory. Offscreen thumbnail-card rendering was inspected; the current card layout and compact progress remain intact.

## Remaining verification

Live macOS before/after CPU profiling, real-provider request/network measurements, the complete interactive embedded-player resize/wide-mode matrix, Android device memory/scroll/network profiling, Windows/Linux packages and remote CI were not run. The headless tests and offscreen rendering do not establish those live outcomes. The unrelated web recording-asset test remains a known baseline failure. No commit or push was performed.


## Wrapped-card and scrolling follow-up

Comparing the working tree with `9917acef` identified newly wrapped programme labels interacting with an older virtual-grid height-measurement defect. The old measurement trusted already allocated bounds, even after asynchronous EPG data changed the preferred height. It now measures width-dependent preferred content and keeps a consistent minimum virtual row height. Summary publication requests one coalesced measurement; no per-card layout/pulse listener was introduced. EPG body/header/title regions retain their preferred minimum height so wrapped text is not compressed into the time/progress area.

A separate scroll regression demonstrated that moving a ten-card virtual window by two detached all ten cards: `children.setAll` disconnected retained nodes and reran scene handlers. Incremental child reconciliation now detaches only the two departing cards. Reattached cached EPG cards keep their existing text/progress nodes, unchanged cache results do not rebuild summaries, and bookmarks use the existing bounded 160-card detached cache already used by channel browsing. Virtualisation remains enabled.

Before-fix regressions failed for all-ten scene detachments and stale virtual row height. After the repair, the focused card/grid suites passed, followed by `./mvnw -pl javafx-app -am test`: 1,256 Java tests passed (772 core, 117 API, 7 lightweight UI, 360 JavaFX). Added offscreen checks cover wrapped channel/account/Now/Next text in both themes and cached summary node identity on reattachment. This demonstrates reduced scene/summary churn, not a measured live macOS scrolling frame rate or CPU reduction.

The follow-up macOS package rebuild (`./mvnw -pl javafx-app -am -DskipTests package`) passed after preserving the prior generated app at `/tmp/UIPTV-before-scroll-repair-20261023-1551.app`. At that checkpoint, the updated installer was `javafx-app/target/dist/UIPTV-1.0.0.dmg` and had not yet been installed or launched.


## Final responsive presentation adjustment

The later user request supersedes the proposed four-column cap: there is **no column cap**. Only bookmark/channel grids currently displaying full EPG use a 20% wider width range (normal 255–345 becomes 306–414). The existing grid selects columns from available pane width. Tests explicitly verify a wide pane can display more than four cards and that EPG-off sizing is unchanged.

Account subtitles are single-line with ellipsis and a full-text tooltip. Both Now and Next sections remain visible for empty data with translated missing-details text. No programme objects or timestamps are invented. Real provider records reporting missing details keep their supplied intervals and eligible progress. Existing desktop assertions were updated to match this explicit presentation request; absent and invalid-time progress assertions remain.

EPG minimum card height now considers current width-dependent preferred content both at sizing and after summary publication, before waiting for a shared virtual height measurement. Full-EPG card height has a 10% allowance after the user's feedback that the previous 20% left too much empty space. This prevents a wrapped title from being squeezed into an obsolete card allocation. Layout tests use 24 recent-style bookmarks, mixed real/absent guides, 620/980/1450/1900-pixel surfaces and both themes, and assert rendered text stays inside the card padding. Offscreen images were inspected at narrow, player-sized and wide widths. These are headless layout checks, not an interactive smoke test of the installed app.

At that checkpoint, no-guide bookmark/channel cards kept the Now / empty track / Next rhythm without invented times. The later card follow-up below supersedes that presentation for desktop cards only.

Final responsive validation: `./mvnw -pl javafx-app -am test` passed 1,258 tests (772 core, 117 API, 7 lightweight UI, 362 JavaFX). The focused card/grid suites passed 54/54. A fresh `./mvnw -pl javafx-app -am -DskipTests -Duiptv.keep.app.image=true package` completed successfully after preserving the earlier generated app image at `/private/tmp/uiptv-epg-build-backup/UIPTV-20261023-1634.app`. The rebuilt macOS app image was launched and inspected from `javafx-app/target/dist`; its DMG was produced there, and `/Applications/UIPTV.app` was left untouched. The five-column bookmark view showed real current/next progress and the Star Gold Thrills no-guide card with an empty neutral track; a scroll to the bottom retained the expected cards and their text.

The connected Mi Max 2 (Android 7.1.1) built and launched the normal debug app to its empty Bookmarks screen. This fresh package has no imported channels/accounts, so it could not display live EPG cards on the phone. The connected instrumentation run passed all 15 `AndroidEpgTest` cases and 45 of 46 total tests. The remaining failure is outside EPG: `MpvEmbeddedPlayerActivityControlTest.lockButtonTogglesLockState` expected the theme `onSurface` tint `-921355` but got white `-1`. No test was weakened. The Android app was installed with `adb install -r`, which preserves existing package data; the isolated test package avoided uninstalling any existing user app.


## Card source-status and timeline follow-up

The selected-card text “An EPG source could not be loaded” was not a reliable, channel-specific explanation. `CardEpgSupport` could show it either when a mapped source had a saved refresh warning or when any exception in the whole card-summary batch occurred; the latter path swallowed the exception and marked the cards as source-unavailable. A read-only check of the currently configured desktop database found 13 source records with the generic unavailable status and no current channel mappings to those records. The screenshot’s exact triggering exception was not retained, so its underlying operation cannot be reconstructed from the screenshot alone.

The card path no longer queries source-warning state or displays the source error. Summary lookup exceptions retain the previous displayed guide and log only the root exception class, rate-limited to once per minute; no URL or credential-bearing exception text is written. Source refresh outcomes remain available in the refresh workflow.

Per the latest explicit UI request, desktop bookmark/channel cards with no current programme now show `No programme details available` for a one-hour Now interval, a progress bar tied to that view-only interval, and a following one-hour Next placeholder when no real Next programme exists. These display-only times are not stored as programme rows. A shared minute clock updates the progress bar in place without rebuilding the card. The original brief’s no-fabricated-times requirement remains in effect for web/mobile and for invalid real programme timestamps.

The focused card/grid rerun passed 58/58 (`VirtualCardEpgUITest` 14, `ResponsiveCardGridTest` 40, `BookmarkCardTest` 4), including local-hour labels, the one-hour Now/Next intervals, hidden source-warning text, invalid-timestamp handling and in-place progress updates. `git -c core.fsmonitor=false diff --check` passed.

The user authorized closing and restarting the local worktree build. The old app image and DMG were preserved at `/private/tmp/uiptv-epg-build-backup/UIPTV-20261023-before-guide-placeholder-rebuild.app` and `/private/tmp/uiptv-epg-build-backup/UIPTV-1.0.0-before-guide-placeholder-rebuild.dmg`. `./mvnw -pl javafx-app -am -DskipTests -Duiptv.keep.app.image=true package` then completed successfully at 18:06 local time and produced a fresh app image plus `javafx-app/target/dist/UIPTV-1.0.0.dmg`. The rebuilt app was launched against the existing desktop database. Visual inspection of the live five-column bookmark grid confirmed that cards with no guide details show the one-hour Now and Next intervals with a shared-clock progress bar, and the misleading source-error message is absent. The live check confirms presentation only; CPU/frame-rate profiling and live-provider network measurements remain outstanding.
