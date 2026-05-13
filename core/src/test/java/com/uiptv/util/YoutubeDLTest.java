package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeDLTest {

    @Test
    void getStreamingUrl_returnsOriginalUrlWhenDownloadersCannotResolve() {
        YoutubeDL.setYtDlpPath("definitely-missing-yt-dlp-for-test");
        YoutubeDL.setYoutubeDlPath("definitely-missing-youtube-dl-for-test");

        assertEquals("https://video.example/watch/1", YoutubeDL.getStreamingUrl("https://video.example/watch/1"));
        assertEquals("", YoutubeDL.getStreamingUrl(""));
    }

    @Test
    void getStreamingUrl_usesCustomDownloaderOutputWhenItReturnsHttpUrl() throws Exception {
        Path script = Files.createTempFile("yt-dlp-test-", ".sh");
        Files.writeString(script, """
                #!/bin/sh
                echo "https://stream.example/resolved.m3u8"
                """);
        assertTrue(script.toFile().setExecutable(true, true));
        YoutubeDL.setYtDlpPath(script.toString());
        YoutubeDL.setYoutubeDlPath("unused-youtube-dl");

        try {
            assertEquals("https://stream.example/resolved.m3u8", YoutubeDL.getStreamingUrl(" https://video.example/watch/2 "));
        } finally {
            YoutubeDL.setYtDlpPath("definitely-missing-yt-dlp-for-test");
            YoutubeDL.setYoutubeDlPath("definitely-missing-youtube-dl-for-test");
            Files.deleteIfExists(script);
        }
    }

    @Test
    void getStreamingUrl_fallsBackAcrossFailedCustomTools() throws Exception {
        Path nonHttp = Files.createTempFile("yt-dlp-non-http-", ".sh");
        Path success = Files.createTempFile("youtube-dl-success-", ".sh");
        Files.writeString(nonHttp, """
                #!/bin/sh
                echo "not-a-url"
                """);
        Files.writeString(success, """
                #!/bin/sh
                echo "http://stream.example/fallback.ts"
                """);
        nonHttp.toFile().setExecutable(true, true);
        success.toFile().setExecutable(true, true);
        YoutubeDL.setYtDlpPath(nonHttp.toString());
        YoutubeDL.setYoutubeDlPath(success.toString());

        try {
            assertEquals("http://stream.example/fallback.ts", YoutubeDL.getStreamingUrl("https://video.example/watch/3"));
        } finally {
            YoutubeDL.setYtDlpPath("definitely-missing-yt-dlp-for-test");
            YoutubeDL.setYoutubeDlPath("definitely-missing-youtube-dl-for-test");
            Files.deleteIfExists(nonHttp);
            Files.deleteIfExists(success);
        }
    }
}
