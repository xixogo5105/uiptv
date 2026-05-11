package com.uiptv.service;

import com.uiptv.model.Configuration;
import com.uiptv.util.json.KJsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThumbnailModeServiceTest extends DbBackedTest {

    @Test
    void imdbMetadataService_returnsEmpty_whenThumbnailsDisabled() {
        saveConfig(false);

        KJsonObject details = ImdbMetadataService.getInstance().findBestEffortDetails("Test Series", "");
        KJsonObject detailsWithHints = ImdbMetadataService.getInstance().findBestEffortDetails("Test Series", "", java.util.List.of("Hint"));
        KJsonObject movieDetails = ImdbMetadataService.getInstance().findBestEffortMovieDetails("Test Movie", "");
        KJsonObject movieDetailsWithHints = ImdbMetadataService.getInstance().findBestEffortMovieDetails("Test Movie", "", java.util.List.of("Hint"));

        assertEquals(0, details.length());
        assertEquals(0, detailsWithHints.length());
        assertEquals(0, movieDetails.length());
        assertEquals(0, movieDetailsWithHints.length());
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
        ConfigurationService.getInstance().save(config);
    }
}
