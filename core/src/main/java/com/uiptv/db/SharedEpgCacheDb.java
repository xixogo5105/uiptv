package com.uiptv.db;

import com.uiptv.application.*;
import java.sql.*;
import java.util.*;

/** Account-independent, read-only resolution of explicitly published shared guides. */
// Query limits are fixed application constants; all user values remain prepared parameters.
@SuppressWarnings("java:S2077")
public final class SharedEpgCacheDb {
    public record Query(String guideId, String name, boolean explicit) { }
    public record Result(List<EpgProgramme> programmes, String icon) { }
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
    private static volatile Snapshot cached;

    static void invalidate() { cached = null; }

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
        List<String[]> metadata = new ArrayList<>();
        StringBuilder version = new StringBuilder(EpgCacheDb.path().toString()).append('|').append(rules.fingerprint())
                .append('|').append(EpgTerritory.fingerprintValue()).append('|').append(now / 60_000);
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT g.source_id,g.priority,g.countries,g.languages,s.refreshed_at FROM epg_shared_source g JOIN epg_source s ON s.source_id=g.source_id ORDER BY g.priority,g.source_id")) {
            while (r.next()) {
                metadata.add(new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4)});
                for (int i = 1; i <= 5; i++) version.append('|').append(r.getString(i));
            }
        }
        Snapshot previous = cached;
        if (previous != null && previous.version().equals(version.toString())) return previous;
        List<Source> sources = new ArrayList<>();
        try (var s = c.prepareStatement("SELECT name,guide_channel_id FROM epg_alias WHERE source_id=? AND kind=0 AND EXISTS(SELECT 1 FROM epg_programme p WHERE p.source_id=epg_alias.source_id AND p.guide_channel_id=epg_alias.guide_channel_id AND p.stops_at>?)")) {
            for (var m : metadata) {
                Map<String,String> names = new HashMap<>(); s.setString(1, m[0]); s.setLong(2,now);
                try (var r = s.executeQuery()) { while (r.next()) names.put(r.getString(1), r.getString(2)); }
                Map<String,Set<String>> guideCountries = new HashMap<>();
                names.values().forEach(guide -> guideCountries.put(guide,
                        EpgTerritory.sourceCountries(m[2], m[0], guide)));
                Map<String,String> identifiers = new HashMap<>();
                Set<String> ambiguousIdentifiers = new HashSet<>();
                names.values().forEach(guide -> {
                    String key = XmltvGuide.normalizeIdentifier(guide);
                    if (key.isBlank()) return;
                    String prior = identifiers.putIfAbsent(key, guide);
                    if (prior != null && !prior.equals(guide)) ambiguousIdentifiers.add(key);
                });
                ambiguousIdentifiers.forEach(identifiers::remove);
                sources.add(new Source(m[0], Integer.parseInt(m[1]), EpgTerritory.parseCountries(m[2]),
                        tokens(m[3]), Map.copyOf(guideCountries), Map.copyOf(names),
                        rules.aliases(names, false), rules.aliases(names, true), Map.copyOf(identifiers), Set.copyOf(names.values())));
            }
        }
        var snapshot = new Snapshot(version.toString(), List.copyOf(sources)); cached = snapshot; return snapshot;
    }
    private Candidate resolve(Query query, Snapshot snapshot, EpgNameRules rules) {
        EpgTerritory.Context context = EpgTerritory.parse(query.name());
        if (!query.explicit() && context.ambiguous()) return null;
        String name = context.name();
        Set<String> expectedCountries = context.territories();
        List<Candidate> candidates = new ArrayList<>();
        for (Source source : snapshot.sources()) {
            String guide = query.guideId();
            int rank = 0;
            if (guide == null || guide.isBlank() || !source.ids().contains(guide)) {
                String identifierMatch = guide == null ? "" : source.identifiers().get(XmltvGuide.normalizeIdentifier(guide));
                if (identifierMatch != null) guide = identifierMatch;
            }
            if (guide == null || guide.isBlank() || !source.ids().contains(guide)) {
                if (query.explicit()) continue;
                rank = 1; guide = source.exact().get(XmltvGuide.normalize(name));
                if (guide == null) guide = source.display().get(rules.display(name));
                if (guide == null) { rank = 2; guide = source.quality().get(rules.quality(name)); }
            }
            if (guide == null || guide.isBlank()) continue;
            Set<String> actualCountries = source.countries(guide);
            if (!query.explicit() && EpgTerritory.conflicts(expectedCountries, actualCountries)) continue;
            int regionRank = expectedCountries.isEmpty()
                    ? (actualCountries.isEmpty() ? 0 : 1)
                    : (actualCountries.isEmpty() ? 1 : 0);
            candidates.add(new Candidate(source, guide, rank, regionRank, actualCountries));
        }
        Comparator<Candidate> order = expectedCountries.isEmpty()
                ? Comparator.comparingInt(Candidate::rank).thenComparingInt(Candidate::regionRank)
                : Comparator.comparingInt(Candidate::regionRank).thenComparingInt(Candidate::rank);
        candidates.sort(order.thenComparingInt(v -> v.source().priority()).thenComparing(v -> v.source().id()));
        if (candidates.isEmpty()) return null;
        Candidate first = candidates.getFirst();
        // Equal-priority conflicting identities are ambiguous. Never guess between them.
        if (candidates.stream().anyMatch(v -> v.rank() == first.rank() && v.regionRank() == first.regionRank()
                && v.source().priority() == first.source().priority() && !v.guide().equals(first.guide()))) return null;
        if (expectedCountries.isEmpty() && candidates.stream().anyMatch(v -> v.rank() == first.rank()
                && v.regionRank() == first.regionRank() && !v.guide().equals(first.guide())
                && EpgTerritory.disjointKnown(first.countries(), v.countries()))) return null;
        return first;
    }
    public Map<Query,Result> read(List<Query> queries, long now, boolean full) {
        if (queries.isEmpty()) return Map.of();
        Map<Query,Result> result = new HashMap<>();
        try (var c = open()) {
            Snapshot snapshot = index(c,now);
            if (snapshot.sources().isEmpty()) return result;
            var rules = new EpgNameRulesDb().read(c);
            try (var programmes = c.prepareStatement("SELECT title,description,starts_at,stops_at FROM epg_programme WHERE source_id=? AND guide_channel_id=? AND stops_at>? AND starts_at<? ORDER BY starts_at LIMIT " + (full ? 2000 : 2));
                 var icons = c.prepareStatement("SELECT icon_url FROM epg_channel_icon WHERE source_id=? AND guide_channel_id=?")) {
                for (Query query : new LinkedHashSet<>(queries)) {
                    Candidate match = resolve(query, snapshot, rules); if (match == null) continue;
                    programmes.setString(1, match.source().id()); programmes.setString(2, match.guide());
                    programmes.setLong(3, full ? now - 6 * 3_600_000L : now); programmes.setLong(4, now + 7 * 86_400_000L);
                    List<EpgProgramme> schedule = new ArrayList<>();
                    try (var rows = programmes.executeQuery()) {
                        while (rows.next()) schedule.add(new EpgProgramme(rows.getString(1), full ? expand(rows.getBytes(2)) : "", rows.getLong(3), rows.getLong(4)));
                    }
                    icons.setString(1, match.source().id()); icons.setString(2, match.guide()); String icon = "";
                    try (var rows = icons.executeQuery()) { if (rows.next()) icon = rows.getString(1); }
                    result.put(query, new Result(List.copyOf(schedule), icon));
                }
            }
        } catch (Exception error) { throw new DatabaseAccessException("Unable to read shared EPG cache", error); }
        return result;
    }
    private String expand(byte[] bytes) throws java.io.IOException {
        try (var input = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes))) {
            byte[] text = input.readNBytes(8193);
            if (text.length > 8192) throw new java.io.IOException("EPG description exceeds cache limit");
            return new String(text, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
