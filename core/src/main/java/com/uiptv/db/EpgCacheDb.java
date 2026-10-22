package com.uiptv.db;

import com.uiptv.application.EpgProgramme;
import com.uiptv.application.EpgChangeNotifications;
import com.uiptv.application.EpgNameRules;
import com.uiptv.application.EpgTerritory;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Indexed EPG cache tables in the application database. Read paths never initialize, mutate or download anything. */
// Dynamic SQL fragments here are generated placeholder tuples or fixed schema table names.
@SuppressWarnings("java:S2077")
public final class EpgCacheDb {
    public record Identity(String accountId, String providerId, String signature) { }
    public record Match(Identity identity, String guideId, String name, String qualityName, boolean explicit,
                        List<String> sources, String nativeSource) { }
    public record SourceState(long refreshedAt, long retryAt, String warning) { }
    public static Path path() { return Path.of(SQLConnection.getDatabasePath()); }

    private Connection open(boolean write) throws SQLException {
        Connection connection=write ? SQLConnection.connect()
                : DriverManager.getConnection("jdbc:sqlite:"+path().toUri()+"?mode=ro");
        try(Statement statement=connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
            statement.execute("PRAGMA cache_size=-2048");
            if(!write) statement.execute("PRAGMA query_only=ON");
            return connection;
        } catch (SQLException error) { connection.close(); throw error; }
    }

    public SourceState sourceState(String sourceId) {
        try (Connection c = open(false)) {
            try (var statement = c.prepareStatement("SELECT refreshed_at,retry_at,warning FROM epg_source WHERE source_id=?")) {
                statement.setString(1, sourceId);
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? new SourceState(rows.getLong(1), rows.getLong(2), rows.getString(3)) : new SourceState(0, 0, "");
                }
            }
        } catch (Exception error) { throw failure(error); }
    }

    /** One transaction publishes a whole source; failed imports leave the old source intact. */
    public void replaceSource(String sourceId, Map<String, List<EpgProgramme>> programmes,
                              Map<String, String> aliases, Map<String, String> qualityAliases, long now, long retryAt) {
        replaceSource(sourceId,programmes,aliases,qualityAliases,Map.of(),now,retryAt,Set.of());
    }
    @SuppressWarnings("java:S107")
    public void replaceSource(String sourceId,Map<String,List<EpgProgramme>> programmes,
                              Map<String,String> aliases,Map<String,String> qualityAliases,Map<String,String> icons,long now,long retryAt) {
        replaceSource(sourceId, programmes, aliases, qualityAliases, icons, now, retryAt, Set.of(), _ -> { });
    }
    @SuppressWarnings("java:S107")
    public void replaceSource(String sourceId,Map<String,List<EpgProgramme>> programmes,
                              Map<String,String> aliases,Map<String,String> qualityAliases,Map<String,String> icons,
                              long now,long retryAt, Set<EpgChangeNotifications.ProviderIdentity> identities) {
        replaceSource(sourceId, programmes, aliases, qualityAliases, icons, now, retryAt, identities, _ -> { });
    }
    @SuppressWarnings("java:S107")
    public void replaceSource(String sourceId,Map<String,List<EpgProgramme>> programmes,
                              Map<String,String> aliases,Map<String,String> qualityAliases,Map<String,String> icons,
                              long now,long retryAt, Set<EpgChangeNotifications.ProviderIdentity> identities,
                              java.util.function.LongConsumer processed) {
        EpgChangeNotifications.Notification notification;
        try (Connection c = open(true)) {
            notification = replaceSourceTransaction(c, sourceId, programmes, aliases, qualityAliases, icons,
                    now, retryAt, identities, processed);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw failure(error);
        } catch (SQLException | java.io.IOException error) {
            throw failure(error);
        }
        EpgChangeNotifications.publish(notification);
    }

    @SuppressWarnings("java:S107")
    private EpgChangeNotifications.Notification replaceSourceTransaction(
            Connection c, String sourceId, Map<String,List<EpgProgramme>> programmes,
            Map<String,String> aliases, Map<String,String> qualityAliases, Map<String,String> icons,
            long now, long retryAt, Set<EpgChangeNotifications.ProviderIdentity> identities,
            java.util.function.LongConsumer processed)
            throws SQLException, InterruptedException, java.io.IOException {
        c.setAutoCommit(false);
        try {
            replaceSource(c, sourceId, programmes, aliases, qualityAliases, icons, now, retryAt, processed);
            EpgChangeNotifications.Notification notification = EpgChangeNotifications.create(c,
                    EpgChangeNotifications.Kind.SOURCE_IMPORT, Set.of(sourceId), programmes.keySet(), identities);
            c.commit();
            return notification;
        } catch (SQLException | InterruptedException | java.io.IOException error) {
            c.rollback();
            throw error;
        }
    }

    @SuppressWarnings("java:S107")
    private void replaceSource(Connection c, String sourceId, Map<String,List<EpgProgramme>> programmes,
                       Map<String,String> aliases, Map<String,String> qualityAliases,
                       Map<String,String> icons, long now, long retryAt,
                       java.util.function.LongConsumer processed)
            throws SQLException, InterruptedException, java.io.IOException {
        deleteSourceRows(c,"epg_programme",sourceId);
        deleteSourceRows(c,"epg_alias",sourceId);
        deleteSourceRows(c,"epg_channel_icon",sourceId);
        insertProgrammes(c, sourceId, programmes, now, processed);
        insertAliases(c, sourceId, aliases, qualityAliases);
        insertIcons(c, sourceId, icons);
        state(c, sourceId, now, retryAt, "");
    }

    @SuppressWarnings("java:S6909")
    private void insertProgrammes(Connection c, String sourceId, Map<String, List<EpgProgramme>> programmes,
                                  long now, java.util.function.LongConsumer processed)
            throws SQLException, InterruptedException {
        try (var insert = c.prepareStatement("INSERT INTO epg_programme VALUES(?,?,?,?,?,?)");
             var compressor = new DescriptionCompressor()) {
            int batch = 0;
            long scanned = 0;
            for (var entry : programmes.entrySet()) {
                for (var programme : entry.getValue()) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    scanned++;
                    if (scanned % 500 == 0) processed.accept(scanned);
                    if (programme.stop() <= programme.start() || programme.title().isBlank()
                            || programme.stop() <= now - 6 * 3_600_000L
                            || programme.start() >= now + 7 * 86_400_000L) continue;
                    // SQLite clears all bound values after executeBatch; bind the source on every row.
                    insert.setString(1, sourceId);
                    insert.setString(2, entry.getKey());
                    insert.setLong(3, programme.start());
                    insert.setLong(4, programme.stop());
                    insert.setString(5, programme.title());
                    insert.setBytes(6, compressor.compress(programme.description()));
                    insert.addBatch();
                    if (++batch % 500 == 0) insert.executeBatch();
                }
            }
            insert.executeBatch();
            processed.accept(scanned);
        }
    }

    private void insertAliases(Connection c, String sourceId, Map<String, String> aliases,
                               Map<String, String> qualityAliases) throws SQLException {
        insertAliasKind(c, sourceId, aliases, 0);
        insertAliasKind(c, sourceId, qualityAliases, 1);
    }

    private void insertAliasKind(Connection c, String sourceId, Map<String, String> aliases, int kind)
            throws SQLException {
        try (var insert = c.prepareStatement("INSERT INTO epg_alias VALUES(?,?,?,?)")) {
            insert.setString(1, sourceId);
            insert.setInt(2, kind);
            for (var alias : aliases.entrySet()) {
                insert.setString(3, alias.getKey());
                insert.setString(4, alias.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertIcons(Connection c, String sourceId, Map<String, String> icons) throws SQLException {
        try (var insert = c.prepareStatement("INSERT INTO epg_channel_icon VALUES(?,?,?)")) {
            insert.setString(1, sourceId);
            for (var icon : icons.entrySet()) {
                insert.setString(2, icon.getKey());
                insert.setString(3, icon.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void rollback(Connection c, RuntimeException error) {
        try { c.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
    }

    private void deleteSourceRows(Connection c, String table, String source) throws SQLException {
        try (var statement = c.prepareStatement("DELETE FROM " + table + " WHERE source_id=?")) { statement.setString(1, source); statement.executeUpdate(); }
    }
    private void state(Connection c, String id, long refreshed, long retry, String warning) throws SQLException {
        try (var statement = c.prepareStatement("INSERT INTO epg_source VALUES(?,?,?,?) ON CONFLICT(source_id) DO UPDATE SET refreshed_at=excluded.refreshed_at,retry_at=excluded.retry_at,warning=excluded.warning")) {
            statement.setString(1,id); statement.setLong(2,refreshed); statement.setLong(3,retry); statement.setString(4,warning); statement.executeUpdate();
        }
    }
    public void failed(String sourceId, long retryAt, String warning) {
        SourceState previous = sourceState(sourceId);
        try (Connection c = open(true)) { state(c,sourceId,previous.refreshedAt(),retryAt,warning); }
        catch (Exception error) { throw failure(error); }
    }

    /** Persist every matched source in priority order; quality selection happens at query time. */
    private record Link(String signature,String source,String guide,int priority,Set<String> countries) { }
    private record Aliases(Map<String,String> display, Map<String,String> quality) { }
    private record MappingQueries(Connection connection, PreparedStatement direct, PreparedStatement alias,
                                  PreparedStatement existing, EpgNameRules rules,
                                  Map<String,String> declaredCountries, Map<String,Aliases> indexes, long now) { }
    public boolean mapChannels(List<Match> matches,long now) { return mapChannels(matches,now,false); }
    public boolean mapChannels(List<Match> matches,long now,boolean preserveExistingMappings) {
        return mapChannels(matches, now, preserveExistingMappings, _ -> { });
    }
    public boolean mapChannels(List<Match> matches,long now,boolean preserveExistingMappings,
                               java.util.function.LongConsumer processed) {
        // A provider channel may be cached in multiple categories. Queue just one
        // mapping per logical channel, or deferred inserts collide at batch commit
        // and roll back every channel in the account after a signature change.
        record ChannelKey(String account,String provider) { }
        Map<ChannelKey,Match> unique=new LinkedHashMap<>();
        for (Match match:matches) unique.putIfAbsent(new ChannelKey(match.identity().accountId(),match.identity().providerId()),match);
        try (Connection c = open(true)) {
            return mapChannelsTransaction(c, unique.values(), now, preserveExistingMappings, processed);
        } catch (SQLException error) { throw failure(error); }
    }

    private boolean mapChannelsTransaction(Connection c, Collection<Match> matches, long now,
                                           boolean preserveExistingMappings,
                                           java.util.function.LongConsumer processed)
            throws SQLException {
        c.setAutoCommit(false);
        boolean changed = false;
        Set<EpgChangeNotifications.ProviderIdentity> changedIdentities = new LinkedHashSet<>();
        try (var direct = c.prepareStatement("WITH target(source,id,now) AS (VALUES(?,?,?)) SELECT id FROM target t WHERE EXISTS(SELECT 1 FROM epg_programme WHERE source_id=t.source AND guide_channel_id=t.id AND stops_at>t.now) OR EXISTS(SELECT 1 FROM epg_channel_icon WHERE source_id=t.source AND guide_channel_id=t.id) LIMIT 1");
             var alias = c.prepareStatement("SELECT guide_channel_id FROM epg_alias WHERE source_id=? AND kind=? AND name=?");
             var existing = c.prepareStatement("SELECT signature,source_id,guide_channel_id,priority FROM epg_channel_mapping WHERE account_id=? AND provider_channel_id=? ORDER BY priority");
             var delete = c.prepareStatement("DELETE FROM epg_channel_mapping WHERE account_id=? AND provider_channel_id=?");
             var insert = c.prepareStatement("INSERT INTO epg_channel_mapping VALUES(?,?,?,?,?,?)")) {
            var rules = new EpgNameRulesDb().read(c);
            MappingQueries queries = new MappingQueries(c, direct, alias, existing, rules,
                    readDeclaredCountries(c), new HashMap<>(), now);
            long scanned = 0;
            for (Match match : matches) {
                if (Thread.currentThread().isInterrupted()) throw new SQLException("EPG mapping interrupted");
                List<Link> previous = readPreviousMappings(match, queries);
                boolean sameSignature = preserveExistingMappings
                        && previous.stream().anyMatch(prior -> prior.signature().equals(match.identity().signature()));
                if (!sameSignature) {
                    List<Link> links = resolveLinks(match, previous, queries);
                    if (!previous.equals(links)) {
                        changed = true;
                        changedIdentities.add(new EpgChangeNotifications.ProviderIdentity(
                                match.identity().accountId(), match.identity().providerId()));
                        delete.setString(1, match.identity().accountId());
                        delete.setString(2, match.identity().providerId());
                        delete.addBatch();
                        for (Link link : links) {
                            insert.setString(1, match.identity().accountId());
                            insert.setString(2, match.identity().providerId());
                            insert.setString(3, link.signature());
                            insert.setString(4, link.source());
                            insert.setString(5, link.guide());
                            insert.setInt(6, link.priority());
                            insert.addBatch();
                        }
                    }
                }
                scanned++;
                if (scanned % 100 == 0) processed.accept(scanned);
            }
            delete.executeBatch();
            insert.executeBatch();
            processed.accept(scanned);
            EpgChangeNotifications.Notification notification = changed
                    ? EpgChangeNotifications.create(c, EpgChangeNotifications.Kind.MAPPING, Set.of(), Set.of(), changedIdentities) : null;
            c.commit();
            if (notification != null) EpgChangeNotifications.publish(notification);
            return changed;
        } catch (Exception error) {
            c.rollback();
            throw error;
        }
    }

    private Map<String, String> readDeclaredCountries(Connection c) throws SQLException {
        Map<String, String> declaredCountries = new HashMap<>();
        try (var metadata = c.createStatement(); var rows = metadata.executeQuery("SELECT source_id,countries FROM epg_shared_source")) {
            while (rows.next()) declaredCountries.put(rows.getString(1), rows.getString(2));
        }
        return declaredCountries;
    }

    private List<Link> readPreviousMappings(Match match, MappingQueries queries) throws SQLException {
        List<Link> previous = new ArrayList<>();
        PreparedStatement existing = queries.existing();
        existing.setString(1, match.identity().accountId());
        existing.setString(2, match.identity().providerId());
        try (var rows = existing.executeQuery()) {
            while (rows.next()) {
                String source = rows.getString(2);
                String guide = rows.getString(3);
                previous.add(new Link(rows.getString(1), source, guide, rows.getInt(4),
                        EpgTerritory.sourceCountries(queries.declaredCountries().get(source), source, guide)));
            }
        }
        return previous;
    }

    private List<Link> resolveLinks(Match match, List<Link> previous, MappingQueries queries) throws SQLException {
        EpgTerritory.Context context = EpgTerritory.parse(match.name());
        Set<String> expectedCountries = context.territories();
        List<Link> links = new ArrayList<>();
        for (int priority = 0; priority < match.sources().size(); priority++) {
            if (!match.explicit() && context.ambiguous()) continue;
            String candidate = match.sources().get(priority);
            String guide = resolveGuide(match, context.name(), candidate, queries);
            if (!guide.isBlank()) {
                Set<String> countries = EpgTerritory.sourceCountries(
                        queries.declaredCountries().get(candidate), candidate, guide);
                if (match.explicit() || !EpgTerritory.conflicts(expectedCountries, countries)) {
                    links.add(new Link(match.identity().signature(), candidate, guide, priority, countries));
                }
            }
        }
        applyCountryPolicy(match, context, links);
        return links;
    }

    private String resolveGuide(Match match, String lookupName, String candidate, MappingQueries queries) throws SQLException {
        PreparedStatement direct = queries.direct();
        direct.setString(1, candidate);
        direct.setString(2, candidate.equals(match.nativeSource()) ? match.identity().providerId() : match.guideId());
        direct.setLong(3, queries.now());
        String guide = firstValue(direct);
        if (guide.isBlank() && !match.explicit()) {
            guide = aliasGuide(lookupName, candidate, queries);
            if (!guide.isBlank() && !hasActiveGuide(direct, candidate, guide, queries.now())) guide = "";
            if (guide.isBlank()) guide = indexedGuide(lookupName, candidate, queries);
        }
        return guide;
    }

    private String aliasGuide(String lookupName, String candidate, MappingQueries queries) throws SQLException {
        PreparedStatement alias = queries.alias();
        alias.setString(1, candidate);
        alias.setInt(2, 0);
        alias.setString(3, lookupName);
        return firstValue(alias);
    }

    private boolean hasActiveGuide(PreparedStatement direct, String candidate, String guide, long now) throws SQLException {
        direct.setString(1, candidate);
        direct.setString(2, guide);
        direct.setLong(3, now);
        return hasRow(direct);
    }

    private String indexedGuide(String lookupName, String candidate, MappingQueries queries) {
        Aliases index = queries.indexes().computeIfAbsent(candidate, key -> loadAliases(key, queries));
        String guide = index.display().getOrDefault(queries.rules().display(lookupName), "");
        return guide.isBlank() ? index.quality().getOrDefault(queries.rules().quality(lookupName), "") : guide;
    }

    private Aliases loadAliases(String candidate, MappingQueries queries) {
        Map<String,String> names = new HashMap<>();
        try (var query = queries.connection().prepareStatement("SELECT a.name,a.guide_channel_id FROM epg_alias a WHERE a.source_id=? AND a.kind=0 AND (EXISTS(SELECT 1 FROM epg_programme p WHERE p.source_id=a.source_id AND p.guide_channel_id=a.guide_channel_id AND p.stops_at>?) OR EXISTS(SELECT 1 FROM epg_channel_icon i WHERE i.source_id=a.source_id AND i.guide_channel_id=a.guide_channel_id))")) {
            query.setString(1, candidate);
            query.setLong(2, queries.now());
            try (var rows = query.executeQuery()) {
                while (rows.next()) names.put(rows.getString(1), rows.getString(2));
            }
        } catch (SQLException error) {
            throw new DatabaseAccessException("Unable to read EPG aliases", error);
        }
        return new Aliases(queries.rules().aliases(names, false), queries.rules().aliases(names, true));
    }

    private String firstValue(PreparedStatement statement) throws SQLException {
        try (var row = statement.executeQuery()) {
            return row.next() ? row.getString(1) : "";
        }
    }

    private boolean hasRow(PreparedStatement statement) throws SQLException {
        try (var row = statement.executeQuery()) { return row.next(); }
    }

    private void applyCountryPolicy(Match match, EpgTerritory.Context context, List<Link> links) {
        if (match.explicit() || context.ambiguous()) return;
        Set<String> expected = context.territories();
        if (!expected.isEmpty() && links.stream().anyMatch(link -> !link.countries().isEmpty())) {
            links.removeIf(link -> link.countries().isEmpty());
        }
        if (expected.isEmpty() && hasConflictingCountries(links)) links.clear();
    }

    private boolean hasConflictingCountries(List<Link> links) {
        return links.stream().anyMatch(left -> links.stream().anyMatch(right ->
                !left.guide().equals(right.guide()) && EpgTerritory.disjointKnown(left.countries(), right.countries())));
    }

    private record Selection(String source, List<EpgProgramme> programmes) { }
    private Map<Identity,Selection> select(Connection c,List<Identity> identities,long now) throws SQLException {
        Map<Identity,Selection> result=new HashMap<>();
        for(int offset=0;offset<identities.size();offset+=128) {
            List<Identity> batch=identities.subList(offset,Math.min(offset+128,identities.size()));
            String values=String.join(",",java.util.stream.IntStream.range(0,batch.size()).mapToObj(i -> "("+i+",?,?,?)").toList());
            String sql="WITH requested(n,account_id,provider_id,signature) AS (VALUES "+values+") SELECT r.n,m.source_id,p.title,p.starts_at,p.stops_at FROM requested r JOIN epg_channel_mapping m ON m.account_id=r.account_id AND m.provider_channel_id=r.provider_id AND m.signature=r.signature JOIN epg_programme p ON p.source_id=m.source_id AND p.guide_channel_id=m.guide_channel_id AND p.starts_at IN (SELECT q.starts_at FROM epg_programme q WHERE q.source_id=m.source_id AND q.guide_channel_id=m.guide_channel_id AND q.stops_at>? ORDER BY q.starts_at LIMIT 2) ORDER BY r.n,m.priority,m.source_id,p.starts_at";
            Map<Integer,Map<String,List<EpgProgramme>>> candidates=new HashMap<>();
            try(var query=c.prepareStatement(sql)) {
                int parameter=1;
                for(Identity id:batch) { query.setString(parameter++,id.accountId()); query.setString(parameter++,id.providerId()); query.setString(parameter++,id.signature()); }
                query.setLong(parameter,now);
                try(var rows=query.executeQuery()) { while(rows.next()) candidates.computeIfAbsent(rows.getInt(1),_ -> new LinkedHashMap<>())
                        .computeIfAbsent(rows.getString(2),_ -> new ArrayList<>(2)).add(new EpgProgramme(rows.getString(3),"",rows.getLong(4),rows.getLong(5))); }
            }
            for(int i=0;i<batch.size();i++) result.put(batch.get(i),choose(candidates.getOrDefault(i,Map.of()),now));
        }
        return result;
    }
    private Selection choose(Map<String,List<EpgProgramme>> candidates,long now) {
        List<EpgProgramme> merged=com.uiptv.application.EpgSchedules.preferSources(new ArrayList<>(candidates.values()));
        return new Selection("",merged.stream().filter(p -> p.stop()>now).limit(2).toList());
    }

    /** One indexed SELECT per 128 identities. Card summaries never read descriptions. */
    public Map<Identity,List<EpgProgramme>> summaries(List<Identity> identities,long now) {
        if(identities.isEmpty()) return Map.of();
        Map<Identity,List<EpgProgramme>> result=new HashMap<>();
        try(Connection c=open(false)) {
            select(c,identities,now).forEach((id,selection) -> result.put(id,selection.programmes()));
        } catch(Exception error) { throw failure(error); }
        return result;
    }
    /** Identities whose mapped guide source failed after a previous successful import. */
    public Set<Identity> sourceWarnings(List<Identity> identities) {
        if (identities.isEmpty()) return Set.of();
        Set<Identity> result = new HashSet<>();
        try (Connection c = open(false)) {
            for (int offset = 0; offset < identities.size(); offset += 128) {
                List<Identity> batch = identities.subList(offset, Math.min(offset + 128, identities.size()));
                String values = String.join(",", java.util.stream.IntStream.range(0, batch.size())
                        .mapToObj(index -> "(" + index + ",?,?,?)").toList());
                String sql = "WITH requested(n,account_id,provider_id,signature) AS (VALUES " + values + ") "
                        + "SELECT DISTINCT r.n FROM requested r JOIN epg_channel_mapping m "
                        + "ON m.account_id=r.account_id AND m.provider_channel_id=r.provider_id AND m.signature=r.signature "
                        + "JOIN epg_source s ON s.source_id=m.source_id WHERE trim(coalesce(s.warning,''))<>''";
                try (var query = c.prepareStatement(sql)) {
                    int parameter = 1;
                    for (Identity identity : batch) {
                        query.setString(parameter++, identity.accountId());
                        query.setString(parameter++, identity.providerId());
                        query.setString(parameter++, identity.signature());
                    }
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) result.add(batch.get(rows.getInt(1)));
                    }
                }
            }
        } catch (Exception error) { throw failure(error); }
        return result;
    }
    public List<EpgProgramme> schedule(Identity identity, long from, long until) {
        try (Connection c=open(false)) {
            try(var query=c.prepareStatement("SELECT m.source_id,p.title,p.description,p.starts_at,p.stops_at FROM epg_channel_mapping m JOIN epg_programme p ON p.source_id=m.source_id AND p.guide_channel_id=m.guide_channel_id WHERE m.account_id=? AND m.provider_channel_id=? AND m.signature=? AND p.stops_at>? AND p.starts_at<? ORDER BY m.priority,m.source_id,p.starts_at LIMIT 8000")) {
                query.setString(1,identity.accountId()); query.setString(2,identity.providerId()); query.setString(3,identity.signature()); query.setLong(4,from); query.setLong(5,until);
                Map<String,List<EpgProgramme>> sources=new LinkedHashMap<>();
                try(var rows=query.executeQuery()) { while(rows.next()) sources.computeIfAbsent(rows.getString(1),_ -> new ArrayList<>()).add(new EpgProgramme(rows.getString(2),expand(rows.getBytes(3)),rows.getLong(4),rows.getLong(5))); }
                return com.uiptv.application.EpgSchedules.preferSources(new ArrayList<>(sources.values())).stream().limit(2000).toList();
            }
        } catch(Exception error) { throw failure(error); }
    }
    /** Artwork metadata only; image bytes are managed by the existing thumbnail cache. */
    public Map<Identity,String> icons(List<Identity> identities) {
        Map<Identity,String> result=new HashMap<>();
        try(Connection c=open(false)) {
            if(c==null) return result;
            for(int offset=0;offset<identities.size();offset+=128) {
                var batch=identities.subList(offset,Math.min(offset+128,identities.size()));
                String values=String.join(",",java.util.stream.IntStream.range(0,batch.size()).mapToObj(i -> "("+i+",?,?,?)").toList());
                String sql="WITH requested(n,account_id,provider_id,signature) AS (VALUES "+values+") SELECT r.n,i.icon_url FROM requested r JOIN epg_channel_mapping m ON m.account_id=r.account_id AND m.provider_channel_id=r.provider_id AND m.signature=r.signature JOIN epg_channel_icon i ON i.source_id=m.source_id AND i.guide_channel_id=m.guide_channel_id ORDER BY r.n,m.priority,m.source_id";
                try(var query=c.prepareStatement(sql)) {
                    int parameter=1;
                    for(var id:batch) { query.setString(parameter++,id.accountId()); query.setString(parameter++,id.providerId()); query.setString(parameter++,id.signature()); }
                    try(var rows=query.executeQuery()) { while(rows.next()) result.putIfAbsent(batch.get(rows.getInt(1)),rows.getString(2)); }
                }
            }
        } catch(Exception error) { throw failure(error); }
        return result;
    }
    public void prune(long now) {
        try(Connection c=open(true); var statement=c.prepareStatement("DELETE FROM epg_programme WHERE stops_at<?")) { statement.setLong(1,now-6*3_600_000L); statement.executeUpdate(); }
        catch(Exception error) { throw failure(error); }
    }
    public void clear() {
        clear(_ -> { });
    }
    public void clear(java.util.function.Consumer<String> progress) {
        EpgChangeNotifications.Notification notification;
        try(Connection c=open(true); var statement=c.createStatement()) {
            c.setAutoCommit(false);
            progress.accept("epgClearMapping");
            statement.executeUpdate("DELETE FROM epg_channel_mapping");
            progress.accept("epgClearProgrammes");
            statement.executeUpdate("DELETE FROM epg_programme");
            progress.accept("epgClearFinishing");
            for(String table:List.of("epg_alias","epg_channel_icon","epg_shared_source","epg_source"))
                statement.executeUpdate("DELETE FROM " + table);
            notification = EpgChangeNotifications.create(c, EpgChangeNotifications.Kind.CACHE_CLEARED, Set.of(), Set.of(), Set.of());
            c.commit();
        } catch(Exception error) { throw failure(error); }
        EpgChangeNotifications.publish(notification);
    }
    private static final class DescriptionCompressor implements AutoCloseable {
        private final java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
        private final byte[] buffer = new byte[4096];
        private byte[] empty;

        byte[] compress(String description) {
            String bounded = description == null ? "" : description.substring(0, Math.min(description.length(), 2048));
            if (bounded.isEmpty() && empty != null) return empty;
            byte[] input = bounded.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            deflater.reset();
            deflater.setInput(input);
            deflater.finish();
            var bytes = new java.io.ByteArrayOutputStream(Math.min(input.length + 32, 4096));
            while (!deflater.finished()) {
                int size = deflater.deflate(buffer);
                bytes.write(buffer, 0, size);
            }
            byte[] result = bytes.toByteArray();
            if (bounded.isEmpty()) empty = result;
            return result;
        }

        @Override public void close() { deflater.end(); }
    }
    private String expand(byte[] bytes) throws java.io.IOException {
        try(var input=new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes))) {
            byte[] text=input.readNBytes(8193);
            if(text.length>8192) throw new java.io.IOException("EPG description exceeds cache limit");
            return new String(text,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private DatabaseAccessException failure(Exception error) { return new DatabaseAccessException("EPG cache database operation failed",error); }

    private DatabaseAccessException interruptedFailure(InterruptedException error) {
        Thread.currentThread().interrupt();
        return failure(error);
    }
}
