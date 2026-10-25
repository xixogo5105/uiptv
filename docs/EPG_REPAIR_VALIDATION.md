> Historical documentation: this cached EPG design was retired by migration 0222. See [current EPG behavior](../EPG.md).

# EPG repair validation — 23 October 2026

This records the local `dev` working tree against the supplied EPG review and repair plan, including the subsequent user requests for Cache Refresh styling, persistent card expansion and content-driven heights. It is not a remote CI or device-test result.

## Requirements checked

| Requirement | Implementation and evidence |
| --- | --- |
| Atomic source and mapping publication | Manual downloads enter connection-private SQLite temporary tables. Successful feeds, mappings and journal notifications publish in one transaction. Database and production-job tests force mapping insertion failures and verify old guide/state/mappings and notification revisions survive unchanged. |
| Avoid repeated catalogue matching | Account channel mappings are prepared once. A single mapping pass covers the entire staged manual batch; the batch test verifies the processed-channel callback runs once. Downloaded guide objects do not accumulate across sources. |
| Cancellation and failed downloads | Closing unpublished staging discards it. Existing network/parse failure, cancellation, joined-job and restart tests pass. Failed downloads retain last-good rows and remain outside the publication batch. |
| Web grid sizing | Existing fractional tracks align headers and rows. Headless Chrome rendered the real channel-row template structure and stylesheet with fixture content at 320, 390, 520, 760, 1024 and 1440 pixels in both themes. Assertions check overflow, header alignment, Next visibility and info-button separation. These are isolated layout renders, not a live-provider browser walkthrough. |
| Desktop live bookmarks | Existing shared card EPG rendering gives live bookmarks the programme-aware row only in full mode. Virtual card tests cover compact/full modes, actions, trailing indicators, responsive columns and mode rebuilding. No separate guide implementation was added. |
| Refresh API exposure | The unused refresh route is absent from server registration. Manual refresh remains a desktop operation. Hostname labels omit URL credentials/query strings and parent paths; the desktop-only filename is deliberately shown to distinguish sources, following the later user request. |
| Truthful outcomes | Tests verify `NO_SOURCES`, zero-match warnings, exact matches, preparation progress and native-provider scope. Download completion is distinct from mapping/publication completion. |
| Clock and lifecycle | Fixed-clock card progress tests and joined-job/cancellation/restart tests pass. Terminal completion time is volatile. |
| Popup layout and interaction | Stable cards preserve manual expansion/collapse and remain open after processing. The header contains source identity and final/current status; stage names and wrapping details are separate body columns. Saving/Saved updates share one row. Progress and cards grow with content rather than fixed heights. The summary uses a wrapping horizontal layout. The window is nonmodal. |
| Popup size | The later request to copy the maximized Cache Refresh window supersedes the original compact-size suggestion. |
| EPG settings parity | Existing Java, web and mobile shared tests cover disabled/full/playback-only gating. Mobile's own enable setting remains independent. |
| Builder removal | Repository search found no surviving removed-builder runtime/build references. Shared XMLTV/cache functionality remains. |

## Local checks

- `./mvnw -pl javafx-app -am test`: **passed**, core 764, API 117, lightweight 1, desktop 351 — **1,233 tests**, zero failures/errors/skips.
- `node --test api-server/src/test/javascript/epg-gating.test.cjs`: **passed**, 14 tests.
- From `mobile`: `./gradlew :shared:allTests -Puiptv.enableIosTargets=false`: **passed**, 99 Android host tests, zero failures/errors/skips.
- Popup layout regressions: four tests, including full popup content at widths 640/1000/1368, height 500 and text sizes 12/16, in both themes. Unattached scenes only; no test opens a stage, popup, application or terminal window.
- Web layout renders: all 12 width/theme combinations passed. Screenshots are under `api-server/target/epg-layout-*.png`. The temporary fixture runner is `/private/tmp/uiptv-epg-layout-check.cjs` and uses the existing local Playwright installation and headless Chrome.
- `./mvnw -pl javafx-app -am -DskipTests package -Pmac-aarch64`: macOS ARM package built successfully; repeated after the final popup changes.
- `git diff --check`: passed.

Popup previews are under `javafx-app/target/epg-popup-*-font*.png`. The separate source-card snapshots are under `javafx-app/target/epg-source-cards-*.png`.

## Verification limits

No Android device appeared in the current ADB device list, so connected instrumentation and programme-sheet device interactions were not run. Mobile application sources were not changed in this repair pass. Windows/Linux package profiles cannot be validated on this macOS host. GitHub Actions has not run against these uncommitted local changes. No changes were pushed or merged during this repair pass.

Manual publication now waits for the consolidated pass, leaving the previous guide visible until then. This intentionally trades per-feed early visibility for one atomic batch and one catalogue scan. Scheduled refresh keeps its separate first-ready-source incremental behavior; its regression test passes.
