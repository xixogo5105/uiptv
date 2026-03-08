package com.uiptv.util;

import com.uiptv.model.Account;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityEdgeCaseTest {

    @Test
    void accountTypeAndStringUtils_coverLookupGuards_andEncodingHelpers() {
        assertEquals(AccountType.XTREME_API, AccountType.getAccountTypeByDisplay("xtreme api"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.getAccountTypeByDisplay(" "));
        assertThrows(IllegalArgumentException.class, () -> AccountType.getAccountTypeByDisplay("unknown"));

        JSONObject json = new JSONObject().put("value", "x");
        assertEquals("x", StringUtils.safeGetString(json, "value"));
        assertEquals("1", StringUtils.safeGetString(Map.of("value", 1), "value"));
        assertEquals("", StringUtils.safeJson(null));
        assertEquals("hello+world", StringUtils.nullSafeEncode("hello world"));
        assertEquals(0, StringUtils.length(null));
        assertTrue(StringUtils.isBlank(" \t"));
        assertFalse(StringUtils.isNotBlank(" "));
        assertEquals(0, StringUtils.split(" ").length);
        assertEquals("Cafe", StringUtils.safeUtf(" Cafe "));
    }

    @Test
    void localizedNumberResolver_andUiptUtils_coverFallbackAndParsingHelpers() {
        assertEquals("", LocalizedNumberLabelResolver.formatSeasonLabel("0", Locale.ENGLISH));
        assertEquals("", LocalizedNumberLabelResolver.formatEpisodeLabel("x", Locale.ENGLISH));
        assertEquals("", LocalizedNumberLabelResolver.formatTabLabel("3", null));
        assertFalse(LocalizedNumberLabelResolver.formatEpisodeLabel("11", Locale.forLanguageTag("ur")).isBlank());

        assertEquals("", UiptUtils.replaceAllNonPrintableChars(null));
        assertTrue(UiptUtils.replaceAllNonPrintableChars("A\u0000B").contains(" "));
        assertTrue(UiptUtils.isValidURL("https://example.com/path?q=1"));
        assertFalse(UiptUtils.isValidURL("not a url"));
        assertTrue(UiptUtils.isValidMACAddress("00:11:22:33:44:55"));
        assertFalse(UiptUtils.isValidMACAddress("bad-mac"));
        assertEquals("http://example.com/", UiptUtils.getPathFromUrl("http://example.com/get.php?username=u&password=p"));
        assertEquals("u", UiptUtils.getUserNameFromUrl("http://example.com/get.php?username=u&password=p"));
        assertEquals("p", UiptUtils.getPasswordNameFromUrl("http://example.com/get.php?username=u&password=p"));
        assertEquals("example.com", UiptUtils.getNameFromUrl("http://example.com/get.php?username=u&password=p"));
        assertTrue(UiptUtils.isUrlValidXtremeLink("http://example.com/get.php?username=u&password=p"));
        assertFalse(UiptUtils.isUrlValidXtremeLink("http://example.com/nope"));
        assertTrue(UiptUtils.sanitizeStalkerText("ʜᴏᴛ ❶").contains("HOT 1"));
        assertEquals("bad url", UiptUtils.getNameFromUrl("bad url"));
    }

    @Test
    void fetchApiAndHttpUtil_coverRequestBuildingHelpers_withoutRealNetwork() throws Exception {
        Account account = new Account();
        account.setUrl("example.com");
        account.setServerPortalUrl("http://portal.example.com/c/");
        account.setMacAddress("00:11:22:33:44:55");
        account.setType(AccountType.STALKER_PORTAL);
        account.setToken("abc");
        account.setHttpMethod("POST");

        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.sendRequest(
                    Mockito.anyString(),
                    Mockito.anyMap(),
                    Mockito.anyString(),
                    Mockito.any(),
                    Mockito.any(HttpUtil.RequestOptions.class)
            )).thenReturn(new HttpUtil.HttpResult(200, "ok", Map.of(), Map.of()));

            String body = FetchAPI.fetch(Map.of("token", "hello world"), account);
            assertEquals("ok", body);
        }

        assertTrue(FetchAPI.nullSafeBoolean(new JSONObject().put("active", true), "active"));
        assertFalse(FetchAPI.nullSafeBoolean(new JSONObject(), "missing"));
        assertEquals(7, FetchAPI.nullSafeInteger(new JSONObject().put("count", 7), "count"));
        assertEquals(-1, FetchAPI.nullSafeInteger(new JSONObject(), "missing"));
        assertEquals("value", FetchAPI.nullSafeString(new JSONObject().put("key", "value"), "key"));
        assertEquals("", FetchAPI.nullSafeString(new JSONObject(), "missing"));
        assertEquals("portal.php", FetchAPI.ServerType.PORTAL.getLoader());

        Method toSafeUri = HttpUtil.class.getDeclaredMethod("toSafeUri", String.class);
        toSafeUri.setAccessible(true);
        URI safeUri = (URI) toSafeUri.invoke(null, "http://example.com/play?q=a|b");
        assertNotNull(safeUri);
        assertTrue(safeUri.toString().contains("q=a%7Cb"));

        Method safeMethod = HttpUtil.class.getDeclaredMethod("safeMethod", String.class);
        safeMethod.setAccessible(true);
        assertEquals("GET", safeMethod.invoke(null, " "));
        assertEquals("POST", safeMethod.invoke(null, " post "));

        HttpUtil.HttpResult result = new HttpUtil.HttpResult(200, "body", Map.of("A", List.of("1")), Map.of("B", List.of("2")));
        assertEquals(200, result.statusCode());
        assertEquals("body", result.body());
        assertTrue(result.requestHeaders().containsKey("A"));

        HttpUtil.RequestOptions options = new HttpUtil.RequestOptions(false, true, 1, 2, 3);
        assertFalse(options.followRedirects());
        assertTrue(options.readBody());
        assertEquals(1, options.connectTimeoutSeconds());
        assertEquals(2, options.connectionRequestTimeoutSeconds());
        assertEquals(3, options.responseTimeoutSeconds());
        assertDoesNotThrow(HttpUtil.RequestOptions::defaults);
    }
}
