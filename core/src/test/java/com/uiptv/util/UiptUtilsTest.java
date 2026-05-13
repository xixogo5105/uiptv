package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiptUtilsTest {

    @Test
    void validatesUrlsMacsAndXtreamLinks() {
        assertTrue(UiptUtils.isValidURL("http://example.test/get.php?username=u&password=p"));
        assertFalse(UiptUtils.isValidURL("not a url"));
        assertTrue(UiptUtils.isValidMACAddress("00:1A:79:AA:BB:CC"));
        assertTrue(UiptUtils.isValidMACAddress("001A.79AA.BBCC"));
        assertFalse(UiptUtils.isValidMACAddress("bad"));

        String xtream = "http://host.test/get.php?username=user&password=pass&type=m3u";
        assertTrue(UiptUtils.isUrlValidXtremeLink(xtream));
        assertFalse(UiptUtils.isUrlValidXtremeLink("http://host.test/get.php?username=user"));
        assertEquals("http://host.test/", UiptUtils.getPathFromUrl(xtream));
        assertEquals("user", UiptUtils.getUserNameFromUrl(xtream));
        assertEquals("pass", UiptUtils.getPasswordNameFromUrl(xtream));
    }

    @Test
    void namesAndSanitizesText() {
        assertEquals("example.test", UiptUtils.getNameFromUrl("http://example.test/path"));
        assertEquals("bad url", UiptUtils.getNameFromUrl("bad url"));
        assertEquals("A B", UiptUtils.replaceAllNonPrintableChars("A\u0000B"));
        assertEquals("", UiptUtils.sanitizeStalkerText(null));

        String stylized = new String(Character.toChars(0x1D407))
                + new String(Character.toChars(0x1D422))
                + new String(Character.toChars(0x2777));
        assertEquals("Hi2", UiptUtils.sanitizeStalkerText(stylized));
    }

    @Test
    void uniqueNameFromUrlIncrementsUntilAvailable() {
        AccountService accountService = Mockito.mock(AccountService.class);
        try (MockedStatic<AccountService> accountStatic = Mockito.mockStatic(AccountService.class)) {
            accountStatic.when(AccountService::getInstance).thenReturn(accountService);
            Mockito.when(accountService.getByName("example.test (1)")).thenReturn(new Account());
            Mockito.when(accountService.getByName("example.test (2)")).thenReturn(null);

            assertEquals("example.test (2)", UiptUtils.getUniqueNameFromUrl("http://example.test/path"));
        }
        assertEquals("bad url", UiptUtils.getUniqueNameFromUrl("bad url"));
    }
}
