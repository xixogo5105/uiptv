package com.uiptv.shared.sync;

import java.util.Collections;
import java.util.List;

public record DatabaseSyncReport(List<TableSyncResult> tableResults,
                                 boolean configurationRequested,
                                 boolean configurationCopied,
                                 boolean externalPlayerPathsIncluded) {
    public DatabaseSyncReport {
        tableResults = tableResults == null ? List.of() : Collections.unmodifiableList(List.copyOf(tableResults));
    }

    public int totalRowsSynced() {
        return tableResults.stream().mapToInt(TableSyncResult::rowCount).sum();
    }
}
