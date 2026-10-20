package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.db.SQLConnection;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
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
// SQL identifiers are fixed allowlisted schema names; external values are never interpolated.
@SuppressWarnings("java:S2077")
public class EpgBuilderApplicationService {
    private static final String DATABASE_IDENTITY = "database_identity";
    private static final String CONFIG_SIGNATURE = "config_signature";
    private static final String REVISION = "revision";
    private static final String SOURCE_ID = "source_id";
    private static final String NAME_RULES = "name_rules";
    private static final String RECORD_TYPE = "record_type";
    private static final String ACCOUNT_ID = "account_id";
    private static final String PROVIDER_CHANNEL_ID = "provider_channel_id";
    private static final String START = "start";
    private static final String DESCRIPTION = "description";
    private static final String TITLE = "title";
    private static final String SIGNATURE = "signature";
    private static final String BUILDER_PREFIX = "builder:";

    private static String computeDatabaseIdentity(String databasePath) throws java.io.IOException {
        Path path = Path.of(databasePath).toRealPath();
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        return hash(path + "|" + Objects.toString(attributes.fileKey(), attributes.creationTime().toString()));
    }

    private static void selectDatabase(String databasePath) throws java.io.IOException, java.sql.SQLException {
        if (databasePath == null || !Files.isRegularFile(Path.of(databasePath))) {
            throw new IllegalArgumentException("Expected an existing UIPTV database at the resolved location");
        }
        // Read-only preflight. Do not initialize/migrate an arbitrary SQLite file.
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + Path.of(databasePath).toRealPath().toUri() + "?mode=ro");
             var query = c.createStatement()) {
            for (String table : List.of("Account", "Channel", "Configuration", "epg_source", "epg_programme", "epg_channel_mapping", "epg_name_exclusion", "epg_shared_source", "epg_change_state", "epg_change_history")) {
                query.executeQuery("SELECT * FROM " + table + " LIMIT 0").close();
            }
        }
        SQLConnection.useExistingDatabase(Path.of(databasePath).toRealPath().toString());
    }

    private static String computeConfigSignature(Configuration config) {
        return hash(config.isEpgEnabled() + "|" + config.getGlobalEpgSource() + "|"
                + config.getEpgMappingsJson() + "|" + config.normalizedEpgRefreshHours() + "|"
                + config.normalizedEpgSchedulerIntervalMinutes() + "|" + new com.uiptv.db.EpgNameRulesDb().read().fingerprint()
                + "|" + EpgTerritory.fingerprintValue());
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
        if (lines) output.write(metadata.put(RECORD_TYPE, "header").toString() + "\n");
        else {
            String header = metadata.toString();
            output.write(header.substring(0, header.length() - 1) + ",\"channels\":[");
        }
        long[] count = {0};
        JSONObject completed = visitInventory(databasePath, channel -> {
            try {
                if (lines) output.write(channel.put(RECORD_TYPE, "channel").toString() + "\n");
                else {
                    if (count[0] > 0) output.write(",");
                    channel.write(output);
                }
                count[0]++;
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        for (String key : List.of(DATABASE_IDENTITY, CONFIG_SIGNATURE)) {
            if (!metadata.getString(key).equals(completed.getString(key)))
                throw new IllegalStateException("Inventory configuration changed during export; retry");
        }
        if (lines) output.write(new JSONObject().put(RECORD_TYPE, "end").put(REVISION, completed.getString(REVISION))
                .put("channel_count", count[0]).toString());
        else output.write("],\"revision\":" + JSONObject.quote(completed.getString(REVISION)) + "}");
        output.write("\n");
        output.flush();
    }

    private JSONObject inventoryMetadata(String databasePath) throws java.io.IOException, java.sql.SQLException {
        selectDatabase(databasePath);
        Configuration config = ConfigurationService.getInstance().read();
        JSONArray accounts = new JSONArray();
        for (Account account : AccountService.getInstance().getAllById().values()) {
            if (account.getAction() == Account.AccountAction.itv) accounts.put(new JSONObject()
                    .put("db_id", account.getDbId()).put("name", account.getAccountName())
                    .put("type", account.getType().name()).put("timezone", Objects.toString(account.getTimezone(), "Europe/London")));
        }
        JSONArray nameRules = new JSONArray();
        new com.uiptv.db.EpgNameRulesDb().read().rules().forEach(rule -> nameRules.put(
                new JSONObject().put("term", rule.term()).put("scope", rule.scope())));
        return new JSONObject().put(NAME_RULES, nameRules).put("database_path", databasePath).put(DATABASE_IDENTITY, computeDatabaseIdentity(databasePath))
                .put("generation", 1).put(CONFIG_SIGNATURE, computeConfigSignature(config)).put("accounts", accounts);
    }

    private JSONObject visitInventory(String databasePath, java.util.function.Consumer<JSONObject> visitor)
            throws java.io.IOException, java.sql.SQLException, java.security.NoSuchAlgorithmException {
        JSONObject inventory = inventoryMetadata(databasePath);
        Configuration config = ConfigurationService.getInstance().read();
        Map<String, Account> accounts = AccountService.getInstance().getAllById();

        var nameRules = new com.uiptv.db.EpgNameRulesDb().read();
        List<String> revisions = new ArrayList<>();
        BookmarkResolver resolver = new BookmarkResolver();

        for (Account account : accounts.values()) {
            if (account.getAction() == Account.AccountAction.itv) {
                visitAccountInventory(account, config, nameRules, resolver, revisions, visitor);
            }
        }

        Collections.sort(revisions);
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < revisions.size(); i++) {
            if (i > 0) digest.update((byte) '\n');
            digest.update(revisions.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        inventory.put(REVISION, HexFormat.of().formatHex(digest.digest()));
        return inventory;
    }

    private void visitAccountInventory(Account account, Configuration config,
                                       EpgNameRules nameRules, BookmarkResolver resolver,
                                       List<String> revisions, java.util.function.Consumer<JSONObject> visitor) {
        List<com.uiptv.model.Bookmark> bookmarks = BookmarkService.getInstance()
                .getBookmarksByAccountName(account.getAccountName());
        var resolved = resolver.resolveBookmarks(bookmarks);
        Set<String> seen = new HashSet<>();
        Map<String, String> bookmarkIds = bookmarkIds(resolved);
        var identity = EpgCacheApplicationService.identityFactory(account, config);
        var overrides = EpgCacheApplicationService.overrides(config, account);
        java.util.function.Consumer<Channel> emit = channel -> {
            if (!seen.add(channel.getChannelId())) return;
            JSONObject channelRecord = channelInventory(account, channel, bookmarkIds.containsKey(channel.getChannelId()),
                    bookmarkIds.getOrDefault(channel.getChannelId(), ""), overrides.optString(channel.getChannelId(), "").strip(), identity.apply(channel).signature());
            channelRecord.put("quality_name", nameRules.quality(channel.getName()));
            revisions.add(channelRecord.getString(ACCOUNT_ID) + "|" + channelRecord.getString(PROVIDER_CHANNEL_ID) + "|" + channelRecord.getString(SIGNATURE));
            visitor.accept(channelRecord);
        };
        com.uiptv.db.ChannelDb.get().visitCachedLiveChannels(account.getDbId(), emit);
        emitBookmarkChannels(resolved, seen, emit);
    }

    private static Map<String, String> bookmarkIds(List<BookmarkResolver.ResolvedBookmark> resolved) {
        Map<String, String> ids = new HashMap<>();
        for (var resolvedBookmark : resolved) {
            var bookmark = resolvedBookmark.getBookmark();
            if (resolvedBookmark.getAccountAction() == Account.AccountAction.itv && bookmark != null) {
                ids.put(bookmark.getChannelId(), bookmark.getDbId());
            }
        }
        return ids;
    }

    private static void emitBookmarkChannels(List<BookmarkResolver.ResolvedBookmark> resolved, Set<String> seen,
                                             java.util.function.Consumer<Channel> emit) {
        for (var resolvedBookmark : resolved) {
            var bookmark = resolvedBookmark.getBookmark();
            if (resolvedBookmark.getAccountAction() != Account.AccountAction.itv || bookmark == null
                    || seen.contains(bookmark.getChannelId())) continue;
            Channel channel = Channel.fromJson(bookmark.getChannelJson());
            if (channel == null) channel = new Channel();
            channel.setChannelId(bookmark.getChannelId());
            channel.setName(bookmark.getChannelName());
            emit.accept(channel);
        }
    }

    private static JSONObject channelInventory(Account account, Channel channel,
                                                boolean isBookmark, String bookmarkId,
                                                String manualOverride, String signature) {
        String tvgId = "";
        String xmltvId = "";
        String epgId = "";
        String epgChannelId = "";
        try {
            JSONObject metadata = new JSONObject(channel.getExtraJson() != null ? channel.getExtraJson() : "{}");
            if (account.getType() == AccountType.XTREME_API) {
                epgChannelId = metadata.optString("epg_channel_id", "");
            } else {
                xmltvId = metadata.optString("xmltv_id", "");
                epgId = metadata.optString("epg_id", "");
                tvgId = metadata.optString("tvg-id", "");
            }
        } catch (org.json.JSONException error) {
            com.uiptv.util.AppLog.addWarningLog(EpgBuilderApplicationService.class,
                    "Ignoring invalid optional channel metadata: " + error.getMessage());
        }

        boolean isManual = !manualOverride.isBlank();

        JSONObject ch = new JSONObject();
        ch.put(ACCOUNT_ID, account.getDbId());
        ch.put(PROVIDER_CHANNEL_ID, channel.getChannelId());
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
        ch.put(SIGNATURE, signature);
        ch.put("is_bookmark", isBookmark);
        ch.put("bookmark_id", bookmarkId);
        ch.put("manual_override", manualOverride);
        ch.put("is_manual_override", isManual);

        return ch;
    }

    private record Prepared(Map<String,Map<String,List<com.uiptv.application.EpgProgramme>>> sources,
                            List<EpgCacheDb.BuilderMatch> matches, Map<String,EpgCacheDb.SharedSource> shared, int programmes) { }

    private record Target(String account, String provider) { }

    private record TargetInventory(Map<Target, JSONObject> targets, JSONObject inventory) { }

    private Prepared prepare(String databasePath, JSONObject bundle) throws Exception {
        validateSchema(bundle);
        boolean sharedGuides = bundle.optBoolean("shared_guides", false);
        TargetInventory targetInventory = readTargetInventory(databasePath, bundle, sharedGuides);
        JSONObject inventory = targetInventory.inventory();
        validateInventory(bundle, inventory, sharedGuides);
        boolean mappingsCurrent = mappingsCurrent(bundle, inventory);
        Map<String,Map<String,List<com.uiptv.application.EpgProgramme>>> sources = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        long created = bundleCreatedAt(bundle, now);
        Map<String,Integer> declared = declaredPriorities(bundle.getJSONArray("sources"));
        JSONArray programmes = bundle.getJSONArray("programmes");
        validateProgrammeCount(programmes);
        readProgrammes(programmes, declared, created, now, sources);
        sources.values().forEach(channels -> channels.replaceAll((guide, schedule) -> com.uiptv.application.EpgSchedules.normalize(schedule)));
        List<EpgCacheDb.BuilderMatch> matches = readMatches(bundle, mappingsCurrent, targetInventory.targets(), sources, declared, now);
        Map<String,EpgCacheDb.SharedSource> shared = readSharedSources(bundle, sources);
        if (matches.isEmpty() && shared.isEmpty()) throw new IllegalArgumentException("Bundle has no usable mappings or shared guides");
        return new Prepared(sources, matches, shared, programmes.length());
    }

    private static void validateSchema(JSONObject bundle) {
        if (bundle.getInt("schema_version") != 1) throw new IllegalArgumentException("Unsupported bundle schema");
    }

    private TargetInventory readTargetInventory(String databasePath, JSONObject bundle, boolean sharedGuides) throws Exception {
        Set<Target> wanted = new HashSet<>();
        for (Object value : bundle.getJSONArray("mappings")) {
            JSONObject mapping = (JSONObject) value;
            wanted.add(new Target(mapping.getString(ACCOUNT_ID), mapping.getString(PROVIDER_CHANNEL_ID)));
        }
        Map<Target, JSONObject> targets = new HashMap<>();
        JSONObject inventory = sharedGuides && wanted.isEmpty() ? inventoryMetadata(databasePath) : visitInventory(databasePath, channel -> {
            Target target = new Target(channel.getString(ACCOUNT_ID), channel.getString(PROVIDER_CHANNEL_ID));
            if (wanted.contains(target)) targets.put(target, channel);
        });
        return new TargetInventory(targets, inventory);
    }

    private static void validateInventory(JSONObject bundle, JSONObject inventory, boolean sharedGuides) {
        if (!bundle.getString(DATABASE_IDENTITY).equals(inventory.getString(DATABASE_IDENTITY)))
            throw new IllegalArgumentException("database_identity mismatch");
        if (!sharedGuides && !mappingsCurrent(bundle, inventory))
            throw new IllegalArgumentException("Stale inventory or config_signature; export and rebuild");
    }

    private static boolean mappingsCurrent(JSONObject bundle, JSONObject inventory) {
        return bundle.getString(CONFIG_SIGNATURE).equals(inventory.getString(CONFIG_SIGNATURE))
                && bundle.getString("inventory_revision").equals(inventory.optString(REVISION))
                && bundle.getInt("inventory_generation") == inventory.getInt("generation");
    }

    private static long bundleCreatedAt(JSONObject bundle, long now) {
        long created = bundle.has("created_at") ? java.time.Instant.parse(bundle.getString("created_at")).toEpochMilli() : now;
        if (created > now + 300_000L || created < now - 86_400_000L)
            throw new IllegalArgumentException("Bundle timestamp is stale or in the future; rebuild");
        return created;
    }

    private static void validateProgrammeCount(JSONArray programmes) {
        if (programmes.isEmpty() || programmes.length() > 1_000_000)
            throw new IllegalArgumentException("Empty or oversized programmes");
    }

    private static Map<String, Integer> declaredPriorities(JSONArray sourceValues) {
        Map<String, Integer> declared = new HashMap<>();
        for (Object value : sourceValues) {
            JSONObject source = (JSONObject) value;
            int priority = source.optInt("priority", 100);
            if (priority < 0 || priority > 1_000_000 || declared.putIfAbsent(source.getString(SOURCE_ID), priority) != null)
                throw new IllegalArgumentException("Invalid source priority or duplicate source");
        }
        return declared;
    }

    private static void readProgrammes(JSONArray programmes, Map<String, Integer> declared, long created, long now,
                                       Map<String, Map<String, List<com.uiptv.application.EpgProgramme>>> sources) {
        for (Object value : programmes) {
            JSONObject programme = (JSONObject) value;
            String source = programme.getString(SOURCE_ID);
            String guide = programme.getString("guide_channel_id");
            long start = programme.getLong(START);
            long stop = programme.getLong("stop");
            String title = programme.getString(TITLE);
            String description = programme.optString(DESCRIPTION, "");
            validateProgramme(new ProgrammeData(source, guide, title, description, start, stop), declared, created);
            if (stop <= now - 6 * 3_600_000L || start >= now + 7 * 86_400_000L) continue;
            sources.computeIfAbsent(BUILDER_PREFIX + hash(source), _ -> new LinkedHashMap<>())
                    .computeIfAbsent(guide, _ -> new ArrayList<>())
                    .add(new com.uiptv.application.EpgProgramme(title, description, start, stop));
        }
    }

    private record ProgrammeData(String source, String guide, String title, String description, long start, long stop) { }

    private static void validateProgramme(ProgrammeData programme, Map<String, Integer> declared, long created) {
        if (!declared.containsKey(programme.source()) || programme.source().isBlank() || programme.guide().isBlank())
            throw new IllegalArgumentException("Invalid programme source or guide identity");
        if (programme.title().isBlank() || programme.title().length() > 512 || programme.description().length() > 2048)
            throw new IllegalArgumentException("Invalid programme title or description length");
        if (programme.stop() <= programme.start()) throw new IllegalArgumentException("Invalid programme interval: stop must follow start");
        if (programme.stop() <= created - 6 * 3_600_000L || programme.start() >= created + 7 * 86_400_000L)
            throw new IllegalArgumentException("Programme outside the supported time window; rebuild the bundle");
    }

    private static List<EpgCacheDb.BuilderMatch> readMatches(JSONObject bundle, boolean mappingsCurrent,
                                                              Map<Target, JSONObject> targets,
                                                              Map<String, Map<String, List<com.uiptv.application.EpgProgramme>>> sources,
                                                              Map<String, Integer> declared, long now) {
        List<EpgCacheDb.BuilderMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JSONArray mappings = mappingsCurrent ? bundle.getJSONArray("mappings") : new JSONArray();
        for (Object value : mappings) {
            JSONObject mapping = (JSONObject) value;
            Target target = new Target(mapping.getString(ACCOUNT_ID), mapping.getString(PROVIDER_CHANNEL_ID));
            JSONObject current = targets.get(target);
            String source = BUILDER_PREFIX + hash(mapping.getString(SOURCE_ID));
            String guide = mapping.getString("guide_channel_id");
            Map<String, List<com.uiptv.application.EpgProgramme>> sourceSchedules = sources.get(source);
            if (current == null || !seen.add(target + "|" + source) || !current.getString(SIGNATURE).equals(mapping.getString(SIGNATURE))
                    || sourceSchedules == null || !sourceSchedules.containsKey(guide)
                    || sourceSchedules.get(guide).stream().noneMatch(p -> p.stop() > now))
                throw new IllegalArgumentException("Unknown, duplicate, stale or empty mapping");
            if (current.getBoolean("is_manual_override") && !current.getString("manual_override").equals(guide))
                throw new IllegalArgumentException("Cannot overwrite manual override");
            matches.add(new EpgCacheDb.BuilderMatch(new EpgCacheDb.Identity(target.account(), target.provider(), mapping.getString(SIGNATURE)),
                    source, guide, declared.get(mapping.getString(SOURCE_ID))));
        }
        return matches;
    }

    private static Map<String, EpgCacheDb.SharedSource> readSharedSources(JSONObject bundle,
                                                                            Map<String, Map<String, List<com.uiptv.application.EpgProgramme>>> sources) {
        Map<String, EpgCacheDb.SharedSource> shared = new LinkedHashMap<>();
        if (!bundle.optBoolean("shared_guides", false)) return shared;
        for (Object value : bundle.getJSONArray("sources")) {
            JSONObject source = (JSONObject) value;
            String id = BUILDER_PREFIX + hash(source.getString(SOURCE_ID));
            Map<String, List<com.uiptv.application.EpgProgramme>> schedules = sources.get(id);
            if (schedules == null) continue;
            Map<String, String> names = sharedNames(source.optJSONObject("names"), schedules);
            Map<String, String> icons = sharedIcons(source.optJSONObject("icons"), schedules);
            shared.put(id, new EpgCacheDb.SharedSource(source.optInt("priority", 100),
                    source.optJSONArray("countries") == null ? "" : source.getJSONArray("countries").join(",").replace("\"", ""),
                    source.optJSONArray("languages") == null ? "" : source.getJSONArray("languages").join(",").replace("\"", ""), names, icons));
        }
        return shared;
    }

    private static Map<String, String> sharedNames(JSONObject aliases, Map<String, List<com.uiptv.application.EpgProgramme>> schedules) {
        Map<String, String> names = new HashMap<>();
        if (aliases != null) for (String name : aliases.keySet()) {
            String guide = aliases.getString(name);
            if (name.length() > 512) throw new IllegalArgumentException("Oversized guide alias");
            if (schedules.containsKey(guide)) names.put(XmltvGuide.normalize(name), guide);
        }
        schedules.keySet().forEach(guide -> names.putIfAbsent(XmltvGuide.normalize(guide), guide));
        return names;
    }

    private static Map<String, String> sharedIcons(JSONObject artwork, Map<String, List<com.uiptv.application.EpgProgramme>> schedules) {
        Map<String, String> icons = new HashMap<>();
        if (artwork != null) for (String guide : artwork.keySet()) {
            String icon = artwork.getString(guide);
            if (schedules.containsKey(guide) && (icon.startsWith("https://") || icon.startsWith("http://"))) icons.put(guide, icon);
        }
        return icons;
    }

    private static JSONObject readBundle(String bundlePath) throws java.io.IOException {
        if (bundlePath == null || Files.size(Path.of(bundlePath)) > 128 * 1024 * 1024L)
            throw new IllegalArgumentException("--bundle must name a file smaller than 128 MiB");
        return new JSONObject(Files.readString(Path.of(bundlePath)));
    }

    public JSONObject importBundle(String databasePath, String bundlePath) throws Exception {
        selectDatabase(databasePath);
        JSONObject bundle = readBundle(bundlePath);
        Prepared prepared = prepare(databasePath, bundle);
        new EpgCacheDb().publishBuilder(prepared.sources(), prepared.matches(), prepared.shared(), System.currentTimeMillis(), () -> {
            try {
                Prepared current = prepare(databasePath, bundle);
                // Revalidation may discard bindings after a concurrent catalogue/config change.
                // Publish that decision, rather than retaining the pre-lock binding list.
                prepared.matches().clear();
                prepared.matches().addAll(current.matches());
            } catch (Exception error) { throw new IllegalArgumentException("Inventory changed before publication", error); }
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
    public JSONObject parseGuide(String requestPath) throws java.io.IOException, javax.xml.stream.XMLStreamException, InterruptedException {
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
            for (EpgProgramme p : EpgSchedules.normalize(entries)) values.put(new JSONObject().put(TITLE, p.title()).put(DESCRIPTION, p.description())
                    .put(START, p.start()).put("stop", p.stop()));
            programmes.put(id, values);
        });
        Map<String,String> qualityNames = guide.qualityNames();
        if (request.has(NAME_RULES)) {
            List<EpgNameRules.Rule> rules = new ArrayList<>();
            for (Object value : request.getJSONArray(NAME_RULES)) {
                JSONObject rule = (JSONObject) value;
                rules.add(new EpgNameRules.Rule(rule.getString("term"), rule.getString("scope")));
            }
            qualityNames = new EpgNameRules(rules).aliases(guide.names(), true);
        }
        return new JSONObject().put("programmes", programmes).put("names", guide.names())
                .put("quality_names", qualityNames).put("icons", guide.icons());
    }

    /** Reuse the same interval resolver as desktop/API reads; no database is opened. */
    public JSONObject mergeSchedules(String requestPath) throws java.io.IOException {
        JSONObject request = readBundle(requestPath);
        JSONArray result = new JSONArray();
        for (Object value : request.getJSONArray("schedules")) {
            List<List<EpgProgramme>> sources = new ArrayList<>();
            for (Object source : (JSONArray)value) {
                List<EpgProgramme> programmes = new ArrayList<>();
                for (Object entry : (JSONArray)source) {
                    JSONObject p = (JSONObject)entry;
                    programmes.add(new EpgProgramme(p.getString(TITLE), p.optString(DESCRIPTION, ""), p.getLong(START), p.getLong("stop")));
                }
                sources.add(programmes);
            }
            JSONArray schedule = new JSONArray();
            for (EpgProgramme p : EpgSchedules.preferSources(sources)) {
                    schedule.put(new JSONObject().put(TITLE, p.title()).put(DESCRIPTION, p.description()).put(START, p.start()).put("stop", p.stop()));
            }
            result.put(schedule);
        }
        return new JSONObject().put("schedules", result);
    }

    public JSONObject showStatus(String databasePath) throws java.io.IOException, java.sql.SQLException {
        if (databasePath == null || databasePath.isBlank()) {
            throw new IllegalArgumentException("UIPTV database path could not be resolved");
        }

        selectDatabase(databasePath);

        Configuration config = ConfigurationService.getInstance().read();

        JSONObject result = new JSONObject();
        result.put("epg_enabled", config.isEpgEnabled());
        result.put("database_path", databasePath);
        result.put(DATABASE_IDENTITY, computeDatabaseIdentity(databasePath));

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
