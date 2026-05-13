package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PingStalkerPortalTest {

    @Test
    void ping_returnsSpecificEndpointParsedFromXpcomScript() {
        Account account = createPortalAccount("http://example.com/stalker_portal/c/");
        HttpUtil.HttpResult xpcom = new HttpUtil.HttpResult(
                200,
                "function x(){this.get_server_params=function(){var pattern='/c/';this.ajax_loader='/stalker_portal/server/load.php';};}",
                java.util.Map.of(),
                java.util.Map.of()
        );

        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.contains("xpcom.common.js"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(xpcom);

            assertEquals("http://example.com/stalker_portal/c/stalker_portal/server/load.php", PingStalkerPortal.ping(account));
        }
    }

    @Test
    void ping_fallsBackToDefaultEndpointWhenXpcomFetchHasNetworkError() {
        Account account = createPortalAccount("demo.example/stalker_portal/c/");

        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.contains("xpcom.common.js"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenThrow(new UnknownHostException("offline"));

            assertEquals("http://demo.example/stalker_portal/server/load.php", PingStalkerPortal.ping(account));
        }
    }

    @Test
    void ping_probesKnownPathsWhenXpcomScriptDoesNotResolveEndpoint() {
        Account account = createPortalAccount("http://portal.example");
        HttpUtil.HttpResult xpcom = new HttpUtil.HttpResult(200, "not useful", java.util.Map.of(), java.util.Map.of());
        HttpUtil.HttpResult miss = new HttpUtil.HttpResult(404, "", java.util.Map.of(), java.util.Map.of());
        HttpUtil.HttpResult hit = new HttpUtil.HttpResult(200, "{\"js\":{\"token\":\"ok\"}}", java.util.Map.of(), java.util.Map.of());

        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.contains("xpcom.common.js"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(xpcom);
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.startsWith("http://portal.example/c/portal.php?"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(miss);
            httpUtil.when(() -> HttpUtil.sendRequest(Mockito.startsWith("http://portal.example/stalker_portal/c/portal.php?"), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(hit);

            assertEquals("http://portal.example/stalker_portal/c/portal.php", PingStalkerPortal.ping(account));
        }
    }

    @Test
    void parsePortalApiServer_handlesPortalAndLoadPathFallbacks() {
        String portalScript = "x='/custom/portal.php';";
        String loadScript = "x='/alt/server/load.php';";

        assertEquals("http://example.com/custom/portal.php",
                PingStalkerPortal.parsePortalApiServer(portalScript, "http://example.com"));
        assertEquals("http://example.com/alt/server/load.php",
                PingStalkerPortal.parsePortalApiServer(loadScript, "http://example.com"));
    }

    @Test
    void parsePortalApiServer_fallsBackToDefaultEndpointForInvalidData() {
        assertEquals("http://example.com/server/load.php",
                PingStalkerPortal.parsePortalApiServer("bad", "example.com/c/"));
    }

    @Test
    void privateHelpers_coverHandshakeAndNormalizationBranches() throws Exception {
        assertTrue((Boolean) invokePrivate("hasHandshakeToken", new Class[]{String.class}, "{\"js\":{\"token\":\"abc\"}}"));
        assertTrue((Boolean) invokePrivate("hasHandshakeToken", new Class[]{String.class}, "{\"token\":\"abc\"}"));
        assertFalse((Boolean) invokePrivate("hasHandshakeToken", new Class[]{String.class}, "not-json"));
        assertFalse((Boolean) invokePrivate("hasHandshakeToken", new Class[]{String.class}, ""));

        assertEquals("http://", invokePrivate("ensureAbsoluteUrl", new Class[]{String.class}, ""));
        assertEquals("http://demo.example", invokePrivate("ensureAbsoluteUrl", new Class[]{String.class}, "demo.example"));
        assertEquals("http://demo.examplepath/", invokePrivate("ensureAbsoluteUrl", new Class[]{String.class}, "http://demo.examplepath"));

        assertEquals("http://base/path", invokePrivate("combineUrlWithPath", new Class[]{String.class, String.class}, "http://base", "/path"));
        assertEquals("http://base", invokePrivate("combineUrlWithPath", new Class[]{String.class, String.class}, "http://base", ""));

        assertTrue((Boolean) invokePrivate("isValidUrl", new Class[]{String.class}, "http://good.example/path"));
        assertFalse((Boolean) invokePrivate("isValidUrl", new Class[]{String.class}, "bad value"));

        assertEquals("http://example.com/server/load.php",
                invokePrivate("getDefaultApiEndpoint", new Class[]{String.class}, "http://example.com/c/"));
        assertEquals("http://example.comportal.php",
                invokePrivate("getDefaultApiEndpoint", new Class[]{String.class}, "example.com"));

        assertEquals("'/custom/portal.php'",
                invokePrivate("extractActiveAssignment", new Class[]{String.class, String.class},
                        "this.get_server_params=function(){this.ajax_loader='/custom/portal.php';}",
                        "this.ajax_loader="));
        assertNull(invokePrivate("extractPortalParam", new Class[]{String.class, String.class, String.class},
                "broken", "this.portal_port=", "document.URL.replace(pattern,"));
    }

    @Test
    void privateHelpers_coverNetworkClassificationAndParsedServerAssignments() throws Exception {
        assertTrue((Boolean) invokePrivate("isNetworkFailure", new Class[]{Throwable.class}, new ConnectException("down")));
        assertTrue((Boolean) invokePrivate("isNetworkFailure", new Class[]{Throwable.class}, new RuntimeException(new SocketTimeoutException("late"))));
        assertFalse((Boolean) invokePrivate("isNetworkFailure", new Class[]{Throwable.class}, new IllegalArgumentException("nope")));

        assertEquals("UnknownHostException: root-cause",
                invokePrivate("rootCauseMessage", new Class[]{Throwable.class},
                        new RuntimeException(new UnknownHostException("root-cause"))));

        String js = "this.get_server_params=function(){this.ajax_loader='/stalker_portal/server/load.php';};";

        assertEquals("http://demo.example/c/stalker_portal/server/load.php",
                invokePrivate("prepareServerUrl", new Class[]{String.class, String.class}, js, "http://demo.example/c/"));
        assertEquals("http://demo.example/c/stalker_portal/server/load.php",
                invokePrivate("ensureAbsoluteServerUrl", new Class[]{String.class, String.class},
                        "/stalker_portal/server/load.php", "http://demo.example/c/"));
    }

    private static Account createPortalAccount(String url) {
        Account account = new Account();
        account.setUrl(url);
        account.setMacAddress("00:11:22:33:44:55");
        account.setTimezone("Europe/London");
        account.setHttpMethod("GET");
        account.setType(AccountType.STALKER_PORTAL);
        return account;
    }

    private static Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = PingStalkerPortal.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
