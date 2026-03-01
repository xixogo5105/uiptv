package com.uiptv.service;

import com.uiptv.model.Configuration;
import com.uiptv.util.ImageCacheManager;
import javafx.scene.image.Image;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailModeServiceTest extends DbBackedTest {

    @Test
    void imdbMetadataService_returnsEmpty_whenThumbnailsDisabled() {
        saveConfig(false);

        JSONObject details = ImdbMetadataService.getInstance().findBestEffortDetails("Test Series", "");
        JSONObject detailsWithHints = ImdbMetadataService.getInstance().findBestEffortDetails("Test Series", "", java.util.List.of("Hint"));
        JSONObject movieDetails = ImdbMetadataService.getInstance().findBestEffortMovieDetails("Test Movie", "");
        JSONObject movieDetailsWithHints = ImdbMetadataService.getInstance().findBestEffortMovieDetails("Test Movie", "", java.util.List.of("Hint"));

        assertEquals(0, details.length());
        assertEquals(0, detailsWithHints.length());
        assertEquals(0, movieDetails.length());
        assertEquals(0, movieDetailsWithHints.length());
    }

    @Test
    void imageCacheManager_returnsNullImmediately_whenThumbnailsDisabled() throws Exception {
        saveConfig(false);

        CompletableFuture<Image> future = ImageCacheManager.loadImageAsync("http://example.com/test.jpg", "test");
        assertTrue(future.isDone());
        assertNull(future.get(1, TimeUnit.SECONDS));
    }

    private void saveConfig(boolean enableThumbnails) {
        Configuration config = new Configuration(
                "", "", "", "",
                "", "",
                false,
                "", "", "",
                false,
                "",
                false,
                false,
                "30",
                enableThumbnails
        );
        ConfigurationService.getInstance().save(config);
    }
}
