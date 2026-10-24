package com.uiptv.application;

import com.uiptv.db.EpgSourceBlacklistDb;
import java.util.List;

/** Central source filter shared by manual and scheduled EPG refreshes. */
public final class EpgSourceBlacklistService {
    private EpgSourceBlacklistService() { }

    public static List<EpgSourceBlacklistDb.Entry> list() { return new EpgSourceBlacklistDb().list(); }
    public static void add(String source) { new EpgSourceBlacklistDb().add(source); }
    public static void remove(String sourceId) { new EpgSourceBlacklistDb().remove(sourceId); }
    public static boolean contains(String source) {
        return source != null && new EpgSourceBlacklistDb().contains(EpgCacheApplicationService.sourceId(source.strip()));
    }
    public static List<String> filter(Iterable<String> sources) {
        var result = new java.util.ArrayList<String>();
        for (String source : sources) if (source != null && !source.isBlank() && !contains(source)) result.add(source);
        return List.copyOf(result);
    }
}
