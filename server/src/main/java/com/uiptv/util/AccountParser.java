package com.uiptv.util;

import com.uiptv.model.Account;

import java.util.List;

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
    List<Account> parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme);
}
