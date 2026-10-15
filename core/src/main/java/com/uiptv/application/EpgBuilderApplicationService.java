package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.db.SQLConnection;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkResolver;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import static com.uiptv.application.EpgCacheApplicationService.hash;

/**
 * Headless Java bridge for EPG Builder.
 * Provides inventory export, bundle import, and validation operations.
 */
public class EpgBuilderApplicationService {

    private static String computeDatabaseIdentity(String databasePath) throws Exception {
        Path path = Path.of(databasePath).toRealPath();
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        return hash(path + "|" + Objects.toString(attributes.fileKey(), attributes.creationTime().toString()));
    }

    private static void selectDatabase(String databasePath) throws Exception {
        if (databasePath == null || !Files.isRegularFile(Path.of(databasePath))) {
            throw new IllegalArgumentException("Expected an existing UIPTV database at the resolved location");
        }
        // Read-only preflight. Do not initialize/migrate an arbitrary SQLite file.
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + Path.of(databasePath).toRealPath().toUri() + "?mode=ro");
             var query = c.createStatement()) {
            for (String table : List.of("Account", "Channel", "Configuration", "epg_source", "epg_programme", "epg_channel_mapping", "epg_name_exclusion")) {
                query.executeQuery("SELECT * FROM " + table + " LIMIT 0").close();
            }
        }
        SQLConnection.useExistingDatabase(Path.of(databasePath).toRealPath().toString());
    }

    private static String computeConfigSignature(Configuration config) {
        return hash(config.isEpgEnabled() + "|" + config.getGlobalEpgSource() + "|"
                + config.getEpgMappingsJson() + "|" + config.normalizedEpgRefreshHours() + "|" + new com.uiptv.db.EpgNameRulesDb().read().fingerprint());
    }

    public JSONObject exportInventory(String databasePath) throws Exception {
        JSONArray channels = new JSONArray();
        JSONObject inventory = visitInventory(databasePath, channels::put);
        return inventory.put("channels", channels);
    }

    /** One JSON object per line: metadata, channels, then a completion/revision record. */
    public void writeInventory(String databasePath, java.io.Writer output) throws Exception {
        writeInventory(databasePath, output, true);
    }

    public void writeInventoryJson(String databasePath, java.io.Writer output) throws Exception {
        writeInventory(databasePath, output, false);
    }

    private void writeInventory(String databasePath, java.io.Writer output, boolean lines) throws Exception {
        // Metadata is small; channels are written immediately and never collected here.
        JSONObject metadata = inventoryMetadata(databasePath);
        if (lines) output.write(metadata.put("record_type", "header").toString() + "\n");
        else {
            String header = metadata.toString();
            output.write(header.substring(0, header.length() - 1) + ",\"channels\":[");
        }
        long[] count = {0};
        JSONObject completed = visitInventory(databasePath, channel -> {
            try {
                if (lines) output.write(channel.put("record_type", "channel").toString() + "\n");
                else {
                    if (count[0] > 0) output.write(",");
                    channel.write(output);
                }
                count[0]++;
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        for (String key : List.of("database_identity", "config_signature")) {
            if (!metadata.getString(key).equals(completed.getString(key)))
                throw new IllegalStateException("Inventory configuration changed during export; retry");
        }
        if (lines) output.write(new JSONObject().put("record_type", "end").put("revision", completed.getString("revision"))
                .put("channel_count", count[0]).toString());
        else output.write("],\"revision\":" + JSONObject.quote(completed.getString("revision")) + "}");
        output.write("\n");
        output.flush();
    }

    private JSONObject inventoryMetadata(String databasePath) throws Exception {
        selectDatabase(databasePath);
        Configuration config = ConfigurationService.getInstance().read();
        JSONArray accounts = new JSONArray();
        for (Account account : AccountService.getInstance().getAllById().values()) {
            if (account.getAction() == Account.AccountAction.itv) accounts.put(new JSONObject()
                    .put("db_id", account.getDbId()).put("name", account.getAccountName())
                    .put("type", account.getType().name()).put("timezone", Objects.toString(account.getTimezone(), "Europe/London")));
        }
        return new JSONObject().put("database_path", databasePath).put("database_identity", computeDatabaseIdentity(databasePath))
                .put("generation", 1).put("config_signature", computeConfigSignature(config)).put("accounts", accounts);
    }

    private JSONObject visitInventory(String databasePath, java.util.function.Consumer<JSONObject> visitor) throws Exception {
        JSONObject inventory = inventoryMetadata(databasePath);
        Configuration config = ConfigurationService.getInstance().read();
        Map<String, Account> accounts = AccountService.getInstance().getAllById();

        List<String> revisions = new ArrayList<>();
        BookmarkResolver resolver = new BookmarkResolver();

        for (Account account : accounts.values()) {
            if (account.getAction() != Account.AccountAction.itv) continue;


            List<com.uiptv.model.Bookmark> bookmarks = BookmarkService.getInstance()
                    .getBookmarksByAccountName(account.getAccountName());

            var resolved = resolver.resolveBookmarks(bookmarks);

            Set<String> seen = new HashSet<>();
            Map<String, String> bookmarkIds = new HashMap<>();
            for (var rb : resolved) {
                if (rb.getAccountAction() != Account.AccountAction.itv || rb.getBookmark() == null) continue;
                var bookmark = rb.getBookmark();
                bookmarkIds.put(bookmark.getChannelId(), bookmark.getDbId());
            }
            var identity = EpgCacheApplicationService.identityFactory(account, config);
            var overrides = EpgCacheApplicationService.overrides(config, account);
            java.util.function.Consumer<Channel> emit = channel -> {
                if (!seen.add(channel.getChannelId())) return;
                JSONObject record = channelInventory(account, channel, bookmarkIds.containsKey(channel.getChannelId()),
                        bookmarkIds.getOrDefault(channel.getChannelId(), ""), overrides.optString(channel.getChannelId(), "").strip(), identity.apply(channel).signature());
                revisions.add(record.getString("account_id") + "|" + record.getString("provider_channel_id") + "|" + record.getString("signature"));
                visitor.accept(record);
            };
            com.uiptv.db.ChannelDb.get().visitCachedLiveChannels(account.getDbId(), emit);
            for (var rb : resolved) {
                if (rb.getAccountAction() != Account.AccountAction.itv || rb.getBookmark() == null) continue;
                var bookmark = rb.getBookmark();
                if (seen.contains(bookmark.getChannelId())) continue;
                Channel channel = Channel.fromJson(bookmark.getChannelJson());
                if (channel == null) channel = new Channel();
                channel.setChannelId(bookmark.getChannelId());
                channel.setName(bookmark.getChannelName());
                emit.accept(channel);
            }
        }

        Collections.sort(revisions);
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < revisions.size(); i++) {
            if (i > 0) digest.update((byte) '\n');
            digest.update(revisions.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        inventory.put("revision", HexFormat.of().formatHex(digest.digest()));
        return inventory;
    }

    private static JSONObject channelInventory(Account account, Channel channel,
                                                boolean isBookmark, String bookmarkId,
                                                String manualOverride, String signature) {
        String tvgId = "", xmltvId = "", epgId = "", epgChannelId = "";
        try {
            JSONObject metadata = new JSONObject(channel.getExtraJson() != null ? channel.getExtraJson() : "{}");
            if (account.getType() == AccountType.XTREME_API) {
                epgChannelId = metadata.optString("epg_channel_id", "");
            } else {
                xmltvId = metadata.optString("xmltv_id", "");
                epgId = metadata.optString("epg_id", "");
                tvgId = metadata.optString("tvg-id", "");
            }
        } catch (Exception ignored) {}

        boolean isManual = !manualOverride.isBlank();

        JSONObject ch = new JSONObject();
        ch.put("account_id", account.getDbId());
        ch.put("provider_channel_id", channel.getChannelId());
        ch.put("db_id", channel.getDbId() != null ? channel.getDbId() : "");
        ch.put("name", channel.getName() != null ? channel.getName() : "");
        ch.put("display_name", channel.getName() != null ? channel.getName() : "");
        ch.put("country", "");
        ch.put("language", "");
        ch.put("tvg_id", tvgId);
        ch.put("xmltv_id", xmltvId);
        ch.put("epg_id", epgId);
        ch.put("epg_channel_id", epgChannelId);
        ch.put("guide_id", EpgApplicationService.guideId(account, channel));
        // Raw provider JSON may contain private stream URLs and credentials.
        ch.put("extra_json", "");
        ch.put("signature", signature);
        ch.put("is_bookmark", isBookmark);
        ch.put("bookmark_id", bookmarkId);
        ch.put("manual_override", manualOverride);
        ch.put("is_manual_override", isManual);

        return ch;
    }

    private record Prepared(Map<String,Map<String,List<com.uiptv.application.EpgProgramme>>> sources,
                            List<EpgCacheDb.BuilderMatch> matches, int programmes) { }

    private Prepared prepare(String databasePath, JSONObject bundle) throws Exception {
        if (bundle.getInt("schema_version") != 1) throw new IllegalArgumentException("Unsupported bundle schema");
        record Target(String account, String provider) { }
        Set<Target> wanted = new HashSet<>();
        for (Object value : bundle.getJSONArray("mappings")) {
            JSONObject mapping = (JSONObject) value;
            wanted.add(new Target(mapping.getString("account_id"), mapping.getString("provider_channel_id")));
        }
        Map<Target,JSONObject> targets = new HashMap<>();
        JSONObject inventory = visitInventory(databasePath, channel -> {
            Target target = new Target(channel.getString("account_id"), channel.getString("provider_channel_id"));
            if (wanted.contains(target)) targets.put(target, channel);
        });
        for (String key : List.of("database_identity", "config_signature")) {
            if (!bundle.getString(key).equals(inventory.getString(key))) throw new IllegalArgumentException(key + " mismatch");
        }
        if (!bundle.getString("inventory_revision").equals(inventory.getString("revision"))
                || bundle.getInt("inventory_generation") != inventory.getInt("generation")) {
            throw new IllegalArgumentException("Stale inventory; export and rebuild");
        }
        Map<String,Map<String,List<com.uiptv.application.EpgProgramme>>> sources = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        long created = bundle.has("created_at") ? java.time.Instant.parse(bundle.getString("created_at")).toEpochMilli() : now;
        if (created > now + 300_000L || created < now - 86_400_000L)
            throw new IllegalArgumentException("Bundle timestamp is stale or in the future; rebuild");
        JSONArray programmes = bundle.getJSONArray("programmes");
        if (programmes.isEmpty() || programmes.length() > 200_000) throw new IllegalArgumentException("Empty or oversized programmes");
        Map<String,Integer> declared = new HashMap<>();
        for (Object value : bundle.getJSONArray("sources")) {
            JSONObject source = (JSONObject)value;
            int priority = source.optInt("priority", 100);
            if (priority < 0 || priority > 1_000_000 || declared.putIfAbsent(source.getString("source_id"), priority) != null)
                throw new IllegalArgumentException("Invalid source priority or duplicate source");
        }
        for (Object value : programmes) {
            JSONObject p = (JSONObject)value;
            String source = p.getString("source_id"), guide = p.getString("guide_channel_id");
            long start = p.getLong("start"), stop = p.getLong("stop");
            String title = p.getString("title"), description = p.optString("description", "");
            if (!declared.containsKey(source) || source.isBlank() || guide.isBlank())
                throw new IllegalArgumentException("Invalid programme source or guide identity");
            if (title.isBlank() || title.length() > 512 || description.length() > 2048)
                throw new IllegalArgumentException("Invalid programme title or description length");
            if (stop <= start) throw new IllegalArgumentException("Invalid programme interval: stop must follow start");
            if (stop <= created - 6 * 3_600_000L || start >= created + 7 * 86_400_000L)
                throw new IllegalArgumentException("Programme outside the supported time window; rebuild the bundle");
            // A long catalogue revalidation can cross a retention boundary.
            if (stop <= now - 6 * 3_600_000L || start >= now + 7 * 86_400_000L) continue;
            sources.computeIfAbsent("builder:" + hash(source), _ -> new LinkedHashMap<>())
                    .computeIfAbsent(guide, _ -> new ArrayList<>())
                    .add(new com.uiptv.application.EpgProgramme(title, description, start, stop));
        }
        sources.values().forEach(channels -> channels.replaceAll((guide, schedule) -> com.uiptv.application.EpgSchedules.normalize(schedule)));
        List<EpgCacheDb.BuilderMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object value : bundle.getJSONArray("mappings")) {
            JSONObject m = (JSONObject)value;
            Target target = new Target(m.getString("account_id"), m.getString("provider_channel_id"));
            JSONObject current = targets.get(target);
            String source = "builder:" + hash(m.getString("source_id")), guide = m.getString("guide_channel_id");
            if (current == null || !seen.add(target + "|" + source) || !current.getString("signature").equals(m.getString("signature"))
                    || !sources.containsKey(source) || !sources.get(source).containsKey(guide)
                    || sources.get(source).get(guide).stream().noneMatch(p -> p.stop() > now))
                throw new IllegalArgumentException("Unknown, duplicate, stale or empty mapping");
            if (current.getBoolean("is_manual_override") && !current.getString("manual_override").equals(guide))
                throw new IllegalArgumentException("Cannot overwrite manual override");
            matches.add(new EpgCacheDb.BuilderMatch(new EpgCacheDb.Identity(target.account(), target.provider(), m.getString("signature")),
                    source, guide, declared.get(m.getString("source_id"))));
        }
        if (matches.isEmpty()) throw new IllegalArgumentException("Bundle has no usable mappings");
        return new Prepared(sources, matches, programmes.length());
    }

    private static JSONObject readBundle(String bundlePath) throws Exception {
        if (bundlePath == null || Files.size(Path.of(bundlePath)) > 128 * 1024 * 1024L)
            throw new IllegalArgumentException("--bundle must name a file smaller than 128 MiB");
        return new JSONObject(Files.readString(Path.of(bundlePath)));
    }

    public JSONObject importBundle(String databasePath, String bundlePath) throws Exception {
        selectDatabase(databasePath);
        JSONObject bundle = readBundle(bundlePath);
        Prepared prepared = prepare(databasePath, bundle);
        new EpgCacheDb().publishBuilder(prepared.sources(), prepared.matches(), System.currentTimeMillis(), () -> {
            try { prepare(databasePath, bundle); }
            catch (Exception error) { throw new IllegalArgumentException("Inventory changed before publication", error); }
        });
        return new JSONObject().put("success", true).put("sources_imported", prepared.sources().size())
                .put("mappings_applied", prepared.matches().size()).put("programmes_imported", prepared.programmes());
    }

    public JSONObject validateBundle(String databasePath, String bundlePath) throws Exception {
        selectDatabase(databasePath);
        prepare(databasePath, readBundle(bundlePath));
        return new JSONObject().put("valid", true).put("warnings", new JSONArray());
    }

    /** Parse with the same bounded, XXE-disabled reader as the application. */
    public JSONObject parseGuide(String requestPath) throws Exception {
        JSONObject request = readBundle(requestPath);
        Path file = Path.of(request.getString("path"));
        if (Files.size(file) > 64 * 1024 * 1024L) throw new IllegalArgumentException("XMLTV exceeds 64 MiB");
        XmltvGuide guide;
        try (var input = Files.newInputStream(file)) {
            guide = XmltvGuide.read(input, java.time.Instant.ofEpochMilli(request.getLong("now")));
        }
        JSONObject programmes = new JSONObject();
        guide.programmes().forEach((id, entries) -> {
            JSONArray values = new JSONArray();
            for (EpgProgramme p : EpgSchedules.normalize(entries)) values.put(new JSONObject().put("title", p.title()).put("description", p.description())
                    .put("start", p.start()).put("stop", p.stop()));
            programmes.put(id, values);
        });
        return new JSONObject().put("programmes", programmes).put("names", guide.names())
                .put("quality_names", guide.qualityNames()).put("icons", guide.icons());
    }

    /** Reuse the same interval resolver as desktop/API reads; no database is opened. */
    public JSONObject mergeSchedules(String requestPath) throws Exception {
        JSONObject request = readBundle(requestPath);
        JSONArray result = new JSONArray();
        for (Object value : request.getJSONArray("schedules")) {
            List<List<EpgProgramme>> sources = new ArrayList<>();
            for (Object source : (JSONArray)value) {
                List<EpgProgramme> programmes = new ArrayList<>();
                for (Object entry : (JSONArray)source) {
                    JSONObject p = (JSONObject)entry;
                    programmes.add(new EpgProgramme(p.getString("title"), p.optString("description", ""), p.getLong("start"), p.getLong("stop")));
                }
                sources.add(programmes);
            }
            JSONArray schedule = new JSONArray();
            for (EpgProgramme p : EpgSchedules.preferSources(sources)) {
                schedule.put(new JSONObject().put("title", p.title()).put("description", p.description()).put("start", p.start()).put("stop", p.stop()));
            }
            result.put(schedule);
        }
        return new JSONObject().put("schedules", result);
    }

    public JSONObject showStatus(String databasePath) throws Exception {
        if (databasePath == null || databasePath.isBlank()) {
            throw new IllegalArgumentException("UIPTV database path could not be resolved");
        }

        selectDatabase(databasePath);

        Configuration config = ConfigurationService.getInstance().read();

        JSONObject result = new JSONObject();
        result.put("epg_enabled", config.isEpgEnabled());
        result.put("database_path", databasePath);
        result.put("database_identity", computeDatabaseIdentity(databasePath));

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + Path.of(databasePath).toRealPath().toUri() + "?mode=ro")) {
            for (var entry : Map.of("source_count", "epg_source", "programme_count", "epg_programme", "mapping_count", "epg_channel_mapping").entrySet()) {
                try (var query = c.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + entry.getValue())) {
                    rows.next(); result.put(entry.getKey(), rows.getLong(1));
                }
            }
        }

        return result;
    }
}
