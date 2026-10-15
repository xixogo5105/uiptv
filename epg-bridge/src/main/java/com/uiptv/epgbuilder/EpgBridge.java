package com.uiptv.epgbuilder;

import com.uiptv.application.EpgBuilderApplicationService;
import com.uiptv.db.SQLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** CLI adapter only. All database and schedule operations belong to the Java core. */
public final class EpgBridge {
    private EpgBridge() { }
    public static void main(String[] args) {
        System.setProperty("uiptv.sql.external", "true");
        try {
            if (args.length == 0) throw new IllegalArgumentException("Expected export-inventory, validate-bundle, import-bundle, status or merge-schedules");
            Map<String,String> options = new HashMap<>();
            for (int i = 1; i < args.length; i += 2) {
                if (i + 1 >= args.length || !Set.of("--bundle").contains(args[i])
                        || options.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IllegalArgumentException("Expected --bundle <path>; the database is resolved by UIPTV");
            }
            var service = new EpgBuilderApplicationService();
            if (args[0].equals("export-inventory-jsonl") || args[0].equals("export-inventory")) {
                var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8));
                if (args[0].equals("export-inventory-jsonl")) service.writeInventory(SQLConnection.resolveConfiguredDatabasePath(), writer);
                else service.writeInventoryJson(SQLConnection.resolveConfiguredDatabasePath(), writer);
                return;
            }
            var result = switch (args[0]) {
                case "validate-bundle" -> service.validateBundle(SQLConnection.resolveConfiguredDatabasePath(), options.get("--bundle"));
                case "import-bundle" -> service.importBundle(SQLConnection.resolveConfiguredDatabasePath(), options.get("--bundle"));
                case "status" -> service.showStatus(SQLConnection.resolveConfiguredDatabasePath());
                case "merge-schedules" -> service.mergeSchedules(options.get("--bundle"));
                case "parse-xmltv" -> service.parseGuide(options.get("--bundle"));
                default -> throw new IllegalArgumentException("Unknown command");
            };
            System.out.println(result);
        } catch (Exception error) {
            // Do not dump provider URLs, metadata or credentials in stack traces.
            System.err.println("EPG bridge failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            System.exit(1);
        }
    }
}
