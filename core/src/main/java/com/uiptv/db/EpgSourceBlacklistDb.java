package com.uiptv.db;

import java.sql.Connection;
import java.util.List;

/** Persisted URLs which must not be contacted by any EPG importer. */
public final class EpgSourceBlacklistDb {
    public record Entry(String sourceId, String source, long createdAt) { }

    public List<Entry> list() {
        try (Connection connection = SQLConnection.connect();
             var statement = connection.prepareStatement(
                     "SELECT source_id,source,created_at FROM source_blacklist ORDER BY source COLLATE NOCASE");
             var rows = statement.executeQuery()) {
            var result = new java.util.ArrayList<Entry>();
            while (rows.next()) result.add(new Entry(rows.getString(1), rows.getString(2), rows.getLong(3)));
            return List.copyOf(result);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read the EPG source blacklist", error);
        }
    }

    public boolean contains(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) return false;
        try (Connection connection = SQLConnection.connect();
             var statement = connection.prepareStatement("SELECT 1 FROM source_blacklist WHERE source_id=?")) {
            statement.setString(1, sourceId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to check the EPG source blacklist", error);
        }
    }

    public void add(String source) {
        String normalized = source == null ? "" : source.strip();
        if (normalized.isBlank()) throw new IllegalArgumentException("EPG source is blank");
        String id = com.uiptv.application.EpgCacheApplicationService.sourceId(normalized);
        try (Connection connection = SQLConnection.connect();
             var statement = connection.prepareStatement(
                     "INSERT INTO source_blacklist(source_id,source,created_at) VALUES(?,?,?) "
                             + "ON CONFLICT(source_id) DO UPDATE SET source=excluded.source")) {
            statement.setString(1, id);
            statement.setString(2, normalized);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to add the EPG source blacklist entry", error);
        }
    }

    public void remove(String sourceId) {
        try (Connection connection = SQLConnection.connect();
             var statement = connection.prepareStatement("DELETE FROM source_blacklist WHERE source_id=?")) {
            statement.setString(1, sourceId);
            statement.executeUpdate();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to remove the EPG source blacklist entry", error);
        }
    }
}
