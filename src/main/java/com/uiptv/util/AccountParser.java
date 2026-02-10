package com.uiptv.util;

/**
 * Defines a standard interface for all account parsers.
 */
public interface AccountParser {
    /**
     * Parses the given text and saves the discovered accounts.
     *
     * @param text The raw text to parse.
     * @param groupAccountsByMac Flag to group Stalker accounts by MAC address.
     * @param convertM3uToXtreme Flag to convert M3U playlists to Xtreme accounts.
     */
    void parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme);
}
