package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class AccountCategoryDbCoverageTest extends DbBackedTest {

    @Test
    void accountDb_saveUpdateAndDefaultPopulatePaths() throws Exception {
        AccountDb accountDb = AccountDb.get();
        assertNotNull(accountDb);
        assertNotNull(AccountDb.get());

        Account account = new Account("acc-main", "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://portal.test/list.m3u8", true);
        account.setHttpMethod("POST");
        account.setTimezone("America/New_York");
        account.setServerPortalUrl("http://portal.test/server");
        accountDb.save(account);

        Account saved = accountDb.getAccountByName("acc-main");
        assertNotNull(saved);
        assertEquals("POST", saved.getHttpMethod());
        assertEquals("America/New_York", saved.getTimezone());
        assertTrue(saved.isPinToTop());
        assertEquals("http://portal.test/server", saved.getServerPortalUrl());

        saved.setUsername("new-user");
        saved.setHttpMethod("PUT");
        saved.setTimezone("Asia/Tokyo");
        saved.setPinToTop(false);
        accountDb.save(saved);

        Account updated = accountDb.getAccountById(saved.getDbId());
        assertEquals("new-user", updated.getUsername());
        assertEquals("PUT", updated.getHttpMethod());
        assertEquals("Asia/Tokyo", updated.getTimezone());
        assertFalse(updated.isPinToTop());

        assertFalse(accountDb.getAccounts().isEmpty());

        Account unsaved = new Account("unsaved", "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, null, false);
        unsaved.setServerPortalUrl("http://portal.test/ignored");
        accountDb.saveServerPortalUrl(unsaved);
        assertNull(accountDb.getAccountByName("unsaved"));

        try (Connection conn = SQLConnection.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO Account (accountName, username, password, url, macAddress, macAddressList, serialNumber, deviceId1, "
                             + "deviceId2, signature, epg, m3u8Path, type, serverPortalUrl, pinToTop, resolveChainAndDeepRedirects, httpMethod, timezone) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, "acc-defaults");
            ps.setString(2, "user");
            ps.setString(3, "pass");
            ps.setString(4, "http://portal.test");
            ps.setString(5, null);
            ps.setString(6, null);
            ps.setString(7, null);
            ps.setString(8, null);
            ps.setString(9, null);
            ps.setString(10, null);
            ps.setString(11, null);
            ps.setString(12, null);
            ps.setString(13, "");
            ps.setString(14, null);
            ps.setString(15, "0");
            ps.setString(16, "0");
            ps.setString(17, "");
            ps.setString(18, "");
            ps.executeUpdate();
        }

        Account defaults = accountDb.getAccountByName("acc-defaults");
        assertNotNull(defaults);
        assertEquals(AccountType.STALKER_PORTAL, defaults.getType());
        assertEquals("GET", defaults.getHttpMethod());
        assertEquals("Europe/London", defaults.getTimezone());
    }

    @Test
    void categoryDb_crudAndErrorPathCoverage() throws Exception {
        AccountDb accountDb = AccountDb.get();
        Account accountA = new Account("cat-a", "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, null, false);
        accountDb.save(accountA);
        accountA = accountDb.getAccountByName("cat-a");

        Account accountB = new Account("cat-b", "user", "pass", "http://portal.test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, null, false);
        accountDb.save(accountB);
        accountB = accountDb.getAccountByName("cat-b");

        CategoryDb categoryDb = CategoryDb.get();
        categoryDb.saveAll(List.of(
                new Category("news", "News", "news", true, 1),
                new Category("sports", "Sports", "sports", false, 0)
        ), accountA);

        List<Category> accountCategories = categoryDb.getCategories(accountA);
        assertEquals(2, accountCategories.size());

        Category sports = accountCategories.stream()
                .filter(c -> "Sports".equals(c.getTitle()))
                .findFirst()
                .orElseThrow();

        assertNotNull(categoryDb.getCategoryByDbId(sports.getDbId(), accountA));
        assertNull(categoryDb.getCategoryByDbId(sports.getDbId(), accountB));
        assertEquals(2, categoryDb.getAllAccountCategories(accountA.getDbId()).size());

        categoryDb.deleteByAccount(accountA);
        assertTrue(categoryDb.getCategories(accountA).isEmpty());

        Connection conn = Mockito.mock(Connection.class);
        Mockito.when(conn.prepareStatement(anyString())).thenThrow(new SQLException("boom"));
        try (MockedStatic<SQLConnection> mocked = Mockito.mockStatic(SQLConnection.class)) {
            mocked.when(SQLConnection::connect).thenReturn(conn);
            Account failing = new Account("fail", "user", "pass", "http://portal.test", null, null, null, null, null, null,
                    AccountType.M3U8_URL, null, null, false);
            failing.setDbId("999");
            DatabaseAccessException ex = assertThrows(DatabaseAccessException.class, () -> categoryDb.deleteByAccount(failing));
            assertTrue(ex.getMessage().contains("delete"));
        }
    }
}
