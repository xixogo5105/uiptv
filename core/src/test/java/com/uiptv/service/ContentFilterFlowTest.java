package com.uiptv.service;

import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class ContentFilterFlowTest extends DbBackedTest {

    @Test
    void parseItvChannels_fromJsonFile_censorTrue_keepsCensoredItemsForParentalLockGate() throws IOException {
        saveConfiguration("", "premium", false);
        String json = readResource("json/itv_channels_filter_case.json");

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, true);

        assertEquals(2, channels.size());
        assertEquals(List.of("Sports Live", "Kids Plus"),
                channels.stream().map(Channel::getName).toList());
    }

    @Test
    void parseItvChannels_fromJsonFile_censorFalse_returnsAllChannels() throws IOException {
        saveConfiguration("", "premium", false);
        String json = readResource("json/itv_channels_filter_case.json");

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);

        assertEquals(3, channels.size());
    }

    @Test
    void parseCategories_fromJsonFile_censorTrue_keepsCensoredItemsForParentalLockGate() throws IOException {
        saveConfiguration("premium", "", false);
        String json = readResource("json/categories_filter_case.json");

        List<Category> categories = CategoryService.getInstance().parseCategories(json, true);

        assertEquals(2, categories.size());
        assertEquals(List.of("Sports", "Documentary"),
                categories.stream().map(Category::getTitle).toList());
    }

    @Test
    void parseCategories_fromJsonFile_pauseFilteringTrue_returnsAllCategories() throws IOException {
        saveConfiguration("premium", "", true);
        String json = readResource("json/categories_filter_case.json");

        List<Category> categories = CategoryService.getInstance().parseCategories(json, true);

        assertEquals(3, categories.size());
    }

    @Test
    void parseCategories_trailingCommaInFilter_doesNotRemoveEverything() throws IOException {
        saveConfiguration("premium,", "", false);
        String json = readResource("json/categories_filter_case.json");

        List<Category> categories = CategoryService.getInstance().parseCategories(json, true);

        assertEquals(List.of("Sports", "Documentary"),
                categories.stream().map(Category::getTitle).toList());
    }

    @Test
    void parseItvChannels_trailingCommaInFilter_doesNotRemoveEverything() throws IOException {
        saveConfiguration("", "premium,", false);
        String json = readResource("json/itv_channels_filter_case.json");

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, true);

        assertEquals(List.of("Sports Live", "Kids Plus"),
                channels.stream().map(Channel::getName).toList());
    }

    @Test
    void streamedXtremeVodAppliesChannelFilterBeforeFirstRender() throws Exception {
        saveConfiguration("", "fr|", false);
        Account account = new Account("streamed-filter-vod", "user", "pass", "http://127.0.0.1/mock",
                null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(Account.AccountAction.vod);
        account.setParentalLock(true);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName("streamed-filter-vod");
        saved.setAction(Account.AccountAction.vod);
        saved.setParentalLock(true);
        VodCategoryDb.get().saveAll(List.of(new Category("provider-cat", "Movies", "Movies", false, 0)), saved);
        Category category = VodCategoryDb.get().getCategories(saved).getFirst();

        Channel blocked = channel("fr-1", "|FR| Hidden title");
        Channel visible = channel("en-1", "|EN| Visible title");
        List<Channel> providerRows = List.of(blocked, visible);
        List<List<Channel>> renderedBatches = new ArrayList<>();

        try (MockedStatic<XtremeApiParser> parser = mockStatic(XtremeApiParser.class)) {
            parser.when(() -> XtremeApiParser.parseChannels(eq("provider-cat"), eq(saved), any(), any()))
                    .thenAnswer(invocation -> {
                        Consumer<List<Channel>> streamedBatch = invocation.getArgument(2);
                        streamedBatch.accept(providerRows);
                        return providerRows;
                    });

            List<Channel> result = ChannelService.getInstance().get(
                    "provider-cat", saved, category.getDbId(), null, renderedBatches::add, () -> false);

            assertEquals(List.of("en-1"), result.stream().map(Channel::getChannelId).toList());
            assertEquals(List.of("en-1"), renderedBatches.stream().flatMap(List::stream)
                    .map(Channel::getChannelId).toList());
            assertEquals(2, VodChannelDb.get().getChannelCount(saved, category.getDbId()),
                    "Filtered titles remain cached; the filter controls visibility only");
        }
    }

    private Channel channel(String id, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id + ".mp4");
        return channel;
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
