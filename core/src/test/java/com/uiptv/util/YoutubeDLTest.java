package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoutubeDLTest {

    @Test
    void getStreamingUrl_returnsOriginalUrlWhenDownloadersCannotResolve() {
        YoutubeDL.setYtDlpPath("definitely-missing-yt-dlp-for-test");
        YoutubeDL.setYoutubeDlPath("definitely-missing-youtube-dl-for-test");

        assertEquals("https://video.example/watch/1", YoutubeDL.getStreamingUrl("https://video.example/watch/1"));
        assertEquals("", YoutubeDL.getStreamingUrl(""));
    }
}
