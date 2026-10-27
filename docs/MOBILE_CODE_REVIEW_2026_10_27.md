# Mobile code review — 27 October 2026

## Scope

Reviewed Android entry points and players, guide repository/parser/presentation,
thumbnail loading, catalogue repository boundaries, cache-job observation,
preferences, backup/restore and remote snapshot replacement. This is a targeted
source review with regression checks, not an exhaustive audit of every provider
or every screen. The earlier embedded playback fix remains in the working tree.

The current EPG architecture remains account-scoped and demand-driven. Catalogue
rows do not fetch programmes: the legacy `epgThumbnail` function returns null.
Explicit Guide actions and player guide loading remain the requesting paths.
No guide cache, saved matcher, importer, scheduler or schema change was added.

## Findings addressed

| Severity | Finding | Change |
| --- | --- | --- |
| High | Local backup restore overwrote the live SQLite file in place, unlike the safer desktop-sync path. A failed copy or validation could leave no usable live database. | Restore now delegates to `AndroidSQLiteSnapshotSyncApplier`, including staging in the database directory, migration/schema/integrity checks, preservation of the previous database, and rollback on replacement/validation failure. Restore extraction uses a unique directory and cleans it afterward. |
| Medium | XMLTV cancellation checked thread interruption, which does not reflect ordinary coroutine cancellation. Parsing buffered XML could continue after closing a guide or switching channels. Native fallback/discovery also swallowed or failed to check cancellation. | The parser checks the requesting coroutine between tokens and inside text elements. A bounded input wrapper checks cancellation before/after reads. Native calls use interruptible execution and check cancellation before retries; discovery preserves cancellation. HTTP connection acquisition failures now disconnect the connection. |
| Medium | Embedded-player EPG settings loading could complete after `onStop` and start a new guide request. Its guide database helper was never closed. Android Media could close its guide database while a cancelled reader was still using it. | Cancel the settings-load job on stop, guard result publication, reset enable state before restart, and release guide helpers only after the owning request scope finishes. |
| Medium | Android Media retained play intent after its Activity stopped, and retry callbacks could prepare/play another stream in the background. | Pause on stop, remove pending retry callbacks, guard retries by screen lifecycle, restore the prior play intent on return, and clear/restore the active-playback indicator with actual playback. This preserves explicitly paused playback. |
| Medium | Concurrent thumbnail requests used `ConcurrentHashMap.getOrPut` with an eagerly launched job. Its initializer can run more than once, and immediate completion could leave a completed job registered. | Register lazy jobs under a lock before starting them, share each active request, and remove only the completing job's own registration. |
| Medium | Failed-logo backoff was checked only by the memory lookup, so subsequent `fetchAsync` calls could still redownload a failing URL. Connections were not explicitly disconnected. | Enforce backoff before fetching and after acquiring the concurrency permit; disconnect on all completion/error paths and propagate cancellation. |
| Medium | Provider images were decoded at their full dimensions, independent of the memory cache limit. A single poster could allocate far more memory than its thumbnail needed. | Bound downloaded encoded data to 8 MiB and decode both downloaded/disk images using a power-of-two sample targeting a 512-pixel maximum dimension. |
| Medium | Clearing thumbnails discarded/cancelled the singleton while existing Compose views still retained it, leaving their future requests attached to a dead scope. Disk cleanup ran on the settings UI coroutine. | Clear the retained instance without disabling it; cancel registered fetch jobs and perform settings-triggered disk cleanup off the UI thread. Disk writes stay in the requesting job and publish via temporary files. |
| Medium | Both in-app players opened/wrote SQLite watch-history records directly in playback callbacks or teardown. | Queue VOD/series history writes on a shared serial background executor using application context. Live playback avoids opening a history helper entirely. |
| Low | EPG setting-save cancellation could be converted into an error message, and a successful retry retained the previous error text. | Preserve cancellation and clear the message after a successful save. |

No new user-visible labels or settings were introduced. Guide rendering/layout,
provider/local identity translation, schema declarations, migrations and the
embedded decoder/audio configuration are unchanged by this review.

## Validation

- Core provider identity contract: **4 tests passed**.
- Shared mobile host tests: **109 passed**, including three new bounded-stream
  cancellation/size/deadline tests.
- Android app host tests: **17 passed**, including five new tests for concurrent
  request sharing, immediate completion, cancellation/replacement races and
  image decoding/download limits.
- Huawei Android 8, **separate `com.uiptv.mobile.epgtest` package**: **28 tests
  passed**. These were non-UI tests: 24 database compatibility cases, two backup
  cases (whole-file restore and rejection of an invalid backup without account
  loss), and two EPG cases (fresh local-file requests/no legacy cache plus
  cancellation inside buffered XML text).
- Release APK build and device-test Kotlin compilation passed.
- No device UI smoke suite was launched; player background/resume interaction
  and thumbnail scrolling still need a dedicated UI smoke session. Building an
  APK does not establish those interactions on every device.
- The isolated test app did not use the production app's database. The new
  release APK was built; it was not installed over the user's running player.
- No commit or push was performed.

## Remaining concerns and recommended follow-up

1. **Coordinate whole-database maintenance.** Backup, restore, desktop sync,
   cache workers and player history can use different database helpers. Snapshot
   replacement protects the previous file, but there is no demonstrated
   application-wide maintenance lock preventing every concurrent writer/reader.
   A shared maintenance boundary and interrupted-operation tests are needed
   before claiming safe concurrent backup/restore with active refresh jobs.
2. **Align guide source policy explicitly.** `schedule` tries provider-native
   data first and falls back to XMLTV; `scheduleProgressively` runs both and can
   finish with XMLTV. That can make player and explicit-Guide results differ.
   Define the expected source priority and exercise conflicting-source fixtures
   before changing the selected programme behavior. This review intentionally
   preserves the current presentation/source policy.
3. **Interrupt blocked network reads consistently.** Cancellation now stops
   parsing/retries as soon as execution reaches a check. HTTP calls that ignore
   thread interruption may still remain blocked until their configured socket
   timeout. Moving guide/provider transports to a common cancellable HTTP layer
   would give stronger immediate cancellation guarantees.
4. **Bound thumbnail disk retention.** Existing files expire when requested;
   the thumbnail directory still has no total-size quota or proactive eviction
   of never-requested old entries. The negative-response map likewise has no
   global entry cap. These deserve long-scroll/storage-pressure tests.
5. **Reduce UI ownership complexity.** `UiptvMobileApp.kt` is a large screen and
   state container. Screen-scoped state/use cases and common cancellation/error
   handling would reduce regressions, but should be a measured refactor with
   behavioral coverage rather than another broad rewrite during playback work.
6. **Remove dead EPG-thumbnail scaffolding separately.** The no-op hook does not
   perform requests and was left intact to keep this change focused. Its future
   removal should not remove the separate metadata-thumbnail fallback loaders.
7. **Legacy strings remain.** Existing Android player/settings text includes
   literal English labels. This review adds none; a translation sweep should
   use the current message generator and all supported bundles.

The successful tests establish the covered cases, not a guarantee that all
providers, lifecycle transitions or resource-pressure conditions are correct.
