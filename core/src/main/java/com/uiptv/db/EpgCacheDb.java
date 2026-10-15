package com.uiptv.db;

import com.uiptv.application.EpgProgramme;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Indexed EPG cache tables in the application database. Read paths never initialize, mutate or download anything. */
public final class EpgCacheDb {
    public record Identity(String accountId, String providerId, String signature) { }
    public record Match(Identity identity, String guideId, String name, String qualityName, boolean explicit,
                        List<String> sources, String nativeSource) { }
    public record SourceState(long refreshedAt, long retryAt, String warning) { }
    public static Path path() { return Path.of(SQLConnection.getDatabasePath()); }

    private Connection open(boolean write) throws Exception {
        Connection connection=write ? SQLConnection.connect()
                : DriverManager.getConnection("jdbc:sqlite:"+path().toUri()+"?mode=ro");
        try(Statement statement=connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=3000");
            statement.execute("PRAGMA cache_size=-2048");
            if(!write) statement.execute("PRAGMA query_only=ON");
            return connection;
        } catch(Exception error) { connection.close(); throw error; }
    }

    public SourceState sourceState(String sourceId) {
        try (Connection c = open(false)) {
            if (c == null) return new SourceState(0, 0, "");
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
        replaceSource(sourceId,programmes,aliases,qualityAliases,Map.of(),now,retryAt);
    }
    public void replaceSource(String sourceId,Map<String,List<EpgProgramme>> programmes,
                              Map<String,String> aliases,Map<String,String> qualityAliases,Map<String,String> icons,long now,long retryAt) {
        try (Connection c = open(true)) {
            c.setAutoCommit(false);
            try {
                deleteSourceRows(c,"epg_programme",sourceId); deleteSourceRows(c,"epg_alias",sourceId); deleteSourceRows(c,"epg_channel_icon",sourceId);
                try (var insert = c.prepareStatement("INSERT INTO epg_programme VALUES(?,?,?,?,?,?)")) {
                    int batch = 0;
                    for (var entry : programmes.entrySet()) for (var p : entry.getValue()) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        if (p.stop() <= p.start() || p.title().isBlank() || p.stop() <= now - 6 * 3_600_000L || p.start() >= now + 7 * 86_400_000L) continue;
                        insert.setString(1, sourceId); insert.setString(2, entry.getKey()); insert.setLong(3, p.start()); insert.setLong(4, p.stop());
                        insert.setString(5, p.title()); insert.setBytes(6, compress(p.description())); insert.addBatch();
                        if (++batch % 500 == 0) insert.executeBatch();
                    }
                    insert.executeBatch();
                }
                try (var insert = c.prepareStatement("INSERT INTO epg_alias VALUES(?,?,?,?)")) {
                    int batch = 0;
                    for (int kind = 0; kind < 2; kind++) for (var alias : (kind == 0 ? aliases : qualityAliases).entrySet()) {
                        insert.setString(1, sourceId); insert.setInt(2, kind); insert.setString(3, alias.getKey()); insert.setString(4, alias.getValue()); insert.addBatch();
                        if (++batch % 500 == 0) insert.executeBatch();
                    }
                    insert.executeBatch();
                }
                try(var insert=c.prepareStatement("INSERT INTO epg_channel_icon VALUES(?,?,?)")) {
                    int batch=0;
                    for(var icon:icons.entrySet()) {
                        insert.setString(1,sourceId); insert.setString(2,icon.getKey()); insert.setString(3,icon.getValue()); insert.addBatch();
                        if(++batch%500==0) insert.executeBatch();
                    }
                    insert.executeBatch();
                }
                state(c, sourceId, now, retryAt, ""); c.commit();
            } catch (Exception error) { c.rollback(); throw error; }
        } catch (Exception error) { if (error instanceof InterruptedException) Thread.currentThread().interrupt(); throw failure(error); }
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
    private record Link(String signature,String source,String guide,int priority) { }
    public boolean mapChannels(List<Match> matches,long now) {
        boolean changed=false;
        try (Connection c = open(true)) {
            c.setAutoCommit(false);
            try (var direct = c.prepareStatement("WITH target(source,id,now) AS (VALUES(?,?,?)) SELECT id FROM target t WHERE EXISTS(SELECT 1 FROM epg_programme WHERE source_id=t.source AND guide_channel_id=t.id AND stops_at>t.now) OR EXISTS(SELECT 1 FROM epg_channel_icon WHERE source_id=t.source AND guide_channel_id=t.id) LIMIT 1");
                 var alias = c.prepareStatement("SELECT guide_channel_id FROM epg_alias WHERE source_id=? AND kind=? AND name=?");
                 var existing=c.prepareStatement("SELECT signature,source_id,guide_channel_id,priority FROM epg_channel_mapping WHERE account_id=? AND provider_channel_id=? ORDER BY priority");
                 var delete = c.prepareStatement("DELETE FROM epg_channel_mapping WHERE account_id=? AND provider_channel_id=?");
                 var insert = c.prepareStatement("INSERT INTO epg_channel_mapping VALUES(?,?,?,?,?,?)")) {
                for (Match match : matches) {
                    List<Link> links=new ArrayList<>();
                    int priority=0;
                    for (String candidate : match.sources()) {
                        String guide="";
                        direct.setString(1,candidate); direct.setString(2,candidate.equals(match.nativeSource()) ? match.identity().providerId() : match.guideId()); direct.setLong(3,now);
                        try (var row = direct.executeQuery()) { if (row.next()) guide = row.getString(1); }
                        if (guide.isBlank() && !match.explicit()) for (int kind=0; kind<2 && guide.isBlank(); kind++) {
                            alias.setString(1,candidate); alias.setInt(2,kind); alias.setString(3,kind==0 ? match.name() : match.qualityName());
                            try (var row=alias.executeQuery()) { if (row.next()) {
                                String id = row.getString(1); direct.setString(2,id);
                                try (var programmes = direct.executeQuery()) { if (programmes.next()) guide=id; }
                            } }
                        }
                        if (!guide.isBlank()) {
                            links.add(new Link(match.identity().signature(),candidate,guide,priority));
                        }
                        priority++;
                    }
                    List<Link> previous=new ArrayList<>();
                    existing.setString(1,match.identity().accountId()); existing.setString(2,match.identity().providerId());
                    try(var rows=existing.executeQuery()) { while(rows.next()) previous.add(new Link(rows.getString(1),rows.getString(2),rows.getString(3),rows.getInt(4))); }
                    if(previous.equals(links)) continue;
                    changed=true;
                    delete.setString(1,match.identity().accountId()); delete.setString(2,match.identity().providerId()); delete.executeUpdate();
                    for(Link link:links) {
                        insert.setString(1,match.identity().accountId()); insert.setString(2,match.identity().providerId()); insert.setString(3,link.signature());
                        insert.setString(4,link.source()); insert.setString(5,link.guide()); insert.setInt(6,link.priority()); insert.addBatch();
                    }
                }
                insert.executeBatch(); c.commit();
            } catch (Exception error) { c.rollback(); throw error; }
        } catch (Exception error) { throw failure(error); }
        return changed;
    }

    private record Selection(String source, List<EpgProgramme> programmes) { }
    private Map<Identity,Selection> select(Connection c,List<Identity> identities,long now) throws SQLException {
        Map<Identity,Selection> result=new HashMap<>();
        for(int offset=0;offset<identities.size();offset+=128) {
            List<Identity> batch=identities.subList(offset,Math.min(offset+128,identities.size()));
            String values=String.join(",",java.util.stream.IntStream.range(0,batch.size()).mapToObj(i -> "("+i+",?,?,?)").toList());
            String sql="WITH requested(n,account_id,provider_id,signature) AS (VALUES "+values+") SELECT r.n,m.source_id,p.title,p.starts_at,p.stops_at FROM requested r JOIN epg_channel_mapping m ON m.account_id=r.account_id AND m.provider_channel_id=r.provider_id AND m.signature=r.signature JOIN epg_programme p ON p.source_id=m.source_id AND p.guide_channel_id=m.guide_channel_id AND p.starts_at IN (SELECT q.starts_at FROM epg_programme q WHERE q.source_id=m.source_id AND q.guide_channel_id=m.guide_channel_id AND q.stops_at>? ORDER BY q.starts_at LIMIT 2) ORDER BY r.n,m.priority,p.starts_at";
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
            if(c!=null) select(c,identities,now).forEach((id,selection) -> result.put(id,selection.programmes()));
        } catch(Exception error) { throw failure(error); }
        return result;
    }
    public List<EpgProgramme> schedule(Identity identity, long now, long from, long until) {
        try (Connection c=open(false)) {
            if(c==null) return List.of();
            try(var query=c.prepareStatement("SELECT m.source_id,p.title,p.description,p.starts_at,p.stops_at FROM epg_channel_mapping m JOIN epg_programme p ON p.source_id=m.source_id AND p.guide_channel_id=m.guide_channel_id WHERE m.account_id=? AND m.provider_channel_id=? AND m.signature=? AND p.stops_at>? AND p.starts_at<? ORDER BY m.priority,p.starts_at LIMIT 8000")) {
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
                String sql="WITH requested(n,account_id,provider_id,signature) AS (VALUES "+values+") SELECT r.n,i.icon_url FROM requested r JOIN epg_channel_mapping m ON m.account_id=r.account_id AND m.provider_channel_id=r.provider_id AND m.signature=r.signature JOIN epg_channel_icon i ON i.source_id=m.source_id AND i.guide_channel_id=m.guide_channel_id ORDER BY r.n,m.priority";
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
        try(Connection c=open(true); var statement=c.createStatement()) {
            c.setAutoCommit(false);
            for(String table:List.of("epg_channel_mapping","epg_programme","epg_alias","epg_channel_icon","epg_source")) statement.executeUpdate("DELETE FROM " + table);
            c.commit();
        } catch(Exception error) { throw failure(error); }
    }
    private byte[] compress(String description) throws java.io.IOException {
        String bounded=description==null ? "" : description.substring(0,Math.min(description.length(),2048));
        var bytes=new java.io.ByteArrayOutputStream();
        try(var output=new java.util.zip.DeflaterOutputStream(bytes)) { output.write(bounded.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }
    private String expand(byte[] bytes) throws java.io.IOException {
        try(var input=new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes))) {
            byte[] text=input.readNBytes(8193);
            if(text.length>8192) throw new java.io.IOException("EPG description exceeds cache limit");
            return new String(text,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private DatabaseAccessException failure(Exception error) { return new DatabaseAccessException("EPG cache database operation failed",error); }
}
