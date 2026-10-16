# UIPTV EPG Builder

A headless guide preparation utility for an **existing** UIPTV database. Python collects configured XMLTV files and ranks channel candidates. Java core exports opaque identities, parses XMLTV, normalizes each source schedule (discarding invalid intervals), resolves programme overlaps, validates bundles and publishes the persistent cache. Neither UI rendering nor this tool reloads provider catalogues.

This is an initial implementation, not the full source-discovery system described in the design prompt. See [the review](../../../docs/EPG_BUILDER_REVIEW.md) for remaining gaps and validation evidence.

## Build and install

Requires Python 3.11+ and Java 25+ (the Java build targets release 25). From the repository root:

```sh
./mvnw -pl epg-bridge -am package -DskipTests
python3 -m venv /tmp/uiptv-epg-venv
/tmp/uiptv-epg-venv/bin/pip install -e 'python/util/epg_builder[dev]'
/tmp/uiptv-epg-venv/bin/uiptv-epg init --output /path/to/config.toml
```

The executable bridge is `epg-bridge/target/epg-bridge-1.0.0.jar`. Its runtime dependencies are shaded into that JAR. Configure its absolute path when running outside this checkout; automatic JAR discovery works only in this source layout.

`init` refuses to overwrite an existing file. Example public sources are disabled: inherited URLs are candidates, not evidence of current health, coverage, licensing or freshness. Configure a verified direct XMLTV endpoint or a local file before building. `source-registry.json` is a reference catalogue; the runner uses `[[sources]]` entries in the TOML configuration.

For all TOML fields, defaults, configuration-file discovery and inactive settings, see [CONFIGURATION.md](CONFIGURATION.md).

## Minimal configuration

Top-level settings must precede TOML table headings:

```toml
working_dir = "/path/to/epg-builder-work"
dry_run = true

[bridge]
bridge_jar = "/path/to/uiptv/epg-bridge/target/epg-bridge-1.0.0.jar"

[[sources]]
id = "my-local-guide"
name = "Local guide"
type = "local_xmltv"
url = "/path/to/guide.xml.gz"
enabled = true
priority = 100
refresh_hours = 6
max_size_mb = 64
timeout_seconds = 60
```

The bridge always uses UIPTV’s own resolver: `db.path` in UIPTV’s `uiptv.ini`, otherwise UIPTV’s standard database location for the current OS user. There is no builder database-path setting or `--database` override. Legacy `[database]` sections are ignored and can be removed. Run the scheduler as the same OS user as UIPTV. The bridge refuses missing files and does not run migrations; open an older database with the current UIPTV application first. The current schema includes migrations through 0218. No second authoritative database is created.

## Commands

```sh
uiptv-epg scan --config /path/to/config.toml --json
uiptv-epg build --config /path/to/config.toml --dry-run
uiptv-epg report --config /path/to/config.toml --json
uiptv-epg status --config /path/to/config.toml --json
uiptv-epg build --config /path/to/config.toml --apply
uiptv-epg sources list --config /path/to/config.toml
uiptv-epg sources check --config /path/to/config.toml
```

Build defaults to dry-run. Both dry-run and apply call the Java validator. The working directory receives `last-bundle.json` and `last-matches.json` after successful validation. A failed build exits nonzero, including with `--json`; progress goes to stderr and stdout contains the JSON result. `programmes_imported` is zero in dry-run.

`report` reads the last successful build's match report, including rejected candidates. `sources check` fetches configured enabled sources and updates download health; it is not a guide coverage audit. `status --bundle FILE` can inspect a saved bundle without opening a database.

For scheduling, use absolute executable/config/JAR paths:

```cron
0 */6 * * * /path/to/venv/bin/uiptv-epg build --apply --config /path/to/config.toml
```

This external builder schedule is independent of UIPTV's in-app full-EPG scheduler. UIPTV's scheduler checks the SQLite cache state at the configurable 1, 2, 3, 5, 10, 15, 30 or 60-minute interval (default 3 minutes); playback-only EPG disables that background importer and fetches guide data only for the item playing in the embedded player.

## Cache and publication contract

- Inventory reads cached live channels and live bookmarks. Account + provider channel ID stays distinct from local row ID. Repeated category rows share one logical target; cached bookmarks annotate that target.
- Signatures come from `EpgCacheApplicationService`, including active database name rules. Python returns them unchanged. Database identity is always revalidated before publication. Account/configuration revisions validate optional channel bindings; shared guide data survives account changes, and stale bindings are discarded. Legacy account-bound bundles still reject stale inventory.
- Database identity binds the canonical file path and filesystem file key. It is stable across ordinary writes and WAL checkpoints; moving/replacing a database requires rebuilding inventory. It is a local guard, not cryptographic authentication.
- Raw provider metadata, portal/stream URLs, credentials and MAC addresses are omitted from exports. Reports still contain local account/channel names and should be kept private.
- Sources publish under `builder:` IDs. Programmes retain separate source provenance; native and user-configured mappings keep precedence. Public priority is `10000 + configured priority`; lower numbers win. Specific titles can replace generic placeholders through the existing Java resolver.
- Entire source updates and mappings commit together. SQLite reserves its writer before the final identity check. Failed sources absent from a bundle retain their prior data; invalid or empty bundles fail before publication; stale legacy account-bound bundles also fail. The in-app refresher preserves current-signature builder mappings.
- Repeated imports are idempotent. No accounts, channels, bookmarks, configuration, overrides or unrelated cache tables are deleted.
- An OS file lock prevents overlapping runs sharing the configured lock file (default: the working directory). Different working directories should configure the same `lock.lock_file` for the same database. SQLite also serializes publications against in-app writes. Lock acquisition fails immediately; `lock.timeout_seconds` is reserved.

Downloads reuse cached bytes while fresh and use conditional HTTP requests when a cached body exists. gzip/XZ decompression is bounded to 64 MiB; Java parsing retains its existing programme/memory limits. Bundle JSON is limited to 128 MiB and 1,000,000 programme entries. The current JSON/subprocess architecture still materializes large inventories and guides in memory; the half-million-channel performance target has not been certified.

## Matching and limitations

Manual overrides cannot fall through to an automatic match. Exact guide IDs precede names; ambiguous candidates remain unmatched. Redundant exact IDs choose source priority without becoming false ambiguity. Region/language metadata on configured sources filters candidates when the channel supplies context. Quality matching preserves timeshifts, channel numbers and region words. Fuzzy matching is a last resort and cannot remove protected numeric/region tokens.

Cross-source schedules are combined only for an independently matched exact/manual ID, not merely an equal source-local ID discovered by name. Name-only matches remain attached to their selected source. A global canonical alias database is not implemented; `canonical_alias_first` is currently reserved. Source/channel locale metadata remains incomplete, so inspect inferred matches before applying.

Implemented source types: `local_xmltv`, `xmltv_url`, and `published_aggregate` as a direct HTTP XMLTV asset. `iptv_org_site` returns an explicit unsupported error. Automatic website discovery, iptv-org/database integration, paid adapters, configurable retry/backoff and parallel collection are not implemented. Reserved collector/logging settings do not imply these features work. Public endpoint health and distribution terms were not reverified in this review. No live database writes or real-device tests were performed.

## Tests

Build the bridge before Python tests: parser and merge tests call the real Java implementation.

```sh
./mvnw -pl epg-bridge -am package -DskipTests
python3 -m pytest python/util/epg_builder/tests
./mvnw -pl javafx-app,epg-bridge -am test
cd mobile && ./gradlew :shared:allTests
```

Tests use temporary databases/configurations and local XMLTV; they do not depend on public feeds. The local pipeline test covers inventory → parse → match → merge → validate → dry-run/apply → repeated apply. Java regressions cover normal cache visibility, source priority, stale signatures, rollback and missing-file rejection.

## Native binaries from Maven

Platform packaging skips the Python utility by default and does not require Python. Explicitly opt in to build both the UIPTV desktop package and standalone EPG utility:

```sh
./mvnw clean package -P mac-aarch64 -Depg.native.skip=false
```

Outputs for version 1.0.0:

- Desktop: `javafx-app/target/dist/UIPTV-1.0.0.dmg`
- Utility: `python/util/epg_builder/target/dist/uiptv-epg-1.0.0-mac-aarch64.tar.gz`

The utility archive contains the `uiptv-epg` executable, its Python libraries, the shaded Java bridge and a Java runtime built with the same JDK as Maven. Extract and keep the entire `uiptv-epg` folder together. Users do not need Python or Java installed, and the bundle works outside the source checkout. Windows archives use `.zip` and the executable is `uiptv-epg.exe`.

Platform profiles: `mac-aarch64`, `mac-x86_64`, `windows-x86_64`, `windows-aarch64`, `linux-x86_64`, `linux-aarch64`. Existing `*-amd64` aliases select x86_64. Build on the corresponding OS/architecture with a matching Python interpreter and JDK; this is not cross-compilation. macOS CI uses separate Intel and ARM runners.

Build prerequisites are Maven's existing JDK 25+ toolchain and Python 3.11+ with venv/pip. Maven creates an isolated build environment under the module's `target/`, installs the pinned PyInstaller build tool and application dependencies, freezes the console executable, builds its Java runtime, and runs an isolated local-file pipeline smoke test with Python/Java removed from `PATH`. Initial builds need access to the Python package index. PyInstaller behaviour follows its [usage documentation](https://www.pyinstaller.org/en/stable/usage.html) and [runtime path documentation](https://pyinstaller.org/en/stable/runtime-information.html).

The executable Python package version remains the utility's `pyproject.toml` version; archive names use the Maven release version. Existing platform CI jobs upload the native utility archive separately from the desktop package. All Maven builds, including platform profiles, skip Python freezing by default. Set `-Depg.native.skip=false` to enable it, or `-Depg.native.skip=true` to explicitly skip it. To select a particular build interpreter, use `-Depg.python=/absolute/path/to/python3`.

Build just the utility and its Java dependencies:

```sh
./mvnw -pl python/util/epg_builder -am clean package -P mac-aarch64 -Depg.native.skip=false
```

Run or schedule the extracted binary as the same OS user as UIPTV:

```sh
/path/to/uiptv-epg/uiptv-epg init
/path/to/uiptv-epg/uiptv-epg build --config /path/to/config.toml --apply
```

UIPTV's database location is still resolved automatically by Java. Set up guide sources in the generated configuration; no database path or bridge path is needed in a native bundle.

### Large inventory exports

The bridge streams cached channels rather than collecting a full JSON catalogue in Java.
The Python adapter spools JSON Lines to a temporary file, then reads channels incrementally.
A completion record verifies the count; a failed or interrupted export cannot proceed to matching.
The regular `export-inventory` JSON command also streams. Java catalogue signatures and
revision semantics remain shared with UIPTV. Bundle validation only retains referenced targets.
Python still retains the final channel inventory, and programme processing uses additional memory.
The adapter enables native access for SQLite on the bundled Java runtime.

### Merge request size

Catalogue channels sharing the same ordered source/guide identities reuse one merged
timeline. The bridge sends complete timelines in batches capped at 16 MiB of UTF-8 JSON,
below Java's 128 MiB request limit. A timeline is never split across batches, preserving
Java overlap/priority resolution. Channels with the same guide ID in unrelated sources
remain separate. Unmatched channels produce no merge requests.

### Account-independent shared guide cache

Successful source imports publish all guide channels within the retention window,
including channels absent from every account. The bundle carries the source display
names, artwork, priority and locale metadata. `epg_shared_source` identifies these
explicitly shared sources; provider-native schedules and private account feeds stay
scoped to their configured identities.

Desktop/web and Android snapshot readers resolve missing account mappings directly
against this shared inventory, without downloading or writing mappings during reads.
Exact XMLTV identities precede unambiguous title and quality aliases. Structured
playlist group prefixes are removed for title matching; recognised country prefixes,
station numbers, language words, regions and +1 shifts remain significant. Manual
overrides never fall through to automatic names, and usable native/account schedules
retain precedence. Adding an account or changing its provider type does not require
reimporting a shared guide.

Existing builder imports lack source names and need one import with this updated
builder to populate the shared inventory. Subsequent account additions reuse it.
Source-only bundles are valid even when no accounts or channel mappings exist.
