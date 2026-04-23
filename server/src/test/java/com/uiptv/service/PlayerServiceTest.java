package com.uiptv.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerServiceTest {

    @Test
    void mergeMissingQueryParams_fillsMissingStreamAndTokenFromOriginal() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=&extension=ts&play_token=";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=1470604"));
        assertTrue(merged.contains("play_token=abc"));
    }

    @Test
    void mergeMissingQueryParams_keepsResolvedValuesWhenPresent() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=999&extension=ts&play_token=xyz";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=999"));
        assertTrue(merged.contains("play_token=xyz"));
    }

    @Test
    void mergeMissingQueryParams_returnsResolvedWhenNoQuery() {
        String resolved = "ffmpeg http://example.com/play/live.php";
        String original = "ffmpeg http://example.com/play/live.php?stream=1470604";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertEquals(resolved, merged);
    }

    @Test
    void sanitizeAndEncodeUrl_encodesInvalidCharacters() {
        PlayerService service = PlayerService.getInstance();

        // Case 1: URL with unencoded brackets
        String urlWithBrackets = "http://host/path?ads.deviceid=[DEVICE_ID]&coppa=0";
        String expectedEncodedUrl = "http://host/path?ads.deviceid=%5BDEVICE_ID%5D&coppa=0";
        assertEquals(expectedEncodedUrl, service.sanitizeAndEncodeUrl(urlWithBrackets));

        // Case 2: URL with spaces and other characters
        // Note: 'hello world' becomes 'hello+world'
        // 'a@b' becomes 'a%40b' (using @ to verify encoding without +/space ambiguity)
        String urlWithSpaces = "http://host/path?q=hello world&filter=a@b";
        String expectedUrlWithSpaces = "http://host/path?q=hello+world&filter=a%40b";
        assertEquals(expectedUrlWithSpaces, service.sanitizeAndEncodeUrl(urlWithSpaces));

        // Case 3: URL that is already partially encoded
        String partiallyEncodedUrl = "http://host/path?data=%5B1,2%5D&q=test";
        String expectedPartiallyEncodedUrl = "http://host/path?data=%5B1%2C2%5D&q=test";
        assertEquals(expectedPartiallyEncodedUrl, service.sanitizeAndEncodeUrl(partiallyEncodedUrl));

        // Case 4: The full user-provided URL
        String userUrl = "https://cdn-uw2-prod.tsv2.amagi.tv/linear/amg01605-ndtvconvergence-ndtvindia-lgin/playlist.m3u8?ads.deviceid=[DEVICE_ID]&ads.ifa=[IFA]&coppa=0";
        String expectedUserUrl = "https://cdn-uw2-prod.tsv2.amagi.tv/linear/amg01605-ndtvconvergence-ndtvindia-lgin/playlist.m3u8?ads.deviceid=%5BDEVICE_ID%5D&ads.ifa=%5BIFA%5D&coppa=0";
        assertEquals(expectedUserUrl, service.sanitizeAndEncodeUrl(userUrl));

        // Case 5: No query string
        String noQueryUrl = "http://host/path";
        assertEquals(noQueryUrl, service.sanitizeAndEncodeUrl(noQueryUrl));
    }
}
