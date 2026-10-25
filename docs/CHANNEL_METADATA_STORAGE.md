# Channel metadata imports and measured storage

Implemented and measured on 2026-10-25 against main at `7c94018f`.

## Result and scope

Future Stalker and Xtreme imports store compact, allow-listed JSON instead of a copy of the whole
provider row. Playback commands, artwork, DRM, identifiers and all other dedicated database columns
are unchanged. M3U metadata remains opaque and complete. No schema, migration history, startup
cleanup, retention setting, account set or catalogue identity was changed by this work.

The installed database was opened read-only to produce a disposable fixture. No existing rows were
rewritten and no clear, reload, migration or VACUUM was run against the installed database.
Source changes alone do not shrink existing files. The figures below describe newly created test files.

## Shared import policy

`core/src/main/resources/metadata/channel-extra-fields.properties` is the single allow-list.
`ChannelExtraJsonCompactor` in core reads it directly. Gradle generates the matching Android lists
before Kotlin compilation; the Android adapter applies the same rules without depending on core.

| Purpose / consumers | Retained scalar fields |
| --- | --- |
| Desktop and Android live EPG identity | `xmltv_id`, `epg_id`, `tvg-id`, `epg_channel_id` |
| Watching Now / VOD title and description fallbacks | `name`, `o_name`, `description`, `plot`, `overview` |
| Desktop/Android date, rating and duration fallbacks | `releaseDate`, `release_date`, `released`, `year`, `rating_imdb`, `rating`, `duration`, `runtime`, `time` |
| Android browse details | `genre`, `imdbUrl` |
| Logo resolution and episode artwork fallback aliases | `stream_icon`, `cover_big`, `cover`, `screenshot_uri`, `movie_image`, `thumbnail`, `still_path`, `image`, `poster` |
| Stalker category derivation | `category`, `category_name`, `genre_name`, `tv_genre` (and `genre` above) |

Every other provider channel key is discarded, including `cmds`, `cmd`, `cmd_1`, `cmd_2`, `cmd_3`,
`logo`, provider IDs already in columns, parent `series` arrays, `cur_playing`, provider `epg` summaries,
archive flags, balancer/monitoring configuration and unrelated nested diagnostics. Useful image aliases
are retained because they can differ from the authoritative logo or supply a missing-logo fallback.

Retained scalars preserve JSON string/number/boolean types. Missing and null keys are omitted.
Unexpected arrays/objects under scalar keys are discarded. Blank/null/malformed text becomes `{}`;
malformed input produces a non-sensitive, once-per-process diagnostic, never a raw-payload fallback.
Compaction is idempotent and does not mutate the provider object.

The entire nested `m3u` object is copied unchanged, including arbitrary nested custom attributes,
`tvg-shift`, `extvlcopt`, `kodiprop`, user agents, referrers, cookies and unknown playlist options.
Ordinary M3U importers do not pass through the provider filter at all.

Desktop typed `Episode` snapshots remain unchanged. Android provider episode rows additionally retain
the episode identity/title/season/container fields declared in the policy, plus compact `info` artwork,
plot, date, duration, rating and metadata-ID aliases consumed by desktop episode restore. Raw parent
episode arrays are never copied to each expanded channel.

## Source changes

Provider ingestion:

- Added core `util/ChannelExtraJsonCompactor.java` and the shared policy resource.
- `service/ChannelService.java`: Stalker live, VOD and series expansion.
- `util/XtremeApiParser.java`: Xtreme live, VOD and series channel parsing.
- Added Android `cache/ChannelExtraJsonCompactor.kt` and policy generation in `mobile/shared/build.gradle.kts`.
- Android `AndroidStalkerCacheReloader`, `AndroidXtremeCacheReloader` and `AndroidVodSeriesCatalogClient`:
  live/VOD/series/episode provider ingestion.

Ordinary cache maintenance:

- `ConfigurationDb`: successful single-account cache clears now VACUUM after the transaction and
  connection close. All-account clearing already did this. Failures roll back and reach the caller.
- `CacheServiceImpl`: VACUUM after a completed account reload has released its request scope.
- `CategoryCacheRemovalService`: VACUUM after a successful category cache removal closes its connection.
- `CategoryListUI`: category deletion, reads and compaction run in a worker; UI updates return through
  JavaFX task callbacks.
- Android account/all-account/category cache clears and each successful M3U/Stalker/Xtreme refresh
  VACUUM outside their write transactions, on the existing IO/background path.
- Android single-account VOD and episode cache deletion now uses `accountId`, preventing deletion of
  another account's rows when providers reuse category or series IDs.

The explicit request to include VACUUM in normal clear/refresh workflows takes precedence over the
attachment's generic prohibition on adding VACUUM. No maintenance operation was invoked on the
installed database as part of this work, and no compaction was used for the size probe.

## Bookmark findings and prevention

The previous comparison of `Bookmark.channelId` with `Channel.id` was invalid: bookmarks store
provider channel IDs. Bookmarks also retain independent playback snapshots/commands so they can
survive cache expiry, cache clears and provider catalogue changes. Missing cached rows do not justify
deleting user bookmarks.

Corrected checks on the disposable fixture found:

- 1,586 bookmarks; zero reference a missing account.
- Zero `BookmarkOrder` rows reference a missing bookmark.
- 380 bookmarks have a non-empty group assignment with no matching `BookmarkCategory` row:
  373 refer to the missing group `11`; the remaining seven contain legacy/provider/sentinel values.
  These existing assignments were left untouched, as required.
- 68 live bookmarks have no match under account + provider channel ID in the current catalogue;
  all 68 have a saved channel snapshot or playback command. They were preserved.
- 1,568 bookmarks have a channel/VOD/series snapshot. No bookmarks or history were pruned.

Future source safeguards:

- Desktop bookmark deletion removes its order rows in the same transaction, including the
  identity-only deletion overload. A failed delete rolls both operations back.
- Account deletion stops when dependent bookmark removal fails instead of swallowing the failure.
- Android bookmark removal/toggle removes bookmarks, order entries and applicable recent-play history
  transactionally.
- Android account renaming updates bookmark and recent-play account names in the same save transaction.
- Android account deletion also deletes its recent-play history.
- Deleting a bookmark group moves its bookmarks to All and removes the group's ordering entries
  atomically; it preserves provider identity, playback commands and source snapshots.
- New API/Android bookmarks go to All instead of treating a provider category ID as a bookmark-group
  ID. Their provider category is saved separately in `categoryJson`; bookmark playback resolves that
  source snapshot, so changing/deleting a user group does not change the provider playback category.
- Desktop's documented name-change behavior (create a new account and preserve the old account)
  remains unchanged.

## Disposable same-catalogue size measurement

The fixture is a read-only backup stored at
`/private/tmp/uiptv-metadata-measure.R4l19s/source.db`. `ChannelMetadataSizeProbeTest` explicitly opts in
through a fixture property. It creates **two fresh databases** using the captured application table/index schema,
all original rows and 4 KiB pages. One inserts the old raw JSON; the other passes Stalker/Xtreme
channel metadata through the production import helper before insertion. M3U and all unrelated
tables are copied identically. No provider network requests, UPDATEs or VACUUM are involved.

This is an offline replay of the captured import metadata, not a fresh download from providers whose
catalogues might have changed. The probe checks every non-compacted column on every row, all table
counts, both databases' integrity, and the fixture's SHA-256 before/after. Those checks passed.

| File | Actual bytes | Decimal MB (1,000,000 bytes) | MiB (1,048,576 bytes) |
| --- | ---: | ---: | ---: |
| Captured fixture | 797,495,296 | 797.495 | 760.551 |
| Fresh raw rebuild | 797,466,624 | 797.467 | 760.523 |
| Fresh compact rebuild | 177,278,976 | **177.279** | **169.066** |

The result is below the approximately 300 MB target for this exact catalogue. It is not a guarantee
for a future larger catalogue or additional downloaded VOD/episode rows. The final Channel table
occupies 156.000 decimal MB; its two indexes occupy 16.474 MB together.
SQLite's internal planner statistics are not copied into either fresh file; this explains the raw
control's 28,672-byte (0.004%) difference from the fixture. All application tables and indexes are
retained in both files, and the size comparison uses that fresh raw control.

| Cache table | Rows before = after | SUM(length(extraJson)) before | After | Rows containing top-level cmds before → after |
| --- | ---: | ---: | ---: | ---: |
| Category | 7,223 | No extraJson column | Same | — |
| Channel | 481,906 | 515,981,086 | 38,370,050 | 360,427 → 0 |
| VodCategory | 3,562 | 292,849 | 292,849 | 0 → 0 |
| VodChannel | 0 | 0 | 0 | 0 → 0 |
| SeriesCategory | 2,636 | 208,097 | 208,097 | 0 → 0 |
| SeriesChannel | 0 | 0 | 0 | 0 → 0 |
| SeriesEpisode | 0 | 0 | 0 | 0 → 0 |

SQLite `length(TEXT)` counts characters, not UTF-8 bytes. Physical file sizes above are actual bytes.
All 63 accounts remain: 26 Stalker, 7 Xtreme, 29 remote M3U and 1 local M3U. All 43,756 M3U catalogue
rows and their 7,220,928 metadata characters are identical. Bookmarks, history, configuration, watch
state, snapshots, account details and existing migration records are also copied unchanged.

## Verification

Executed successfully:

```sh
./mvnw -pl core -Dtest=ProviderIdentityContractTest test
./mvnw -pl core test
./mvnw test
./mvnw -pl core -Dtest=BookmarkIdentityLifecycleTest,BookmarkCrudFlowTest,BookmarkServiceCoverageTest,PlayerRequestResolverTest,BookmarkApplicationServiceTest test
./mvnw -pl javafx-app -am -Dtest=CategoryListUITest,CategoryCacheRemovalServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl core -Dtest=ChannelMetadataSizeProbeTest \
  -Duiptv.metadata.fixture=/private/tmp/uiptv-metadata-measure.R4l19s/source.db \
  -Duiptv.metadata.output=/private/tmp/uiptv-metadata-measure.R4l19s/results test
cd mobile
GRADLE_USER_HOME=/Volumes/backup/.gradle ./gradlew :shared:allTests \
  :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin
```

The final Maven reactor reported core 680 tests (one skipped opt-in size probe), API 116, lightweight
UI 7 and desktop UI/player 350, with no failures or errors. The size probe was separately enabled
and passed. Provider parsing/reload tests cover discarded raw metadata, unchanged commands, live
EPG IDs, VOD fallbacks, M3U custom options, preserved bookmark snapshots, transactional rollback and
cache compaction. `git diff --check` also passed.

`ChannelMetadataPolicyTest` was also executed successfully as two JVM tests against the compiled
Android adapter and the repository's existing JSON/JUnit libraries. New Android database lifecycle
tests compile, but were **not run on a device**. No app installation or device UI test was performed.

The focused bookmark and category checks were rerun after the final transactional-delete and
background callback refinements.

Logs: `/tmp/uiptv-metadata-identity.log`, `/tmp/uiptv-metadata-core.log`,
`/tmp/uiptv-metadata-reactor.log`, `/tmp/uiptv-metadata-size.log`, `/tmp/uiptv-metadata-android.log`.
The fresh measurement files remain under `/private/tmp/uiptv-metadata-measure.R4l19s/results/`.
