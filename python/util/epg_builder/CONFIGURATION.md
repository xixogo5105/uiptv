# UIPTV EPG Builder: config.toml reference

This describes the implemented configuration, including settings that are currently inactive. Start with the utility [README](README.md) for installation and native executable locations. The shared design discussion describes a larger future system; configuration cannot enable adapters that have not been implemented.

## Is a file required?

You do not have to pass `--config`. The CLI normally looks for:

```text
~/.uiptv/epg-builder/config.toml
```

However, guide sources are not automatically discovered today. A useful build therefore needs a configuration containing at least one enabled source with usable XMLTV schedules.

- `uiptv-epg build` reads the default file. If it is missing, the builder creates one with **disabled example sources**; that file must be configured before a build can succeed.
- `uiptv-epg init` explicitly creates that default file. `init --output /absolute/path/config.toml` creates a file elsewhere. It refuses to overwrite an existing file.
- `scan`, `sources list`, `sources check`, `report` and database `status` expect their selected configuration file to already exist. They do not automatically create it.
- `status --bundle /path/bundle.json` can inspect a saved bundle without configuration or database access.
- An explicit `--config /absolute/path/config.toml` selects a different file. The utility does **not** search the current directory, executable directory, Downloads or arbitrary database folders for configuration.
- `~` means the OS user's home directory, not the location of the executable. A scheduler should run under the same OS user as UIPTV.

There is no builder database-path setting or `--database` override. Java uses the same database resolver as UIPTV: `db.path` in UIPTV's own `uiptv.ini`, otherwise the standard per-user database location. A legacy `[database]` table in a builder config has no effect. The bridge refuses to create a database or migrate an old one.

## A minimal working structure

Replace the example URL with a verified **XMLTV file**, not a website directory or GitHub repository page.

```toml
working_dir = "~/.uiptv/epg-builder"

[[sources]]
id = "my-guide"
name = "My guide"
type = "xmltv_url"
url = "https://example.org/guide.xml.gz"
enabled = true
priority = 100
refresh_hours = 12
```

All other fields have defaults. The URL above illustrates syntax; it is not a real source to enable.

TOML top-level fields must precede table headings. A field after `[collector]` belongs to that table until another table begins. Each `[[sources]]` starts another source; do not place global settings underneath it.

Use absolute paths for scheduled jobs. `working_dir`, `bridge.bridge_jar` and `lock.lock_file` support `~` expansion. Local source URLs and a custom Java executable should use full paths; local source paths are otherwise interpreted relative to the process working directory.

## Top-level fields

| Field | Default | Effect |
|---|---|---|
| `working_dir` | `~/.uiptv/epg-builder` | Rebuildable downloads, source health, reports and default process lock. Created when the runner starts. |
| `dry_run` | `true` | Runner API default. The CLI `build` command independently defaults to dry-run; use `--apply` to publish. Setting this field to false does not make a bare CLI `build` write data. |
| `force_refresh` | `false` | Runner API default. In CLI use, `--force` controls forced download refresh. |
| `include_sources` | `[]` | Limit source selection to listed source IDs; does not enable a disabled source. |
| `exclude_sources` | `[]` | Exclude listed source IDs. |
| `region_filter` | `[]` | Source selection by advertised country codes. `[]` leaves all configured regions eligible. |
| `language_filter` | `[]` | Source selection by advertised language codes. `[]` leaves all configured languages eligible. |

The corresponding CLI flags are repeatable: `--include SOURCE`, `--exclude SOURCE`, `--region GB`, `--language en`. CLI filter values override configured lists when supplied. Empty lists mean no restriction, not no channels. These are **source filters**, not a guarantee that only channels from those countries will be exported. Source entries with no country/language metadata remain eligible.

## Source entries: [[sources]]

| Field | Default | Effect |
|---|---|---|
| `id` | Required | Unique stable local identifier. Used in cached download names, health and published source identities. Do not rename casually. |
| `name` | Required | Human-readable label. |
| `type` | Required | `xmltv_url`, `published_aggregate` or `local_xmltv` for implemented collectors. |
| `url` | `""` | Direct HTTP(S) XMLTV asset, or a local XMLTV filename for `local_xmltv`. |
| `enabled` | `true` | Whether the source is eligible to run. Disabled sources are skipped. |
| `priority` | `100` | Lower wins. Imported public priority is `10000 + priority`, below existing native/account/configured sources. Use integers from 0 to 1,000,000. |
| `refresh_hours` | `6` | Minimum time between HTTP downloads. A scheduler can run more frequently and reuse cached bytes. Local files are read each run. |
| `max_size_mb` | `200` | Positive download/local-file size limit in MiB. This does not raise parser/decompression limits. |
| `timeout_seconds` | `60` | Positive HTTP timeout and streamed-body time budget. It is separate from the Java bridge timeout. |
| `countries` | `[]` | Source coverage metadata, used in source filtering and candidate eligibility when a channel's country is known. Use `GB`, `PK`, `IN`, `US`, `AU`, `NZ`, `CA`, etc. |
| `languages` | `[]` | Source language metadata; leave empty for a multilingual source unless the list is reliable. |
| `headers` | `{}` | Optional HTTP headers. Keep authentication headers private. |
| `decompress` | `true` | Currently inactive as a toggle: gzip/XZ are detected automatically from content regardless of this field. Omit it. |
| `extra` | `{}` | Reserved adapter metadata; current collectors do not act on it. |

Example custom request headers:

```toml
[[sources]]
id = "private-guide"
name = "Private guide"
type = "xmltv_url"
url = "https://example.org/private/guide.xml.gz"
enabled = false
headers = { Authorization = "Bearer REPLACE_LOCALLY" }
```

This is a disabled syntax example, not an actual provider endpoint.

`published_aggregate` is currently the same direct HTTP XMLTV retrieval path as `xmltv_url`; it does not crawl a project or discover release files. `iptv_org_site` returns an explicit unsupported error. If you separately run iptv-org's grabber, its generated XMLTV can be consumed as `local_xmltv`.

A successful HTTP transport is not proof of usable schedules. A response can contain empty XML, expired data or unsupported XML. The build uses Java's actual parser and validates the resulting bundle before importing.

## Matching: [matching]

| Field | Default | Effect |
|---|---|---|
| `min_confidence` | `0.85` | Minimum automatic match score, from 0 to 1. |
| `margin_threshold` | `0.10` | Minimum lead over a competing guide ID, from 0 to 1. Ambiguous matches stay unmatched. |
| `enable_fuzzy` | `true` | Last-resort name-similarity matching. Disabling it is useful for a conservative first run and large catalogues. |
| `quality_aliases` | `true` | Generate candidates ignoring quality tokens while preserving meaningful channel numbers, timeshift and region tokens. |
| `exact_id_first` | `true` | Include exact guide-identifier candidates. Keep enabled. |
| `canonical_alias_first` | `true` | Reserved; there is currently no global canonical alias database integration. |

Current scores are 1.00 for exact identifiers, 0.90 for exact normalized names, 0.88 for alternate display names, 0.80 for a quality-neutral alias and 0.78 for generated quality variants. Therefore the default 0.85 threshold rejects **quality-only** matches even if `quality_aliases` is true. Setting 0.80 with fuzzy matching disabled allows unique quality-neutral candidates without enabling general fuzzy guessing.

Manual overrides are honored separately; an unavailable manual target cannot fall through to an automatic match. Configured source country/language metadata can exclude incompatible candidates, but channel locale extraction is incomplete. Inspect the report for uncertain results.

## HTTP: [collector]

| Field | Default | Status |
|---|---|---|
| `user_agent` | `UIPTV-EPG-Builder/0.1.0` | Sent on HTTP requests. |
| `verify_ssl` | `true` | TLS certificate verification; keep true. |
| `follow_redirects` | `true` | Permit HTTP redirects. Useful for published release-asset links. |
| `max_redirects` | `5` | Requests session redirect bound. |
| `max_concurrent` | `3` | Reserved: collection currently runs sequentially. |
| `max_retries` | `3` | Reserved: configurable retries are not implemented. |
| `retry_backoff_seconds` | `2.0` | Reserved: configurable retry backoff is not implemented. |

Inactive settings are accepted for compatibility, but changing them has no effect. A later scheduled run is currently the retry mechanism.

## Java: [bridge]

Native bundles need no Java/bridge settings: they locate their adjacent Java runtime and shaded JAR automatically. These fields are useful when running from source or pinning a personal installation to absolute paths.

| Field | Default | Effect |
|---|---|---|
| `java_executable` | `"java"` | System Java for source installs; the bundled Java executable for a native install using this default. A nondefault value selects that explicit executable. |
| `bridge_jar` | `""` | Explicit shaded bridge JAR; blank invokes bundle/checkout discovery. |
| `classpath` | `""` | Alternative Java classpath when no explicit JAR is selected. |
| `timeout_seconds` | `120` | Maximum duration of each bridge subprocess, not the entire build. Large inventories may need longer. |
| `jvm_args` | `[]` | Java argument array, e.g. `["-Xmx1536m"]`. Passed as arguments, not shell text. |

A Java heap cap applies to each Java process only; it does not cap Python memory. Moving an installation with hard-coded paths requires updating those paths. No bridge setting changes which application database is selected.

## Locking: [lock]

`lock_file` defaults to a lock inside `working_dir` when blank. Use an absolute path to share a lock across personal scheduled/manual runs. `init` normally writes an explicit `epg-builder.lock` path in its generated config; a runner with no explicit path uses `builder.lock` in the working directory.

Lock acquisition is immediate. An overlapping process fails without importing. `timeout_seconds` (default 300) is reserved and does not cause a wait. SQLite separately serializes publication with application writes.

## Logging: [logging]

The accepted fields `level`, `file`, `max_size_mb`, `backup_count` and `redact_secrets` are currently reserved. They do not configure a log file or rotation. Omit this section unless compatibility requires it. Capture output through your scheduler or redirect stdout/stderr explicitly. Build `--json` writes its result to stdout and progress to stderr.

## What configuration cannot change yet

- Automatic website/source discovery, iptv-org invocation and paid adapters are not implemented.
- `source-registry.json` is reference material; it is not automatically imported into the TOML configuration.
- Decompressed XMLTV is capped at 64 MiB, and the Java parser has its own retained-memory/programme limits. Increasing `max_size_mb` does not lift those limits.
- Bundles are limited to 128 MiB and 1,000,000 programme entries. There is no configuration setting to raise these limits or partition a large build automatically.
- The retained programme horizon is fixed by Java, rather than a `days` option in TOML.
- A scheduler is external to the utility. Writing `refresh_hours` does not install a background job.
- EPG display must remain enabled in UIPTV. A dedicated external-refresh-owner setting is not implemented; the application can still refresh its own configured sources.
- Unknown configuration fields are generally ignored. A syntactically valid file can therefore contain ineffective misspelled settings. Follow the field names in this reference.

## Preview, publish and schedule

```sh
uiptv-epg build --config /absolute/path/config.toml --dry-run
uiptv-epg report --config /absolute/path/config.toml --json
uiptv-epg build --config /absolute/path/config.toml --apply
```

A successful build saves `last-bundle.json` and `last-matches.json` inside the working directory. `report` reads the last successfully validated report; it is not a live coverage scan. A failed build exits nonzero. Missing, stale or invalid bundles do not replace good published data.

For a six-hour external schedule, invoke the **same** `build --config ... --apply` command every six hours as the UIPTV OS user. `refresh_hours = 12` on a feed means alternate six-hour runs reuse its downloaded bytes. Public feed URLs and coverage can change; recheck rejected sources before enabling them.
