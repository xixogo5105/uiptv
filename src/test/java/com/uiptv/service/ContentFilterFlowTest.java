package com.uiptv.service;

import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContentFilterFlowTest extends DbBackedTest {

    @Test
    void parseItvChannels_fromJsonFile_censorTrue_appliesConfiguredFilter() throws IOException {
        saveConfiguration("", "premium", false);
        String json = readResource("json/itv_channels_filter_case.json");

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, true);

        assertEquals(1, channels.size());
        assertEquals("Sports Live", channels.get(0).getName());
    }

    @Test
    void parseItvChannels_fromJsonFile_censorFalse_returnsAllChannels() throws IOException {
        saveConfiguration("", "premium", false);
        String json = readResource("json/itv_channels_filter_case.json");

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);

        assertEquals(3, channels.size());
    }

    @Test
    void parseCategories_fromJsonFile_censorTrue_appliesConfiguredFilter() throws IOException {
        saveConfiguration("premium", "", false);
        String json = readResource("json/categories_filter_case.json");

        List<Category> categories = CategoryService.getInstance().parseCategories(json, true);

        assertEquals(1, categories.size());
        assertEquals("Sports", categories.get(0).getTitle());
    }

    @Test
    void parseCategories_fromJsonFile_pauseFilteringTrue_returnsAllCategories() throws IOException {
        saveConfiguration("premium", "", true);
        String json = readResource("json/categories_filter_case.json");

        List<Category> categories = CategoryService.getInstance().parseCategories(json, true);

        assertEquals(3, categories.size());
    }

    private void saveConfiguration(String categoryFilter, String channelFilter, boolean pauseFiltering) {
        Configuration configuration = new Configuration(
                null,
                null,
                null,
                null,
                categoryFilter,
                channelFilter,
                pauseFiltering,
                null,
                null,
                null,
                false,
                "8888",
                false,
                false
        );
        ConfigurationService.getInstance().save(configuration);
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(is, "Missing test resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
