# EPG Builder implementation review — 15 October 2026

## Assessment

The original compile and 47 passing tests did not establish import correctness. Review found broken apply arguments and console entry point, duplicated/stale signatures, ineffective validation, non-atomic replacement of application mappings, inverted fetch freshness, an undefined local-file variable, JSON CLI crashes, misleading reports/status, and permissive tests that asserted only that some programme existed.

The reviewed implementation now delegates identities, XMLTV parsing and programme interval resolution to existing Java core code. Bridge CLI wiring remains in `epg-bridge`; database/inventory orchestration is in `EpgBuilderApplicationService`. `EpgCacheDb` reuses its source writer for atomic bundle publication, keeps public source namespaces separate, and preserves native/user mapping priorities. `SQLConnection` has an explicit external mode so the headless entry point cannot initialize the default database before selecting the requested existing file.

## Correctness fixes

- Export core signatures with the name-rule fingerprint; remove copied native/signature calculations.
- Replace database-content hashing with stable local file identity. WAL/cache writes no longer invalidate a bundle merely by changing database bytes.
- Revalidate inventory revision, configuration and target signatures under SQLite's reserved writer lock. Reject missing/wrong database, unsupported bundle version, invalid/expired programmes, duplicate mappings, unknown targets, unavailable guides and conflicting manual overrides.
- Publish programmes and mappings in one transaction, with a forced mapping-failure regression proving rollback of source replacement.
- Retain successful sources independently, so failure of another source cannot overwrite their data with a synthetic merged source. Preserve builder mappings during ordinary application remaps.
- Reuse Java's parser/time rules and overlap resolution instead of the Python copies. Missing stop times follow core inference, rather than inventing one-hour entries. Same-title episodes and gaps remain distinct.
- Centralize Python bridge commands, fix `--database`/`--bundle`, console entry point, JSON output and failure status. Save inspectable bundle/match reports and implement database status.
- Fix first/fresh/304/local fetch paths; add bounded decompression, redirect limits, atomic cached downloads and a process lock. Remove secret-bearing raw metadata from exports and exception URLs from collector errors.
- Deduplicate cached targets and programme work, keep time-shift/region tokens, and refuse to fall through from missing manual overrides.
- Correct Java requirements/JAR names, TOML top-level scope, enabled-source defaults and unsupported-feature claims. Trim unused Python dependencies and ignore generated Python artifacts.

## Remaining scope and risks

This is a tested direct-feed/local-file utility. It does **not** complete the entire aspirational design prompt. Automatic discovery, the iptv-org grabber, canonical database aliases, paid adapters, retry/backoff, per-programme winning-source reports and exhaustive locale metadata remain unfinished. Public source URLs are disabled candidates until verified; prior claims of working public coverage were not reproduced here.

Inventory export now streams cached SQLite rows and JSON records. Python spools subprocess output to a temporary file and constructs channel objects incrementally; a required completion record verifies the channel count. Bundle validation retains only referenced targets while computing the same full catalogue revision. Both JSON and JSON Lines CLI exports stream; the materialized Java export remains for small callers/tests. On 2026-10-15, a read-only export of the locally resolved database completed with a 512 MiB Java heap in 33.05 seconds, with 309,772,288 bytes maximum RSS. The rebuilt macOS executable also completed a read-only scan of all 506,175 channels across 77 accounts using a temporary 512 MiB heap setting, with no stderr warnings. Regression checks passed: 644 core Java tests and 73 Python tests. This verifies export, not the full production matching/import pipeline. Windows lock behaviour and real Android-device cache visibility remain unverified. Programme request/response JSON and Python's final inventory still consume memory proportional to their data. Name-only matches need review where locale context is missing.

The current in-app scheduler still downloads its configured sources; this review does not convert the entire application into an external-builder-only runtime. No UI render path was changed to perform network work. Existing name-exclusion/mobile/schema changes were preserved and checked through the Java/shared test suites; the previous catalogue counts in `epg-name-exclusions.md` are historical claims from that implementation, not fresh measurements by this review.

## Verification

See the utility README for reproducible build/test commands. Regression tests exercise the real packaged bridge and an isolated SQLite database through dry-run, apply and repeated apply. Full affected Java reactor tests and mobile shared checks are also run. These tests do not contact public EPG sources and do not modify the user's live catalogue.

### Results from this review

- Packaged Java bridge build: passed.
- Java reactor: **1,055 tests passed** (643 core, 88 API, 324 desktop), zero failures/errors/skips.
- Python: **59 tests passed**, including real Java XMLTV/merge operations and isolated-database pipeline/failed-source preservation.
- Mobile shared `allTests`: passed, with existing results up to date; no connected-device run.
- Editable installation into a temporary virtual environment and installed `uiptv-epg --help`: passed.
- Tracked diff whitespace check: passed.

No commits, pushes, releases or live-database imports were performed.

### Database location follow-up

Removed the builder database-path configuration and CLI override. The bridge now always calls the same `SQLConnection.resolveConfiguredDatabasePath()` resolver used by UIPTV startup, honoring UIPTV's own `uiptv.ini` or standard per-user location without creating a database. Isolated end-to-end tests cover both default and configured UIPTV locations; legacy builder `[database]` settings have no effect. Scheduled runs must use the same OS user as UIPTV.

Follow-up verification: packaged bridge build, all 63 Python tests and 10 focused Java regression tests passed. Both UIPTV default and configured database discovery were exercised, and the removed `--database` argument is rejected.

### Native packaging follow-up

Added the Python utility as a Maven packaging module, ordered after the shaded bridge. Explicit platform profiles build a native console executable, bundle its Python libraries and a linked Java runtime, and emit a platform/version archive. Existing platform CI jobs install a build interpreter and upload the EPG archives; Mac Intel/ARM jobs now use matching runners. Release version staging includes both new module POMs. No release was triggered.

Verified `./mvnw clean package -P mac-aarch64` without skipping tests: all reactor modules succeeded, 1,055 Java tests passed, and both `javafx-app/target/dist/UIPTV-1.0.0.dmg` and `python/util/epg_builder/target/dist/uiptv-epg-1.0.0-mac-aarch64.tar.gz` were generated. All 66 Python tests passed. The frozen executable passed local dry-run/apply/repeated-apply/status smoke tests with Python and Java removed from PATH, including after extracting its archive into a directory with spaces outside the checkout. Windows, Linux and Intel Mac packaging is configured but was not executed on this ARM Mac.

## Source interval validation repair

The 2026-10-15 production dry-run exposed a mismatch: merged schedules discarded invalid
intervals, while the bundle republished the original source schedules. Cached feeds included
515 nonpositive intervals. The bridge XMLTV adapter now applies `EpgSchedules.normalize`
to each source/channel before returning it to Python, preserving source priority and avoiding
a second Python interval implementation. Bundle validation still rejects malformed intervals;
its errors now distinguish source identity, text limits, interval order and time-window failures.
Regression coverage includes parsing a zero-duration programme beside a valid programme,
then Java bundle validation; the Python isolated-database dry-run/apply/repeated-apply test
also includes that malformed XMLTV entry. Checks passed: 646 core Java tests and 74 Python tests.

The full-catalogue rerun also exposed retention-window drift between feed parsing and
validation. Bundle construction now filters each source/guide once at `created_at`, omits
mappings with no future programmes, and Java validates that fixed window before discarding
records that aged out during catalogue revalidation. Bundles older than one day or dated
more than five minutes into the future are rejected. An entry already outside the window
when the bundle was created is still rejected. Regression tests cover the aging boundary
and the distinction between processing delay and originally invalid data.

The rebuilt macOS executable then completed the exact personal-config `build --dry-run`
command successfully in 110.8 seconds against 506,175 channels and seven cached feeds.
It matched 5,417 channels; the validated publication bundle contained 5,163 mappings,
68,144 unique source/guide programmes and six contributing sources, with no warnings.
The larger merged count (560,995) repeats guide schedules across account/channel targets;
bundle programmes are published once per source/guide. Channels without future data are
omitted from mappings. No production import was performed.

## Large merge request repair

With ten feeds and 7,352 matched catalogue channels, the old merger serialized source
schedules once per channel, exceeding the 128 MiB Java request limit. It now deduplicates
by ordered source/guide identities, merges each unique selection once, and assigns the
result back to account/channel targets. The Python adapter additionally batches complete
timelines at 16 MiB of actual UTF-8 JSON, keeping the Java limit unchanged. Regression
tests cover repeated account targets, source-local guide IDs, multibyte payload accounting,
request ordering and batch boundaries, alongside existing Java interval resolver tests.

Verification on 16 October 2026: all 79 Python tests passed and the native package smoke
test passed. The installed copy used by `/Volumes/backup/epg.sh` was backed up and replaced.
Its ten-source production-config dry-run completed in 92.5 seconds: 506,175 inventory
channels, 20,271 matched channels, 19,645 validated mappings and 95,511 source/guide
programmes across eight contributing sources, with no warnings. The repeated-target merged
count was 1,537,560; deduplication kept bridge requests within the existing limits.
No production import was performed.
