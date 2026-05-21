package com.uiptv.shared.schema;

public final class UiptvMigrationInfo {
    public static final String MIGRATION_RESOURCE_PATH = "db/migrations";
    public static final String MIGRATION_LIST_RESOURCE = "db/migrations/migrations.txt";
    public static final String BASELINE_MIGRATION = "0000_baseline.sql";
    public static final String CURRENT_SCHEMA_VERSION = "0199";

    private UiptvMigrationInfo() {
    }
}
