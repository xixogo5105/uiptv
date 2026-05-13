package com.uiptv.service;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeServiceTest {

    @Test
    void parseJasonToken_returnsTokenWhenPresent() {
        String json = """
                {
                  "js": {
                    "token": "abc123token"
                  }
                }
                """;

        String token = HandshakeService.getInstance().parseJasonToken(json);

        assertEquals("abc123token", token);
    }

    @Test
    void profileAndAccountInfoHelpers_mapPortalFieldsAndPasswords() throws Exception {
        HandshakeService service = HandshakeService.getInstance();
        AccountInfo info = new AccountInfo();
        JSONObject profile = new JSONObject()
                .put("blocked", "yes")
                .put("tariff_plan_id", "plan-1")
                .put("tariff_name", "Premium")
                .put("default_timezone", "Europe/London")
                .put("expire_billing_date", "2026-05-13 10:15:00")
                .put("allowed_stb_types", new JSONArray().put("MAG540"))
                .put("allowed_stb_types_for_local_recording", new JSONArray().put("MAG540"))
                .put("pass", "secret");

        assertTrue((Boolean) invoke(service, "applyProfileDetails", new Class[]{AccountInfo.class, String.class}, info, new JSONObject().put("js", profile).toString()));
        assertEquals(AccountStatus.SUSPENDED, info.getAccountStatus());
        assertEquals("Premium", info.getTariffName());
        assertEquals("plan-1", info.getTariffPlan());
        assertEquals("Europe/London", info.getDefaultTimezone());
        assertEquals("2026-05-13 10:15:00", info.getExpireDate());
        assertEquals("[\"MAG540\"]", info.getAllowedStbTypesJson());
        assertEquals("MAG540", info.getPreferredStbType());
        assertTrue(info.getPassHash().startsWith("pbkdf2_sha256$"));

        JSONObject accountInfo = new JSONObject()
                .put("account_info", new JSONObject()
                        .put("balance", "12.50")
                        .put("tariff_name", "Basic")
                        .put("tariff_plan", "plan-2")
                        .put("timezone", "UTC")
                        .put("end_date", "May 13, 2026, 4:30 PM")
                        .put("password", "account-secret"));
        assertTrue((Boolean) invoke(service, "applyAccountInfoDetails", new Class[]{AccountInfo.class, String.class}, info, accountInfo.toString()));
        assertEquals("12.50", info.getAccountBalance());
        assertEquals("Basic", info.getTariffName());
        assertEquals("plan-2", info.getTariffPlan());
        assertEquals("UTC", info.getDefaultTimezone());
        assertEquals("2026-05-13 16:30:00", info.getExpireDate());
        assertTrue(info.getPasswordHash().startsWith("pbkdf2_sha256$"));
    }

    @Test
    void parsingAndSelectionHelpers_coverFallbacks() throws Exception {
        HandshakeService service = HandshakeService.getInstance();

        assertEquals("", service.parseJasonToken("{\"js\":{\"token\":\"\"}}"));
        assertEquals("2026-05-13 00:00:00", invoke(service, "normalizeAccountInfoExpiry", new Class[]{String.class}, "2026/05/13"));
        assertEquals("2026-05-13 00:00:00", invoke(service, "normalizeAccountInfoExpiry", new Class[]{String.class}, "05/13/2026"));
        assertEquals("", invoke(service, "normalizeAccountInfoExpiry", new Class[]{String.class}, "not a date"));
        assertEquals("2026-05-13 16:00:00", invoke(service, "resolveExtendAt", new Class[]{String.class}, "1778688000"));
        assertEquals("", invoke(service, "resolveExtendAt", new Class[]{String.class}, "0"));
        assertEquals("", invoke(service, "resolveExtendAt", new Class[]{String.class}, "bad"));

        assertEquals(AccountStatus.ACTIVE, invoke(service, "deriveAccountStatus", new Class[]{JSONObject.class}, new JSONObject()));
        assertTrue((Boolean) invoke(service, "isTruthy", new Class[]{String.class}, "true"));
        assertFalse((Boolean) invoke(service, "isTruthy", new Class[]{String.class}, "no"));
        assertEquals("", invoke(service, "selectPreferredStbType", new Class[]{String.class}, "[\"MAG250\",\"MAG540\"]"));
        assertEquals("MAG540", invoke(service, "selectPreferredStbType", new Class[]{String.class}, "[\"MAG540\"]"));
        assertEquals("", invoke(service, "selectPreferredStbType", new Class[]{String.class}, "bad"));
        assertTrue((Boolean) invoke(service, "allowedContainsMag250", new Class[]{String.class}, "[\"MAG250\"]"));
        assertFalse((Boolean) invoke(service, "allowedContainsMag250", new Class[]{String.class}, "[\"MAG540\"]"));
        assertEquals("MAG540", invoke(service, "firstAllowedStbType", new Class[]{String.class, String.class}, "[\"MAG540\"]", "MAG250"));
        assertEquals("MAG250", invoke(service, "firstAllowedStbType", new Class[]{String.class, String.class}, "bad", "MAG250"));
        assertEquals("", invoke(service, "normalizeArray", new Class[]{JSONArray.class}, new JSONArray()));
        assertEquals("[\"A\"]", invoke(service, "normalizeArray", new Class[]{JSONArray.class}, new JSONArray().put("A")));
        assertEquals("first", invoke(service, "firstNonBlank", new Class[]{String[].class}, (Object) new String[]{"", "first"}));
        assertEquals("", invoke(service, "firstNonBlank", new Class[]{String[].class}, (Object) null));
    }

    @Test
    void passwordHelpers_verifyAndRejectMalformedHashes() throws Exception {
        HandshakeService service = HandshakeService.getInstance();

        String hash = (String) invoke(service, "hashPassword", new Class[]{String.class}, "secret");
        assertTrue((Boolean) invoke(service, "verifyPassword", new Class[]{String.class, String.class}, "secret", hash));
        assertFalse((Boolean) invoke(service, "verifyPassword", new Class[]{String.class, String.class}, "wrong", hash));
        assertFalse((Boolean) invoke(service, "verifyPassword", new Class[]{String.class, String.class}, "secret", "bad"));
        assertFalse((Boolean) invoke(service, "verifyPassword", new Class[]{String.class, String.class}, "", hash));
        assertTrue((Boolean) invoke(service, "slowEquals", new Class[]{byte[].class, byte[].class}, new byte[]{1, 2}, new byte[]{1, 2}));
        assertFalse((Boolean) invoke(service, "slowEquals", new Class[]{byte[].class, byte[].class}, new byte[]{1, 2}, new byte[]{1, 3}));
        assertFalse((Boolean) invoke(service, "slowEquals", new Class[]{byte[].class, byte[].class}, null, new byte[]{1}));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
