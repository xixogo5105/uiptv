package com.uiptv.db;

import com.uiptv.application.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Account-independent, read-only resolution of explicitly published shared guides. */
// Query limits are fixed application constants; all user values remain prepared parameters.
@SuppressWarnings("java:S2077")
public final class SharedEpgCacheDb {
    public record Query(String guideId, String name, boolean explicit) { }
    public record Result(List<EpgProgramme> programmes, String icon) { }
    private record Source(String id, int priority, Set<String> countries, Set<String> languages,
                          Map<String,String> exact, Map<String,String> display, Map<String,String> quality,
                          Set<String> ids) { }
    private record Snapshot(String version, List<Source> sources) { }
    private record Candidate(Source source, String guide, int rank) { }
    private static volatile Snapshot cached;
    private static final Pattern GROUP = Pattern.compile("^\\s*[┃│|]([^┃│|]+)[┃│|]\\s*");

    static void invalidate() { cached = null; }

    private Connection open() throws SQLException {
        var c = DriverManager.getConnection("jdbc:sqlite:" + EpgCacheDb.path().toUri() + "?mode=ro");
        try (var s = c.createStatement()) { s.execute("PRAGMA query_only=ON"); s.execute("PRAGMA busy_timeout=3000"); }
        return c;
    }
    private static Set<String> tokens(String value) {
        return value.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(value.toUpperCase(Locale.ROOT).split(",")));
    }
    private static String country(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "UK", "GB" -> "GB"; case "USA", "US" -> "US"; case "CAN", "CA" -> "CA";
            case "AUS", "AU" -> "AU"; case "IND", "IN" -> "IN"; case "PK" -> "PK"; case "NZ" -> "NZ";
            default -> "";
        };
    }
    private Snapshot index(Connection c, long now) throws SQLException {
        var rules = new EpgNameRulesDb().read(c);
        List<String[]> metadata = new ArrayList<>();
        StringBuilder version = new StringBuilder(EpgCacheDb.path().toString()).append('|').append(rules.fingerprint()).append('|').append(now / 60_000);
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
                sources.add(new Source(m[0], Integer.parseInt(m[1]), tokens(m[2]), tokens(m[3]), Map.copyOf(names),
                        rules.aliases(names, false), rules.aliases(names, true), Set.copyOf(names.values())));
            }
        }
        var snapshot = new Snapshot(version.toString(), List.copyOf(sources)); cached = snapshot; return snapshot;
    }
    private Candidate resolve(Query query, Snapshot snapshot, EpgNameRules rules) {
        String name = Objects.toString(query.name(), "");
        String region = "";
        var group = GROUP.matcher(name);
        if (group.find()) { region = country(group.group(1)); name = name.substring(group.end()); }
        List<Candidate> candidates = new ArrayList<>();
        for (Source source : snapshot.sources()) {
            if (!query.explicit() && !region.isBlank() && !source.countries().isEmpty() && !source.countries().contains(region)) continue;
            String guide = query.guideId();
            int rank = 0;
            if (guide == null || guide.isBlank() || !source.ids().contains(guide)) {
                if (query.explicit()) continue;
                rank = 1; guide = source.exact().get(XmltvGuide.normalize(name));
                if (guide == null) guide = source.display().get(rules.display(name));
                if (guide == null) { rank = 2; guide = source.quality().get(rules.quality(name)); }
            }
            if (guide != null && !guide.isBlank()) candidates.add(new Candidate(source, guide, rank));
        }
        candidates.sort(Comparator.comparingInt(Candidate::rank).thenComparingInt(v -> v.source().priority()).thenComparing(v -> v.source().id()));
        if (candidates.isEmpty()) return null;
        Candidate first = candidates.getFirst();
        // Equal-priority conflicting identities are ambiguous. Never guess between them.
        if (candidates.stream().anyMatch(v -> v.rank() == first.rank() && v.source().priority() == first.source().priority() && !v.guide().equals(first.guide()))) return null;
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
