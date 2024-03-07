package com.uiptv.db;

import java.util.*;

import static com.uiptv.db.DatabaseUtils.DbTable.ACCOUNT_TABLE;

public class DatabasePatchesUtils {
    private static Set<String> dbPatches = new HashSet<>();

    static {
        dbPatches.add("ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN macAddressList TEXT");
    }

    public static Set<String> getDbPatches() {
        return dbPatches;
    }
}
