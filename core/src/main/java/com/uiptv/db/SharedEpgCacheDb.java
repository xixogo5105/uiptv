package com.uiptv.db;

import com.uiptv.application.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Account-independent, read-only resolution of explicitly published shared guides. */
// Query limits are fixed application constants; all user values remain prepared parameters.
@SuppressWarnings("java:S2077")
public final class SharedEpgCacheDb {
    public record Query(String guideId, String name, boolean explicit) { }
    public record Result(List<EpgProgramme> programmes, String icon) { }
    public enum ResolutionStatus { MATCHED, NO_SOURCE_COVERAGE, MATCHING_FAILED }
    public record ResolutionDiagnostic(ResolutionStatus status, String guideId, String normalizedName,
                                      int activeSources) { }
    private record Source(String id, int priority, Set<String> countries, Set<String> languages,
                          Map<String,Set<String>> guideCountries, Map<String,String> exact,
                          Map<String,String> display, Map<String,String> quality,
                          Map<String,String> identifiers, Set<String> ids) {
        Set<String> countries(String guide) {
            return guideCountries.getOrDefault(guide, countries);
        }
    }
    private record Snapshot(String version, List<Source> sources) { }
    private record Candidate(Source source, String guide, int rank, int regionRank, Set<String> countries) { }
    private record SourceMetadata(String id, int priority, String countries, String languages, long refreshedAt) { }
    private record IndexMetadata(String version, List<SourceMetadata> sources) { }
    private static final AtomicReference<Snapshot> CACHED = new AtomicReference<>();

    static void invalidate() { CACHED.set(null); }

    private Connection open() throws SQLException {
        var c = DriverManager.getConnection("jdbc:sqlite:" + EpgCacheDb.path().toUri() + "?mode=ro");
        try (var s = c.createStatement()) { s.execute("PRAGMA query_only=ON"); s.execute("PRAGMA busy_timeout=3000"); }
        return c;
    }
    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::strip).filter(token -> !token.isBlank())
                .map(token -> token.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static Snapshot index(Connection c, long now) throws SQLException {
        var rules = new EpgNameRulesDb().read(c);
        IndexMetadata metadata = readMetadata(c, rules, now);
        Snapshot previous = CACHED.get();
        if (previous != null && previous.version().equals(metadata.version())) return previous;
        List<Source> sources = new ArrayList<>();
        try (var statement = c.prepareStatement("SELECT name,guide_channel_id FROM epg_alias WHERE source_id=? AND kind=0 AND EXISTS(SELECT 1 FROM epg_programme p WHERE p.source_id=epg_alias.source_id AND p.guide_channel_id=epg_alias.guide_channel_id AND p.stops_at>? )")) {
            for (SourceMetadata source : metadata.sources()) sources.add(readSource(statement, source, rules, now));
        }
        Snapshot snapshot = new Snapshot(metadata.version(), List.copyOf(sources));
        CACHED.set(snapshot);
        return snapshot;
    }

    private static IndexMetadata readMetadata(Connection c, EpgNameRules rules, long now) throws SQLException {
        StringBuilder version = new StringBuilder(EpgCacheDb.path().toString()).append('|').append(rules.fingerprint())
                .append('|').append(EpgTerritory.fingerprintValue()).append('|').append(now / 60_000);
        List<SourceMetadata> sources = new ArrayList<>();
        try (var statement = c.createStatement(); var rows = statement.executeQuery(
                "SELECT g.source_id,g.priority,g.countries,g.languages,s.refreshed_at FROM epg_shared_source g JOIN epg_source s ON s.source_id=g.source_id ORDER BY g.priority,g.source_id")) {
            while (rows.next()) {
                SourceMetadata source = new SourceMetadata(rows.getString(1), rows.getInt(2), rows.getString(3),
                        rows.getString(4), rows.getLong(5));
                sources.add(source);
                version.append('|').append(source.id()).append('|').append(source.priority()).append('|')
                        .append(source.countries()).append('|').append(source.languages()).append('|').append(source.refreshedAt());
            }
        }
        return new IndexMetadata(version.toString(), List.copyOf(sources));
    }

    private static Source readSource(PreparedStatement statement, SourceMetadata metadata,
                                     EpgNameRules rules, long now) throws SQLException {
        Map<String,String> names = new HashMap<>();
        statement.setString(1, metadata.id());
        statement.setLong(2, now);
        try (var rows = statement.executeQuery()) {
            while (rows.next()) names.put(rows.getString(1), rows.getString(2));
        }
        Map<String,Set<String>> guideCountries = new HashMap<>();
        names.values().forEach(guide -> guideCountries.put(guide,
                EpgTerritory.sourceCountries(metadata.countries(), metadata.id(), guide)));
        Map<String,String> identifiers = uniqueIdentifiers(names.values());
        return new Source(metadata.id(), metadata.priority(), EpgTerritory.parseCountries(metadata.countries()),
                tokens(metadata.languages()), Map.copyOf(guideCountries), Map.copyOf(names),
                rules.aliases(names, false), rules.aliases(names, true), Map.copyOf(identifiers), Set.copyOf(names.values()));
    }

    private static Map<String,String> uniqueIdentifiers(Collection<String> guides) {
        Map<String,String> identifiers = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        guides.forEach(guide -> {
            String key = XmltvGuide.normalizeIdentifier(guide);
            if (key.isBlank()) return;
            String prior = identifiers.putIfAbsent(key, guide);
            if (prior != null && !prior.equals(guide)) ambiguous.add(key);
        });
        ambiguous.forEach(identifiers::remove);
        return identifiers;
    }
    private Candidate resolve(Query query, Snapshot snapshot, EpgNameRules rules) {
        EpgTerritory.Context context = EpgTerritory.parse(query.name());
        if (!query.explicit() && context.ambiguous()) return null;
        List<String> names = new ArrayList<>();
        names.add(context.name());
        String playlistIndexFree = EpgTerritory.stripLeadingPlaylistIndex(context.name());
        if (!playlistIndexFree.equals(context.name())) names.add(playlistIndexFree);
        Set<String> expectedCountries = context.territories();
        List<Candidate> candidates = new ArrayList<>();
        for (Source source : snapshot.sources()) {
            Candidate candidate = candidateForSource(query, source, names, expectedCountries, rules);
            if (candidate != null) candidates.add(candidate);
        }
        return selectCandidate(candidates, expectedCountries);
    }

    private Candidate candidateForSource(Query query, Source source, List<String> names,
                                         Set<String> expectedCountries, EpgNameRules rules) {
        String guide = query.guideId();
        int rank = 0;
        if (guide == null || guide.isBlank() || !source.ids().contains(guide)) {
            guide = source.identifiers().get(XmltvGuide.normalizeIdentifier(guide == null ? "" : guide));
        }
        if (guide == null || guide.isBlank() || !source.ids().contains(guide)) {
            if (query.explicit()) return null;
            guide = firstNameMatch(names, source, rules);
            rank = 1;
            if (guide == null) {
                guide = firstQualityMatch(names, source, rules);
                rank = 2;
            }
        }
        if (guide == null || guide.isBlank()) return null;
        Set<String> actualCountries = source.countries(guide);
        if (!query.explicit() && EpgTerritory.conflicts(expectedCountries, actualCountries)) return null;
        int regionRank = expectedCountries.isEmpty() == actualCountries.isEmpty() ? 0 : 1;
        return new Candidate(source, guide, rank, regionRank, actualCountries);
    }

    private String firstNameMatch(List<String> names, Source source, EpgNameRules rules) {
        for (String name : names) {
            String guide = source.exact().get(XmltvGuide.normalize(name));
            if (guide == null) guide = source.display().get(rules.display(name));
            if (guide != null) return guide;
        }
        return null;
    }

    private String firstQualityMatch(List<String> names, Source source, EpgNameRules rules) {
        for (String name : names) {
            String guide = source.quality().get(rules.quality(name));
            if (guide != null) return guide;
        }
        return null;
    }

    private Candidate selectCandidate(List<Candidate> candidates, Set<String> expectedCountries) {
        if (candidates.isEmpty()) return null;
        Comparator<Candidate> order = expectedCountries.isEmpty()
                ? Comparator.comparingInt(Candidate::rank).thenComparingInt(Candidate::regionRank)
                : Comparator.comparingInt(Candidate::regionRank).thenComparingInt(Candidate::rank);
        candidates.sort(order.thenComparingInt(v -> v.source().priority()).thenComparing(v -> v.source().id()));
        Candidate first = candidates.getFirst();
        if (hasEqualPriorityConflict(candidates, first)) return null;
        if (expectedCountries.isEmpty() && hasCountryConflict(candidates, first)) return null;
        return first;
    }

    private boolean hasEqualPriorityConflict(List<Candidate> candidates, Candidate first) {
        return candidates.stream().anyMatch(v -> v.rank() == first.rank() && v.regionRank() == first.regionRank()
                && v.source().priority() == first.source().priority() && !v.guide().equals(first.guide()));
    }

    private boolean hasCountryConflict(List<Candidate> candidates, Candidate first) {
        return candidates.stream().anyMatch(v -> v.rank() == first.rank() && v.regionRank() == first.regionRank()
                && !v.guide().equals(first.guide()) && EpgTerritory.disjointKnown(first.countries(), v.countries()));
    }
    public Map<Query,Result> read(List<Query> queries, long now, boolean full) {
        if (queries.isEmpty()) return Map.of();
        Map<Query,Result> result = new HashMap<>();
        try (var c = open()) {
            Snapshot snapshot = index(c,now);
            if (snapshot.sources().isEmpty()) return result;
            var rules = new EpgNameRulesDb().read(c);
            result.putAll(readQueries(c, queries, snapshot, rules, now, full));
        } catch (Exception error) { throw new DatabaseAccessException("Unable to read shared EPG cache", error); }
        return result;
    }

    private Map<Query, Result> readQueries(Connection c, List<Query> queries, Snapshot snapshot,
                                           EpgNameRules rules, long now, boolean full) throws SQLException {
        Map<Query, Result> result = new HashMap<>();
        try (var programmes = c.prepareStatement("SELECT title,description,starts_at,stops_at FROM epg_programme WHERE source_id=? AND guide_channel_id=? AND stops_at>? AND starts_at<? ORDER BY starts_at LIMIT " + (full ? 2000 : 2));
             var icons = c.prepareStatement("SELECT icon_url FROM epg_channel_icon WHERE source_id=? AND guide_channel_id=?")) {
            for (Query query : new LinkedHashSet<>(queries)) {
                Candidate match = resolve(query, snapshot, rules);
                if (match == null) continue;
                Result resolved = readResult(query, match, programmes, icons, now, full);
                if (resolved != null) result.put(query, resolved);
            }
        }
        return result;
    }

    private Result readResult(Query query, Candidate match, PreparedStatement programmes,
                              PreparedStatement icons, long now, boolean full) throws SQLException {
        programmes.setString(1, match.source().id());
        programmes.setString(2, match.guide());
        programmes.setLong(3, full ? now - 6 * 3_600_000L : now);
        programmes.setLong(4, now + 7 * 86_400_000L);
        List<EpgProgramme> schedule = new ArrayList<>();
        try (var rows = programmes.executeQuery()) {
            while (rows.next()) {
                schedule.add(new EpgProgramme(rows.getString(1), full ? expand(rows.getBytes(2)) : "",
                        rows.getLong(3), rows.getLong(4)));
            }
        } catch (java.io.IOException error) {
            throw new SQLException("Unable to expand EPG description", error);
        }
        icons.setString(1, match.source().id());
        icons.setString(2, match.guide());
        String icon = "";
        try (var rows = icons.executeQuery()) {
            if (rows.next()) icon = rows.getString(1);
        }
        return new Result(List.copyOf(schedule), icon);
    }

    /** Explain an empty shared lookup without exposing provider credentials or URLs. */
    public Map<Query,ResolutionDiagnostic> diagnostics(List<Query> queries, long now) {
        if (queries.isEmpty()) return Map.of();
        Map<Query,ResolutionDiagnostic> result = new LinkedHashMap<>();
        try (var c = open()) {
            Snapshot snapshot = index(c, now);
            var rules = new EpgNameRulesDb().read(c);
            for (Query query : new LinkedHashSet<>(queries)) {
                EpgTerritory.Context context = EpgTerritory.parse(query.name());
                Candidate match = resolve(query, snapshot, rules);
                ResolutionStatus status = diagnosticStatus(match, query, context, snapshot, rules);
                result.put(query, new ResolutionDiagnostic(status, query.guideId(), context.name(), snapshot.sources().size()));
            }
        } catch (Exception error) {
            throw new DatabaseAccessException("Unable to diagnose shared EPG cache", error);
        }
        return result;
    }

    private ResolutionStatus diagnosticStatus(Candidate match, Query query, EpgTerritory.Context context,
                                              Snapshot snapshot, EpgNameRules rules) {
        if (match != null) return ResolutionStatus.MATCHED;
        if (snapshot.sources().isEmpty()) return ResolutionStatus.NO_SOURCE_COVERAGE;
        return hasCandidateIdentity(query, context, snapshot, rules)
                ? ResolutionStatus.MATCHING_FAILED : ResolutionStatus.NO_SOURCE_COVERAGE;
    }

    private boolean hasCandidateIdentity(Query query, EpgTerritory.Context context, Snapshot snapshot, EpgNameRules rules) {
        List<String> names = new ArrayList<>(List.of(context.name()));
        String playlistIndexFree = EpgTerritory.stripLeadingPlaylistIndex(context.name());
        if (!playlistIndexFree.equals(context.name())) names.add(playlistIndexFree);
        for (Source source : snapshot.sources()) {
            if (query.guideId() != null && !query.guideId().isBlank()
                    && (source.ids().contains(query.guideId())
                    || source.identifiers().containsKey(XmltvGuide.normalizeIdentifier(query.guideId())))) return true;
            for (String name : names) {
                if (source.exact().containsKey(XmltvGuide.normalize(name))
                        || source.display().containsKey(rules.display(name))
                        || source.quality().containsKey(rules.quality(name))) return true;
            }
        }
        return false;
    }
    private String expand(byte[] bytes) throws java.io.IOException {
        try (var input = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes))) {
            byte[] text = input.readNBytes(8193);
            if (text.length > 8192) throw new java.io.IOException("EPG description exceeds cache limit");
            return new String(text, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
