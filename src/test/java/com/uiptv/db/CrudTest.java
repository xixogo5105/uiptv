package com.uiptv.db;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CrudTest {

    @TempDir
    Path tempDir;

    private File testDbFile;

    @BeforeEach
    public void setUp() throws Exception {
        // Use the temporary directory provided by JUnit
        testDbFile = tempDir.resolve("test_uiptv.db").toFile();
        
        // Set the database path directly using the new method in SQLConnection
        // This avoids file system hacks and ensures the test uses the correct DB.
        SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() {
        // Explicitly delete the database file to ensure test isolation.
        // The @TempDir annotation will handle the cleanup of the parent directory.
        if (testDbFile != null && testDbFile.exists()) {
            testDbFile.delete();
        }
    }

    @Test
    public void testAccountCrud() {
        System.out.println("Testing Account CRUD...");
        AccountService accountService = AccountService.getInstance();

        // Create
        Account account = new Account("TestAccount", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        accountService.save(account);

        Account fetchedAccount = accountService.getByName("TestAccount");
        assertNotNull(fetchedAccount, "Account should be saved");
        assertEquals("http://test.com", fetchedAccount.getUrl());

        // Update
        fetchedAccount.setUsername("newUser");
        accountService.save(fetchedAccount);
        Account updatedAccount = accountService.getByName("TestAccount");
        assertEquals("newUser", updatedAccount.getUsername());

        // Delete
        accountService.delete(updatedAccount.getDbId());
        Account deletedAccount = accountService.getByName("TestAccount");
        assertNull(deletedAccount, "Account should be deleted");
    }

    @Test
    public void testCategoryCrud() {
        System.out.println("Testing Category CRUD...");
        AccountService accountService = AccountService.getInstance();
        
        // Setup Account
        Account account = new Account("CatTestAccount", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("CatTestAccount");

        // Create Categories (using CategoryDb directly as Service mostly fetches)
        CategoryDb categoryDb = CategoryDb.get();
        List<Category> categories = new ArrayList<>();
        Category cat1 = new Category("cat1", "Category 1", "alias1", false, 0);
        categories.add(cat1);
        
        categoryDb.saveAll(categories, savedAccount);

        // Read
        List<Category> fetchedCategories = categoryDb.getCategories(savedAccount);
        assertEquals(1, fetchedCategories.size());
        assertEquals("Category 1", fetchedCategories.get(0).getTitle());

        // Delete
        categoryDb.deleteByAccount(savedAccount);
        List<Category> emptyCategories = categoryDb.getCategories(savedAccount);
        assertTrue(emptyCategories.isEmpty());
        
        // Cleanup
        accountService.delete(savedAccount.getDbId());
    }

    @Test
    public void testChannelCrudAndPlayerService() throws IOException {
        System.out.println("Testing Channel CRUD and PlayerService...");
        AccountService accountService = AccountService.getInstance();
        
        // 1. Setup Account
        Account account = new Account("PlayerTestAccount", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("PlayerTestAccount");

        // 2. Setup Category
        CategoryDb categoryDb = CategoryDb.get();
        List<Category> categories = new ArrayList<>();
        Category cat1 = new Category("cat1", "Player Category", "alias1", false, 0);
        categories.add(cat1);
        categoryDb.saveAll(categories, savedAccount);
        Category savedCat = categoryDb.getCategories(savedAccount).get(0);

        // 3. Create Channel with a specific command for PlayerService
        ChannelDb channelDb = ChannelDb.get();
        List<Channel> channels = new ArrayList<>();
        String command = "ffmpeg http://someurl.com/playfile.ts";
        Channel ch1 = new Channel("ch1", "Player Test Channel", "1", command, null, null, null, "logo", 0, 1, 1, null, null, null, null, null);
        channels.add(ch1);
        
        channelDb.saveAll(channels, savedCat.getDbId(), savedAccount);

        // 4. Read data back from DB
        List<Channel> fetchedChannels = channelDb.getChannels(savedCat.getDbId());
        assertEquals(1, fetchedChannels.size());
        Channel savedChannel = fetchedChannels.get(0);
        assertEquals("Player Test Channel", savedChannel.getName());

        // 5. Test PlayerService with the data from the database
        PlayerService playerService = PlayerService.getInstance();
        PlayerResponse response = playerService.get(savedAccount, savedChannel);

        // 6. Assert the URL is processed correctly
        assertEquals("http://someurl.com/playfile.ts", response.getUrl());

        // 7. Cleanup
        accountService.delete(savedAccount.getDbId());
        categoryDb.deleteByAccount(savedAccount);
        channelDb.deleteByAccount(savedAccount.getDbId());
    }

    @Test
    public void testGlobalCacheClear() {
        System.out.println("Testing Global Cache Clear...");
        AccountService accountService = AccountService.getInstance();
        CacheServiceImpl cacheService = new CacheServiceImpl();

        // 1. Create Account
        Account account = new Account("FlowAccount", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("FlowAccount");
        assertNotNull(savedAccount);

        // 2. Create Categories
        CategoryDb categoryDb = CategoryDb.get();
        List<Category> categories = new ArrayList<>();
        Category cat1 = new Category("cat1", "Category 1", "alias1", false, 0);
        categories.add(cat1);
        categoryDb.saveAll(categories, savedAccount);
        
        List<Category> savedCategories = categoryDb.getCategories(savedAccount);
        assertEquals(1, savedCategories.size());
        Category savedCat = savedCategories.get(0);

        // 3. Create Channels
        ChannelDb channelDb = ChannelDb.get();
        List<Channel> channels = new ArrayList<>();
        Channel ch1 = new Channel("ch1", "Channel 1", "1", "cmd", null, null, null, "logo", 0, 1, 1, null, null, null, null, null);
        channels.add(ch1);
        channelDb.saveAll(channels, savedCat.getDbId(), savedAccount);
        
        assertEquals(1, channelDb.getChannelCountForAccount(savedAccount.getDbId()));

        // 4. Clear Cache using the global method
        cacheService.clearAllCache();

        // Verify cleared
        assertEquals(0, channelDb.getChannelCountForAccount(savedAccount.getDbId()));
        assertTrue(categoryDb.getCategories(savedAccount).isEmpty());

        // Cleanup
        accountService.delete(savedAccount.getDbId());
    }

    @Test
    public void testBookmarkCrud() {
        System.out.println("Testing Bookmark CRUD...");
        BookmarkService bookmarkService = BookmarkService.getInstance();
        AccountService accountService = AccountService.getInstance();

        // 1. Setup Account
        Account account = new Account("BookmarkAccount", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("BookmarkAccount");

        // 2. Create Bookmark
        Bookmark bookmark = new Bookmark(savedAccount.getAccountName(), "Category 1", "ch1", "Test Channel", "cmd", savedAccount.getServerPortalUrl(), "cat1");
        bookmark.setAccountAction(savedAccount.getAction());
        bookmark.setDrmType("drm");
        bookmark.setDrmLicenseUrl("license");
        bookmark.setClearKeysJson("keys");
        bookmark.setInputstreamaddon("addon");
        bookmark.setManifestType("manifest");
        bookmark.setCategoryJson("catJson");
        bookmark.setChannelJson("chJson");
        bookmark.setVodJson("vodJson");
        bookmark.setSeriesJson("seriesJson");
        bookmarkService.save(bookmark);

        // 3. Read
        List<Bookmark> bookmarks = bookmarkService.read();
        assertFalse(bookmarks.isEmpty());
        assertTrue(bookmarkService.isChannelBookmarked(bookmark));
        
        Bookmark fetchedBookmark = bookmarkService.getBookmark(bookmark);
        assertNotNull(fetchedBookmark);
        assertEquals("Test Channel", fetchedBookmark.getChannelName());

        // 4. Toggle (Remove)
        bookmarkService.toggleBookmark(bookmark);
        assertFalse(bookmarkService.isChannelBookmarked(bookmark));

        // 5. Bookmark Categories
        // The constructor requires (id, name), passing null for ID as it's auto-generated or not needed for creation
        BookmarkCategory category = new BookmarkCategory(null, "My Favorites");
        bookmarkService.addCategory(category);
        
        List<BookmarkCategory> categories = bookmarkService.getAllCategories();
        assertTrue(categories.stream().anyMatch(c -> c.getName().equals("My Favorites")));
        
        // Cleanup Categories
        // Need to find the ID to remove, assuming name is unique or we get the last one
        BookmarkCategory savedCategory = categories.stream().filter(c -> c.getName().equals("My Favorites")).findFirst().orElse(null);
        if (savedCategory != null) {
            bookmarkService.removeCategory(savedCategory);
        }
        
        // Cleanup Account
        accountService.delete(savedAccount.getDbId());
    }

    @Test
    public void testConfigurationCrud() {
        System.out.println("Testing Configuration CRUD...");
        ConfigurationService configService = ConfigurationService.getInstance();

        // 1. Create/Update Configuration
        Configuration config = new Configuration("path1", "path2", "path3", "default", "catFilter", "chanFilter", true, "Arial", "12", "Bold", true, "8080", false, false);
        configService.save(config);

        // 2. Read
        Configuration fetchedConfig = configService.read();
        assertNotNull(fetchedConfig);
        assertEquals("path1", fetchedConfig.getPlayerPath1());
        assertEquals("8080", fetchedConfig.getServerPort());
        assertTrue(fetchedConfig.isDarkTheme());

        // 3. Update
        fetchedConfig.setServerPort("9090");
        configService.save(fetchedConfig);
        
        Configuration updatedConfig = configService.read();
        assertEquals("9090", updatedConfig.getServerPort());
    }
}
