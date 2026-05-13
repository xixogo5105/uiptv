package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiptUtilsTest {

    @Test
    void validatesUrlsMacsAndXtreamLinks() {
        assertTrue(UiptUtils.isValidURL("http://example.test/get.php?username=u&password=p"));
        assertFalse(UiptUtils.isValidURL("not a url"));
        assertTrue(UiptUtils.isValidMACAddress("00:1A:79:AA:BB:CC"));
        assertTrue(UiptUtils.isValidMACAddress("001A.79AA.BBCC"));
        assertFalse(UiptUtils.isValidMACAddress("bad"));
        assertFalse(UiptUtils.isValidMACAddress(""));
        assertFalse(UiptUtils.isValidMACAddress(null));

        String xtream = "http://host.test/get.php?username=user&password=pass&type=m3u";
        assertTrue(UiptUtils.isUrlValidXtremeLink(xtream));
        assertFalse(UiptUtils.isUrlValidXtremeLink("http://host.test/get.php?username=user"));
        assertFalse(UiptUtils.isUrlValidXtremeLink("not a url"));
        assertEquals("http://host.test/", UiptUtils.getPathFromUrl(xtream));
        assertEquals("user", UiptUtils.getUserNameFromUrl(xtream));
        assertEquals("pass", UiptUtils.getPasswordNameFromUrl(xtream));
        assertEquals("bad url", UiptUtils.getUserNameFromUrl("bad url"));
        assertEquals("bad url", UiptUtils.getPasswordNameFromUrl("bad url"));
        assertNull(UiptUtils.getPathFromUrl(null));
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
    void sanitizeStalkerTextMapsKnownDecorativeCodepoints() {
        String input = codepoints(
                0x1D400, 0x1D41A, 0x1D7CE, 0x1F150, 0x1F170,
                0x029C, 0x1D0F, 0x1D1B, 0x1D07, 0x1D00, 0x029F, 0x1D18, 0x0280,
                0x1D1C, 0x0274, 0x1D0D, 0x026A, 0x1D04, 0x1D20, 0x1D05,
                0x2776, 0x2777, 0x278C,
                0x279F, 0x27A4, 0x27D0, 0x1F511, 0x1F194, 0x1F4DD, 0x1F538,
                0x25CF, 0x251C, 0x2500, 0x2502, 0x2570, 0x256D, 0x1F3C1, 0x23F0,
                0x1F47D, 0x1F510, 0x1FA62, 0x1F3AF, 0x1F4EE, 0x1F310, 0x26D1,
                0x1F30D, 0x269C, 0x2620, 0x2605, 0x2606, 0x1F5A5
        );

        assertEquals("Aa0AAHOTEALPRUNMICVD123                            ", UiptUtils.sanitizeStalkerText(input));
        assertEquals("Plain", UiptUtils.sanitizeStalkerText("Plain"));
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

    private String codepoints(int... values) {
        StringBuilder builder = new StringBuilder();
        for (int value : values) {
            builder.appendCodePoint(value);
        }
        return builder.toString();
    }
}
