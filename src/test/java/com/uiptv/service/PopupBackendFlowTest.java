package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.test.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupBackendFlowTest extends DbBackedTest {

    @Test
    void accountSave_persistsDefaultMacAndNormalizedMacList() {
        AccountService accountService = AccountService.getInstance();

        Account account = new Account(
                "mac-flow-account",
                "user",
                "pass",
                "http://example.com",
                "AA:BB:CC:DD:EE:FF",
                "aa:bb:cc:dd:ee:ff, 11:22:33:44:55:66,11:22:33:44:55:66",
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        accountService.save(account);

        Account saved = accountService.getByName("mac-flow-account");
        assertNotNull(saved);
        assertEquals("AA:BB:CC:DD:EE:FF", saved.getMacAddress());
        assertEquals(
                Set.of("aa:bb:cc:dd:ee:ff", "11:22:33:44:55:66"),
                csvToLowerSet(saved.getMacAddressList())
        );
    }

    @Test
    void accountUpdate_replacesDefaultMacAndMacList() {
        AccountService accountService = AccountService.getInstance();

        Account initial = new Account(
                "mac-update-account",
                "user",
                "pass",
                "http://example.com",
                "AA:AA:AA:AA:AA:AA",
                "AA:AA:AA:AA:AA:AA,BB:BB:BB:BB:BB:BB",
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        accountService.save(initial);

        Account updated = new Account(
                "mac-update-account",
                "user",
                "pass",
                "http://example.com",
                "CC:CC:CC:CC:CC:CC",
                "CC:CC:CC:CC:CC:CC,DD:DD:DD:DD:DD:DD",
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        accountService.save(updated);

        Account saved = accountService.getByName("mac-update-account");
        assertNotNull(saved);
        assertEquals("CC:CC:CC:CC:CC:CC", saved.getMacAddress());
        assertEquals(
                Set.of("cc:cc:cc:cc:cc:cc", "dd:dd:dd:dd:dd:dd"),
                csvToLowerSet(saved.getMacAddressList())
        );
    }

    @Test
    void bookmarkCategoryRemove_deletesOnlySelectedCategoryById() {
        BookmarkService bookmarkService = BookmarkService.getInstance();

        bookmarkService.addCategory(new BookmarkCategory(null, "Sports"));
        bookmarkService.addCategory(new BookmarkCategory(null, "Sports"));

        var allCategories = bookmarkService.getAllCategories();
        assertEquals(2, allCategories.stream().filter(c -> "Sports".equals(c.getName())).count());

        BookmarkCategory toRemove = allCategories.stream()
                .filter(c -> "Sports".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        bookmarkService.removeCategory(toRemove);

        var remainingSports = bookmarkService.getAllCategories().stream()
                .filter(c -> "Sports".equals(c.getName()))
                .collect(Collectors.toList());
        assertEquals(1, remainingSports.size());
        assertTrue(remainingSports.stream().noneMatch(c -> toRemove.getId().equals(c.getId())));
    }

    private Set<String> csvToLowerSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
