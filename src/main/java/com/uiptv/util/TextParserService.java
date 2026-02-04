package com.uiptv.util;

/**
 * Main service for parsing text into accounts.
 * This class now acts as a factory to select and create the appropriate parser.
 */
public class TextParserService {

    public static final String MODE_STALKER = "Stalker Portal";
    public static final String MODE_XTREME = "Xtreme";
    public static final String MODE_M3U = "M3U Playlists";

    public static void saveBulkAccounts(String text, String mode, boolean pauseCaching, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        AccountParser parser;

        if (MODE_STALKER.equals(mode)) {
            parser = new StalkerPortalParser();
        } else if (MODE_XTREME.equals(mode)) {
            parser = new XtremeParser();
        } else {
            parser = new M3uParser();
        }

        parser.parseAndSave(text, pauseCaching, groupAccountsByMac, convertM3uToXtreme);
    }
}
