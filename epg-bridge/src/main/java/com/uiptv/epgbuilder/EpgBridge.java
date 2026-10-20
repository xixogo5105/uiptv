package com.uiptv.epgbuilder;

import com.uiptv.application.EpgBuilderApplicationService;
import com.uiptv.db.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** CLI adapter only. All database and schedule operations belong to the Java core. */
public final class EpgBridge {
    private static final String BUNDLE_OPTION = "--bundle";
    private static final Logger LOG = LoggerFactory.getLogger(EpgBridge.class);

    private EpgBridge() { }
    public static void main(String[] args) {
        if (run(args) != 0) {
            System.exit(1);
        }
    }

    static int run(String[] args) {
        return run(args, new EpgBuilderApplicationService());
    }

    static int run(String[] args, EpgBuilderApplicationService service) {
        System.setProperty("uiptv.sql.external", "true");
        try {
            if (args.length == 0) throw new IllegalArgumentException("Expected export-inventory, validate-bundle, import-bundle, status or merge-schedules");
            Map<String,String> options = new HashMap<>();
            for (int i = 1; i < args.length; i += 2) {
                if (i + 1 >= args.length || !Set.of(BUNDLE_OPTION).contains(args[i])
                        || options.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IllegalArgumentException("Expected " + BUNDLE_OPTION + " <path>; the database is resolved by UIPTV");
            }
            if (args[0].equals("export-inventory-jsonl") || args[0].equals("export-inventory")) {
                var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(java.io.FileDescriptor.out), java.nio.charset.StandardCharsets.UTF_8));
                if (args[0].equals("export-inventory-jsonl")) service.writeInventory(SQLConnection.resolveConfiguredDatabasePath(), writer);
                else service.writeInventoryJson(SQLConnection.resolveConfiguredDatabasePath(), writer);
                return 0;
            }
            var result = switch (args[0]) {
                case "validate-bundle" -> service.validateBundle(SQLConnection.resolveConfiguredDatabasePath(), options.get(BUNDLE_OPTION));
                case "import-bundle" -> service.importBundle(SQLConnection.resolveConfiguredDatabasePath(), options.get(BUNDLE_OPTION));
                case "status" -> service.showStatus(SQLConnection.resolveConfiguredDatabasePath());
                case "merge-schedules" -> service.mergeSchedules(options.get(BUNDLE_OPTION));
                case "parse-xmltv" -> service.parseGuide(options.get(BUNDLE_OPTION));
                default -> throw new IllegalArgumentException("Unknown command");
            };
            LOG.info("{}", result);
            return 0;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOG.error("EPG bridge interrupted: {}", error.getMessage());
            return 1;
        } catch (Exception error) {
            // Do not dump provider URLs, metadata or credentials in stack traces.
            LOG.error("EPG bridge failed: {}: {}", error.getClass().getSimpleName(), error.getMessage());
            return 1;
        }
    }
}
