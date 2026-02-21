package com.uiptv.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerServiceTest {

    @Test
    void mergeMissingQueryParams_fillsMissingStreamAndTokenFromOriginal() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=&extension=ts&play_token=";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = PlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=1470604"));
        assertTrue(merged.contains("play_token=abc"));
    }

    @Test
    void mergeMissingQueryParams_keepsResolvedValuesWhenPresent() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=999&extension=ts&play_token=xyz";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = PlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=999"));
        assertTrue(merged.contains("play_token=xyz"));
    }

    @Test
    void mergeMissingQueryParams_returnsResolvedWhenNoQuery() {
        String resolved = "ffmpeg http://example.com/play/live.php";
        String original = "ffmpeg http://example.com/play/live.php?stream=1470604";
        String merged = PlayerService.mergeMissingQueryParams(resolved, original);

        assertEquals(resolved, merged);
    }
}
