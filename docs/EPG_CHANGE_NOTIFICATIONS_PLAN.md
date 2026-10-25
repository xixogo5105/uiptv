> Historical documentation: this cached EPG design was retired by migration 0222. See [current EPG behavior](../EPG.md).

# EPG change notifications — implementation notes

Status: implemented. The journal, in-process subscriptions, SQLite reconciliation
and local web SSE endpoint described below are now part of the application. The
filesystem-marker approach was deliberately removed; SQLite remains the source
of truth for cross-process changes.

## Purpose

Stop repeated guide lookups when programme data has not changed. Keep the current
programme panel mounted, update it only when relevant data changes, and preserve
native-provider precedence, explicit mappings and the shared guide cache.

A clock still needs to drive countdowns and progress. Those are local calculations;
they must not trigger database queries, provider requests or HTTP requests.

## 1. Publish changes once, after committed writes

Extend the existing core EPG listener into a typed change notification containing a
monotonic revision, database identity/generation, changed source and guide IDs, and
account/provider identities only for account-specific data. Publish after a source
import or mapping transaction commits, rather than once per channel. Include cache
clear, name-rule changes, overrides, settings and database restoration.

Persist the revision with the transaction. Keep bounded event history; do not keep
whole schedules, credentials or account catalogues in notifications. Subscribers
perform one initial snapshot read, then receive changes after that revision.
Subscription setup must detect a revision change during the initial read.

## 2. Notify desktop and local Android consumers

Desktop panels subscribe only while attached and relevant. Ignore changes to
unrelated guides; update an unmatched channel when the shared alias index changes.
Coalesce bursts, allow one read at a time and apply the newest result in place.
Keep JavaFX work on its application thread and database reads off it.

Android uses the same event semantics through its local observable state. Native
and account-specific schedules remain scoped appropriately. Suspend subscriptions
and local animations while the relevant screen is inactive; reconcile once on
resume or database restoration.

## 3. Reconcile changes from other database writers

After a successful commit, the writer records a monotonic revision and bounded
invalidation details in `epg_change_state` and `epg_change_history`. The desktop
scheduler periodically reads that journal and replays revisions that the process
has not observed. There is no marker file, `WatchService`, or filesystem polling.
Database generation changes produce a resync event so restored or replaced
databases cannot silently reuse stale subscriptions.

## 4. Push to web and connected mobile clients

Expose authenticated server-sent events from the existing local HTTP server,
carrying revisions and relevant invalidations. Clients subscribe once and request
only changed visible guide data, or receive a bounded summary directly. SSE is a
one-way persistent stream and suits this use without a separate cloud push service.

Use bounded queues and event history. On reconnect, replay events after the last
revision; if unavailable or from another database generation, send a resync event
and read one fresh snapshot. Use heartbeats only to maintain/detect connectivity,
not as triggers for EPG queries. Close subscriptions when screens leave the foreground.
Retain existing access controls and avoid broadcasting private account information.

## 5. Separate time passing from data changing

Replace the minute-based guide lookup with a one-shot wake-up at the next cached
programme boundary. Promote the cached next programme locally where possible and
schedule its next boundary. Relevant change notifications refresh that schedule.
Recalculate after sleep, resume, timezone or clock changes.

Use one lightweight visible-screen clock for progress and remaining time. The MAC
verification countdown is derived from its pause deadline; verification job events
announce entering/leaving the pause. Sending one notification per elapsed second
would add work rather than remove it.

Provider/XMLTV collection remains on the configured refresh policy unless a source
actually supports change notifications. UI notifications do not make external IPTV
providers push new guide data.

## Delivery and validation

1. Revisioned core notifications and desktop subscribers: implemented.
2. SQLite journal reconciliation for external database/application changes: implemented.
3. SSE wake-up stream for the local web client: implemented.
4. Cached boundary scheduling and coalesced visible/player reads: implemented.
5. Filesystem marker and watcher: rejected and removed in favour of database revision polling.

Acceptance checks:

- An unchanged visible guide causes no repeated EPG database/HTTP lookups.
- Programme rollover and countdown progress work offline from cached data.
- Unrelated account imports cause no player-panel relayout or guide read.
- An import burst emits one committed revision per transaction, with bounded reads.
- External imports reach subscribed clients through journal reconciliation/SSE without
  watching every DB write.
- Reconnect, missed events, clock changes and restored databases recover correctly,
  with no duplicate subscribers or leaked background tasks.
- Existing provider-identity, precedence, override, shared-cache, schema and narrow
  layout contracts remain valid on desktop, web and Android.

References:

- https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/file/WatchService.html
