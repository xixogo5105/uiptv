# EPG change notifications — implementation plan

Status: proposal only. This document does not introduce a notifier, SSE endpoint,
file watcher, or change the current refresh schedule.

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

## 3. Connect the external builder

After a successful commit, atomically replace a small notification marker beside
the database. Watch that marker's directory; do not watch SQLite/WAL writes, which
also contain unrelated catalogue activity. Treat the marker as a wake-up signal,
then validate the committed revision in the database.

Handle watcher overflow, replacement, unavailable directories and duplicate events.
Reconcile once at startup, resume and watcher reconnection so a crash between commit
and marker publication cannot leave an active screen permanently stale. If supported
recovery cannot guarantee this, retain an explicit low-frequency safety check and
measure it; do not claim the design eliminates every possible reconciliation read.

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

1. Add revisioned core notifications and migrate desktop subscribers.
2. Add the builder marker and watcher recovery.
3. Add SSE and migrate web/connected mobile subscribers.
4. Replace repeated schedule reads with cached boundary scheduling.
5. Remove superseded polling after coverage and recovery checks pass.

Acceptance checks:

- An unchanged visible guide causes no repeated EPG database/HTTP lookups.
- Programme rollover and countdown progress work offline from cached data.
- Unrelated account imports cause no player-panel relayout or guide read.
- An import burst emits one committed revision per transaction, with bounded reads.
- External imports reach subscribed clients promptly without watching every DB write.
- Reconnect, missed events, watcher overflow, clock changes and restored databases
  recover correctly, with no duplicate subscribers or leaked background tasks.
- Existing provider-identity, precedence, override, shared-cache, schema and narrow
  layout contracts remain valid on desktop, web and Android.

References:

- https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/file/WatchService.html
