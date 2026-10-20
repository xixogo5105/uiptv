package com.uiptv.service.cache;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.vod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractAccountCacheReloaderCoverageTest extends DbBackedTest {

    private final ProbeReloader reloader = new ProbeReloader();

    @Test
    void normalizesAndMergesProviderItemsWithoutLosingCanonicalIds() {
        Category first = new Category("provider-1", " News ", "news", false, 0);
        Category duplicate = new Category("provider-2", "news", "news-duplicate", false, 0);
        Category byId = new Category("provider-3", "", "by-id", false, 0);
        Category noIdentity = new Category("provider-4", "", "", false, 0);
        var normalized = reloader.normalize(Arrays.asList(first, duplicate, byId, noIdentity, null));

        assertEquals(List.of(first, byId, noIdentity), normalized.categories());
        assertEquals("provider-1", reloader.canonicalIds(normalized).get("provider-2"));
        assertEquals("by-id", reloader.lookupCategory("by-id", reloader.canonicalIds(normalized)));
        assertEquals("missing", reloader.lookupCategory("missing", reloader.canonicalIds(normalized)));
        assertEquals("", reloader.key(null));

        Channel byChannelId = channel("ID-1", "One", "");
        Channel duplicateId = channel("id-1", "Different", "");
        Channel byName = channel("", "Same Name", "");
        Channel duplicateName = channel("", " same name ", "");
        Channel byCommand = channel("", "", "http://stream/one");
        Channel duplicateCommand = channel("", "", "HTTP://STREAM/ONE");
        List<Channel> merged = reloader.merge(List.of(byChannelId), Arrays.asList(duplicateId, byName, duplicateName, byCommand, duplicateCommand, null));
        assertEquals(3, merged.size());
        assertEquals("ID-1", merged.get(0).getChannelId());
        assertEquals("Same Name", merged.get(1).getName());
        assertEquals("http://stream/one", merged.get(2).getCmd());
        assertEquals(Set.of("provider-1", "provider-3"), reloader.ids(List.of(first, byId)));
    }

    @Test
    void retainsOnlyVisibleCategoryChannelsAndCountsCensoring() {
        Channel visible = channel("visible", "Visible", "");
        visible.setCategoryId("v");
        Channel hidden = channel("hidden", "Hidden", "");
        hidden.setCategoryId("h");
        Channel unknown = channel("unknown", "Unknown", "");
        List<Channel> retained = reloader.retain(List.of(visible, hidden, unknown), Set.of("v", "h"), Set.of("v"), Map.of());

        assertEquals(List.of(visible, unknown), retained);
        assertEquals(2, reloader.unique(Map.of("one", List.of(visible, duplicate(visible)), "two", Arrays.asList(hidden, null))));
        assertEquals(0, reloader.unique(null));
        assertEquals(2, reloader.censored(4, 2, true));
        assertEquals(0, reloader.censored(4, 2, false));
        assertTrue(reloader.removed(2, 0, true));
        assertFalse(reloader.removed(0, 0, true));
        assertFalse(reloader.removed(2, 0, false));
        assertEquals(0, reloader.summary().categories());
    }

    @Test
    void buildsProviderParametersAndParsesLocalM3uCategories() throws Exception {
        assertEquals("get_genres", reloader.categoryParams(itv).get("action"));
        assertEquals("get_categories", reloader.categoryParams(vod).get("action"));
        assertEquals("4", reloader.allParams(4, 50).get("p"));
        assertEquals("50", reloader.allParams(4, 50).get("per_page"));
        assertFalse(reloader.allParams(null, null).containsKey("p"));

        Path playlist = tempDir.resolve("coverage.m3u");
        Files.writeString(playlist, """
                #EXTM3U
                #EXTINF:-1 tvg-id="news-id" group-title="News;Sports",News One
                https://stream.test/news
                #EXTINF:-1 group-title="",No Group
                https://stream.test/no-group
                """);
        Account account = new Account();
        account.setType(AccountType.M3U8_LOCAL);
        account.setM3u8Path(playlist.toString());

        assertTrue(reloader.categories(account).stream().anyMatch(entry -> "News".equals(entry.getGroupTitle())));
        assertEquals(3, reloader.entries(account).size());
        assertEquals(1, reloader.channels("News", account).size());
        assertEquals(1, reloader.channels(CategoryType.UNCATEGORIZED.displayName(), account).size());
        assertEquals(2, reloader.channels(CategoryType.ALL.displayName(), account).size());

        PlaylistEntry noId = new PlaylistEntry("", "News", "No id", "https://stream.test/no-id", null);
        Channel converted = reloader.convert(noId);
        assertNotNull(converted.getChannelId());
        assertEquals("No id", converted.getName());
    }

    @Test
    void filteringFlagsRespectPauseStateAndParentalLock() {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPauseFiltering(false);
        configuration.setFilterCategoriesList("Adult");
        configuration.setFilterChannelsList("Adult Channel");
        ConfigurationService.getInstance().save(configuration);

        Account unlocked = new Account();
        unlocked.setParentalLock(false);
        Account locked = new Account();
        locked.setParentalLock(true);
        assertTrue(reloader.categoryCensoring(null));
        assertTrue(reloader.categoryCensoring(locked));
        assertFalse(reloader.categoryCensoring(unlocked));
        assertTrue(reloader.channelCensoring(null));
        assertTrue(reloader.channelCensoring(locked));
        assertFalse(reloader.channelCensoring(unlocked));
    }

    private static Channel channel(String id, String name, String command) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd(command);
        return channel;
    }

    private static Channel duplicate(Channel source) {
        return channel(source.getChannelId(), source.getName(), source.getCmd());
    }

    private static final class ProbeReloader extends AbstractAccountCacheReloader {
        @Override
        public void reloadCache(Account account, com.uiptv.api.LoggerCallback logger) {
            throw new UnsupportedOperationException("The coverage probe does not perform cache reloads");
        }

        CategoryNormalization normalize(List<Category> categories) { return normalizeCategoriesByTitle(categories); }
        Map<String, String> canonicalIds(CategoryNormalization normalized) { return normalized.canonicalCategoryIdByOriginalId(); }
        String lookupCategory(String id, Map<String, String> ids) { return canonicalCategoryId(id, ids); }
        String key(String value) { return categoryLookupKey(value); }
        List<Channel> merge(List<Channel> left, List<Channel> right) { return mergeChannelsCaseInsensitive(left, right); }
        Set<String> ids(List<Category> categories) { return categoryIds(categories); }
        List<Channel> retain(List<Channel> channels, Set<String> known, Set<String> visible, Map<String, String> ids) {
            return retainChannelsForVisibleCategories(channels, known, visible, ids);
        }
        int unique(Map<String, List<Channel>> channels) { return uniqueChannelCount(channels); }
        int censored(int raw, int filtered, boolean enabled) { return censoredItemCount(raw, filtered, enabled); }
        boolean removed(int raw, int filtered, boolean enabled) { return wasEverythingRemovedByActiveCensoring(raw, filtered, enabled); }
        CensoringSummary summary() { return CensoringSummary.empty().add(null).addCategories(0).addChannels(0); }
        Map<String, String> categoryParams(Account.AccountAction action) { return getCategoryParams(action); }
        Map<String, String> allParams(Integer page, Integer perPage) { return getAllChannelsParams(page, perPage); }
        Set<PlaylistEntry> categories(Account account) { return loadM3uCategories(account); }
        List<PlaylistEntry> entries(Account account) { return loadM3uEntries(account); }
        List<Channel> channels(String category, Account account) { return m3u8Channels(category, account); }
        Channel convert(PlaylistEntry entry) { return toChannel(entry); }
        boolean categoryCensoring(Account account) { return shouldApplyCategoryCensoring(account); }
        boolean channelCensoring(Account account) { return shouldApplyChannelCensoring(account); }
    }
}
