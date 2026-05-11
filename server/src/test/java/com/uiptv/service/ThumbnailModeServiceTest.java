package com.uiptv.service;

import com.uiptv.model.Configuration;
import kotlinx.serialization.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThumbnailModeServiceTest extends DbBackedTest {

    @Test
    void imdbMetadataService_returnsEmpty_whenThumbnailsDisabled() {
        saveConfig(false);

        JsonObject details = ImdbMetadataService.INSTANCE.findBestEffortDetails("Test Series", "");
        JsonObject detailsWithHints = ImdbMetadataService.INSTANCE.findBestEffortDetails("Test Series", "", java.util.List.of("Hint"));
        JsonObject movieDetails = ImdbMetadataService.INSTANCE.findBestEffortMovieDetails("Test Movie", "");
        JsonObject movieDetailsWithHints = ImdbMetadataService.INSTANCE.findBestEffortMovieDetails("Test Movie", "", java.util.List.of("Hint"));

        assertEquals(0, details.size());
        assertEquals(0, detailsWithHints.size());
        assertEquals(0, movieDetails.size());
        assertEquals(0, movieDetailsWithHints.size());
    }

    private void saveConfig(boolean enableThumbnails) {
        Configuration config = new Configuration(
                "", "", "", "",
                "", "",
                false,
                false,
                "",
                false,
                false,
                "30",
                enableThumbnails
        );
        ConfigurationService.INSTANCE.save(config);
    }
}
