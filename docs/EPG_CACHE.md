# Persistent EPG cache

EPG records live in the existing application SQLite database. There is no second database. The authoritative table definitions are in `0000_baseline.sql`; migration 0211 adds the tables to existing installations. Migration 0210 adds the refresh interval. The mobile schema mirror is generated from that baseline.

## Storage and identity

- `epg_source`: successful import time, next attempt time and warning. Source identifiers are hashes, so provider credentials and guide URLs are not used as identifiers in these rows.
- `epg_programme`: source ID, XMLTV/provider channel ID, UTC start/stop timestamps, title and a compressed description. The composite primary key supports bounded channel/time reads. Descriptions are expanded only when opening programme details.
- `epg_alias`: unambiguous normalized channel names and quality-neutral aliases. It never guesses between conflicting names.
- `epg_channel_mapping`: account ID plus provider channel ID, configuration signature, guide-channel ID, source ID and priority. Rebuilding local catalog rows does not break mappings. Changed sources, credentials, explicit overrides or channel metadata invalidate the old association.
- `epg_channel_icon`: channel icon URL per guide source/channel. Programme artwork is not treated as a channel icon. Relative URLs resolve against the feed location. Image bytes continue through `AsyncImageView` and `ImageCacheManager`, using their existing bounded memory/disk cache.

Existing catalog logos remain authoritative. EPG artwork is a fallback for missing or failed images, not a replacement written into the catalog or bookmarks. Plain-text and EPG-off cards retain their normal presentation.

## Priority and live time calculations

Provider/embedded or M3U-declared EPG comes first, account EPG second, and global feeds last in their configured order. Each time interval retains that order unless a preferred entry is generic (for example Movie, Drama, Programme, TBA or TBC) and a lower-priority source supplies a specific title. If every available source is generic, the generic entry remains visible. Titles such as “Movie: A Real Title” are not reduced to their genre.

Epoch timestamps and dates carrying offsets remain absolute. Provider local dates without offsets use the saved including daylight saving. Invalid preferred timestamp fields fall back to other valid date/end-time fields. Account timezone changes do not change guide cache identities. The native cache key carries a parser version to prevent reuse of records imported under the earlier account-timezone policy.

The cache stores UTC timestamps rather than flags saying “now” or “next”. Read-time interval selection, Now/Next boundaries, timezone display and progress are computed from those timestamps. Future intervals can therefore choose a different source without an import or remapping pass.

## Reads and refresh lifecycle

Cards use read-only connections and indexed batches of up to 128 identities. They return at most two programme summaries per channel and never fetch descriptions, download feeds, parse XML or perform fuzzy searches. Full guides use a bounded read on explicit demand. Desktop card workers operate on visible cards; background updates update those nodes instead of rebuilding the bookmark/channel list, selection or scroll position.

Only an enabled EPG feature owns a scheduled executor. Disabling it immediately shuts down the timer, interrupts its work and invalidates obsolete imports. Lightweight configuration observers remain so enabling EPG can start the service again; they do not poll or fetch guides while it is off.

Settings → Appearance → EPG refresh interval allows 1–24 hours, default 2. Short provider schedules refresh earlier, before coverage ends. A background minute tick checks persisted due times; a fresh source is not downloaded on each tick. Account/catalog changes wake the importer. Native requests are bounded by count and time, give bookmarks priority, then rotate through cached live channels. Catalog browsing does not fetch provider channel catalogs for the importer.

Each completed source is mapped and published immediately, so later slow fallback feeds cannot delay already available summaries. Native bookmark requests precede bulk native rotation, while imported XMLTV records are mapped to all cached channels for that account.

XMLTV imports validate the feed before replacing its rows in one transaction. SQLite WAL keeps existing rows readable during refresh. Failed refreshes keep the last good programmes and use a five-minute retry backoff. Settings → Cache → Refresh EPG clears only EPG cache rows and transient discovery/provider state, invalidates old in-flight imports and queues a fresh background import when enabled. It preserves accounts, bookmarks, catalog rows and explicit guide overrides.

Expired programmes are pruned after a six-hour history window. Imports retain at most seven future days and use the existing XMLTV byte/programme safety limits. Descriptions and database page caches are bounded. Empty or first-time caches cannot display information that has not yet been fetched: channels appear immediately and guide summaries fill in when the background import completes. Later launches read the persisted records immediately.

## Validation

`EpgPersistentCacheTest` covers persistence, UTC boundaries, source priority, generic-title fallback, name ambiguity, account isolation, configuration signatures, source replacement rollback, stale-on-error behavior, clearing during an import, Settings clear/rebuild, refresh intervals, disabled scheduler lifecycle and channel icons.

`EpgCacheUpdatesUITest` verifies that cache revisions preserve existing card nodes and selection. `AsyncImageViewEpgFallbackTest` verifies use of the existing thumbnail cache and preservation of primary images. `ConfigurationUIEpgTest` checks saved interval values, disabled controls and the corresponding help text. Main baseline and cross-platform configuration sync parity remain contract-tested.

The synthetic performance test imports 48,000 programmes for 500 channels, reads visible batches without descriptions, and writes measurements to `core/target/epg-cache-benchmark.txt`. These measurements are query timings, not a claim about total process RAM or a guarantee of provider download speed.
