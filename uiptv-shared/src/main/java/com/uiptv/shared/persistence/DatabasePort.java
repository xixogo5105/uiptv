package com.uiptv.shared.persistence;

import com.uiptv.shared.schema.UiptvTable;

import java.util.List;
import java.util.Map;

public interface DatabasePort {
    void migrateToLatest() throws DatabasePortException;

    List<String> tableColumns(UiptvTable table) throws DatabasePortException;

    List<Map<String, Object>> readRows(UiptvTable table, List<String> columns) throws DatabasePortException;

    void upsertRows(UiptvTable table, List<String> columns, List<Map<String, Object>> rows) throws DatabasePortException;

    void replaceRows(UiptvTable table, List<String> columns, List<Map<String, Object>> rows) throws DatabasePortException;
}
