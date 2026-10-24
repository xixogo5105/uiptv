package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPathDisplayNameTest {
    @Test
    void extractsNamesFromUnixAndWindowsPathsRegardlessOfHost() {
        assertEquals("mpv", PlayerPathDisplayName.playerName("/usr/lib/mpv"));
        assertEquals("mpv", PlayerPathDisplayName.playerName("C:\\mpv.exe"));
        assertEquals("vlc", PlayerPathDisplayName.playerName("C:\\Program Files\\VLC\\vlc.EXE"));
    }

    @Test
    void stripsQuotesTrailingSeparatorsAndCommonExecutableSuffixes() {
        assertEquals("mpv", PlayerPathDisplayName.playerName("  'C:\\Program Files\\mpv.exe'  "));
        assertEquals("vlc", PlayerPathDisplayName.playerName("/opt/vlc.AppImage/"));
        assertEquals("IINA", PlayerPathDisplayName.playerName("/Applications/IINA.app"));
    }

    @Test
    void leavesUnconfiguredOrNamelessPathsWithoutInventingAPlayerName() {
        assertEquals("", PlayerPathDisplayName.playerName(null));
        assertEquals("", PlayerPathDisplayName.playerName("  "));
        assertEquals("", PlayerPathDisplayName.playerName("C:\\"));
        assertEquals("Player 2", PlayerPathDisplayName.withPlayerName("Player 2", ""));
    }

    @Test
    void appendsTheNameAfterTheTranslatedSlotLabel() {
        assertEquals("Player 1 (mpv)",
                PlayerPathDisplayName.withPlayerName("Player 1", "C:\\mpv.exe"));
    }
}
