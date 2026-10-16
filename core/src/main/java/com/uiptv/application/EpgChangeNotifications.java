package com.uiptv.application;

import com.uiptv.db.SQLConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The committed EPG change journal.  It deliberately contains identities and
 * invalidations only; schedules, credentials and catalogues never enter an event.
 */
public final class EpgChangeNotifications {
    private static final int HISTORY_LIMIT = 256;
    private static final String SEPARATOR = "\u001f";
    private static final Set<Consumer<Notification>> LISTENERS = new CopyOnWriteArraySet<>();
    private static final AtomicLong OBSERVED_REVISION = new AtomicLong();
    private static volatile Thread watcher;

    public enum Kind { SOURCE_IMPORT, MAPPING, CACHE_CLEARED, DATABASE_RESTORED, SETTINGS }

    public record ProviderIdentity(String accountId, String providerId) { }

    public record Notification(long revision, String databaseGeneration, Kind kind,
                               Set<String> sourceIds, Set<String> guideIds,
                               Set<ProviderIdentity> accountProviders) {
        public boolean affects(String accountId, String providerId) {
            return accountProviders.isEmpty() || accountProviders.contains(new ProviderIdentity(accountId, providerId));
        }
    }

    public record Snapshot(long revision, String databaseGeneration) { }

    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    private EpgChangeNotifications() { }

    public static Snapshot snapshot() {
        try (Connection connection = SQLConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT revision,database_generation FROM epg_change_state WHERE id=1")) {
            if (!rows.next()) return new Snapshot(0, "");
            return new Snapshot(rows.getLong(1), rows.getString(2));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read the EPG change revision", error);
        }
    }

    /**
     * Register after an initial snapshot.  The second snapshot closes the
     * initial-read race: a revision published while the subscriber was being
     * installed is delivered as a wake-up and can be replayed by the consumer.
     */
    public static Subscription subscribe(long afterRevision, Consumer<Notification> listener) {
        Objects.requireNonNull(listener, "listener");
        Snapshot after = snapshot();
        var delivered = new java.util.concurrent.atomic.AtomicLong(afterRevision);
        Consumer<Notification> guarded = event -> {
            if (event.revision() > delivered.get()
                    && delivered.compareAndSet(delivered.get(), event.revision())) listener.accept(event);
        };
        LISTENERS.add(guarded);
        replay(afterRevision, after.databaseGeneration()).forEach(guarded);
        return () -> LISTENERS.remove(guarded);
    }

    /** Return retained events, or one resync event when the requested revision is gone. */
    public static List<Notification> replay(long afterRevision, String generation) {
        try (Connection connection = SQLConnection.connect();
             var query = connection.prepareStatement(
                     "SELECT revision,database_generation,kind,source_ids,guide_ids,account_provider_ids "
                             + "FROM epg_change_history WHERE revision>? ORDER BY revision")) {
            query.setLong(1, afterRevision);
            List<Notification> events = new ArrayList<>();
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) events.add(read(rows));
            }
            if (events.isEmpty()) return List.of();
            if (events.getFirst().revision() > afterRevision + 1) {
                Notification latest = events.getLast();
                return List.of(new Notification(latest.revision(), latest.databaseGeneration(), Kind.DATABASE_RESTORED,
                        Set.of(), Set.of(), Set.of()));
            }
            if (generation != null && !generation.isBlank() && events.stream().anyMatch(event -> !generation.equals(event.databaseGeneration())))
                return List.of(new Notification(events.getLast().revision(), events.getLast().databaseGeneration(), Kind.DATABASE_RESTORED, Set.of(), Set.of(), Set.of()));
            return List.copyOf(events);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to replay EPG changes", error);
        }
    }

    /** Make a restored database a new logical generation before consumers reconcile it. */
    public static void databaseRestored() {
        Notification notification;
        try (Connection connection = SQLConnection.connect()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement(
                    "UPDATE epg_change_state SET database_generation=? WHERE id=1")) {
                update.setString(1, UUID.randomUUID().toString());
                update.executeUpdate();
            }
            notification = record(connection, Kind.DATABASE_RESTORED, Set.of(), Set.of(), Set.of());
            connection.commit();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to publish database restoration", error);
        }
        publish(notification);
    }

    /** Persist and publish one event after the caller's transaction commits. */
    public static Notification record(Connection connection, Kind kind, Collection<String> sourceIds,
                                      Collection<String> guideIds, Collection<ProviderIdentity> identities)
            throws SQLException {
        Set<String> sources = clean(sourceIds);
        Set<String> guides = clean(guideIds);
        Set<ProviderIdentity> providers = identities == null ? Set.of() : Set.copyOf(identities);
        long revision;
        String generation;
        try (var update = connection.prepareStatement(
                "UPDATE epg_change_state SET revision=revision+1 WHERE id=1")) {
            update.executeUpdate();
        }
        try (var query = connection.prepareStatement(
                "SELECT revision,database_generation FROM epg_change_state WHERE id=1");
             var rows = query.executeQuery()) {
            if (!rows.next()) throw new SQLException("Missing epg_change_state row");
            revision = rows.getLong(1);
            generation = rows.getString(2);
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO epg_change_history(revision,database_generation,kind,source_ids,guide_ids,account_provider_ids) VALUES(?,?,?,?,?,?)")) {
            insert.setLong(1, revision);
            insert.setString(2, generation);
            insert.setString(3, kind.name());
            insert.setString(4, encode(sources));
            insert.setString(5, encode(guides));
            insert.setString(6, encode(providers.stream().map(EpgChangeNotifications::encode).toList()));
            insert.executeUpdate();
        }
        try (var prune = connection.prepareStatement(
                "DELETE FROM epg_change_history WHERE revision <= (SELECT MAX(revision)-? FROM epg_change_history)")) {
            prune.setInt(1, HISTORY_LIMIT);
            prune.executeUpdate();
        }
        Notification notification = new Notification(revision, generation, kind, sources, guides, providers);
        // The database transaction is still owned by the caller; this callback
        // is only reached after the caller commits (see publish below).
        return notification;
    }

    public static void publish(Notification notification) {
        OBSERVED_REVISION.accumulateAndGet(notification.revision(), Math::max);
        LISTENERS.forEach(listener -> {
            try { listener.accept(notification); }
            catch (RuntimeException ignored) { /* one subscriber cannot stop the journal */ }
        });
        writeMarker(notification);
    }

    /** Start the external-builder wake-up watcher. SQLite/WAL files are never watched. */
    public static synchronized void startExternalWatcher() {
        if (watcher != null && watcher.isAlive()) return;
        Snapshot snapshot = snapshot();
        OBSERVED_REVISION.set(snapshot.revision());
        watcher = Thread.ofVirtual().name("epg-change-watcher").start(() -> watch(snapshot.databaseGeneration()));
    }

    public static synchronized void stopExternalWatcher() {
        Thread current = watcher;
        watcher = null;
        if (current == null || current == Thread.currentThread()) return;
        current.interrupt();
        try {
            current.join(1000);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static void watch(String initialGeneration) {
        reconcileExternal(initialGeneration);
        while (!Thread.currentThread().isInterrupted()) {
            Path marker = markerPath();
            try (WatchService service = FileSystems.getDefault().newWatchService()) {
                Path directory = marker.getParent();
                if (directory == null) return;
                Files.createDirectories(directory);
                directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW);
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = service.take();
                    boolean wake = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) wake = true;
                        else if (marker.getFileName().equals(event.context())) wake = true;
                    }
                    if (wake) reconcileExternal(initialGeneration);
                    if (!key.reset()) break;
                }
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception ignored) {
                try { Thread.sleep(1000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private static void reconcileExternal(String knownGeneration) {
        try {
            Snapshot current = snapshot();
            if (!Objects.equals(knownGeneration, current.databaseGeneration())) OBSERVED_REVISION.set(0);
            long after = OBSERVED_REVISION.get();
            for (Notification event : replay(after, current.databaseGeneration())) {
                OBSERVED_REVISION.accumulateAndGet(event.revision(), Math::max);
                publish(event);
            }
            OBSERVED_REVISION.accumulateAndGet(current.revision(), Math::max);
        } catch (RuntimeException ignored) { /* marker is only a wake-up; next recovery pass retries */ }
    }

    private static Path markerPath() {
        Path database = Path.of(SQLConnection.getDatabasePath()).toAbsolutePath();
        return database.resolveSibling(database.getFileName() + ".epg-change");
    }

    private static void writeMarker(Notification notification) {
        try {
            Path marker = markerPath();
            Path directory = marker.getParent();
            if (directory != null) Files.createDirectories(directory);
            Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp-" + Thread.currentThread().threadId());
            Files.writeString(temporary, notification.databaseGeneration() + "\n" + notification.revision() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { /* database history remains the recovery source */ }
    }

    private static Set<String> clean(Collection<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String encode(Collection<String> values) { return String.join(SEPARATOR, values); }
    private static String encode(ProviderIdentity identity) { return identity.accountId() + "\u001e" + identity.providerId(); }

    private static Notification read(ResultSet rows) throws SQLException {
        Set<ProviderIdentity> providers = new LinkedHashSet<>();
        for (String value : split(rows.getString(6))) {
            String[] parts = value.split("\u001e", 2);
            if (parts.length == 2) providers.add(new ProviderIdentity(parts[0], parts[1]));
        }
        return new Notification(rows.getLong(1), rows.getString(2), Kind.valueOf(rows.getString(3)),
                Set.copyOf(split(rows.getString(4))), Set.copyOf(split(rows.getString(5))), Set.copyOf(providers));
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(SEPARATOR, -1));
    }
}
