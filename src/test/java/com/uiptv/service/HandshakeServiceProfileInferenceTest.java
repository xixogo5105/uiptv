package com.uiptv.service;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeServiceProfileInferenceTest {

    private final HandshakeService service = HandshakeService.getInstance();

    @Test
    void deriveAccountStatus_activeWhenStatusZeroAndNotBlocked() throws Exception {
        JSONObject json = new JSONObject()
                .put("status", 0)
                .put("blocked", "0");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JSONObject.class},
                json);

        assertEquals(AccountStatus.ACTIVE, status);
    }

    @Test
    void deriveAccountStatus_suspendedWhenBlocked() throws Exception {
        JSONObject json = new JSONObject()
                .put("status", 0)
                .put("blocked", "1");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JSONObject.class},
                json);

        assertEquals(AccountStatus.SUSPENDED, status);
    }

    @Test
    void deriveAccountStatus_activeWhenNotBlockedEvenIfStatusNonZero() throws Exception {
        JSONObject json = new JSONObject()
                .put("status", 2)
                .put("blocked", "0");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JSONObject.class},
                json);

        assertEquals(AccountStatus.ACTIVE, status);
    }

    @Test
    void deriveExpiryDate_prefersTariffExpiredDate() throws Exception {
        JSONObject json = new JSONObject()
                .put("tariff_expired_date", "2027-02-16 00:00:00")
                .put("expire_billing_date", "0000-00-00 00:00:00")
                .put("extend_at", "1773074900");

        String result = invokePrivate("deriveExpiryDate",
                new Class[]{JSONObject.class},
                json);

        assertEquals("2027-02-16 00:00:00", result);
    }

    @Test
    void deriveExpiryDate_fallsBackToExtendAt() throws Exception {
        long epoch = 1_700_000_000L;
        JSONObject json = new JSONObject()
                .put("tariff_expired_date", JSONObject.NULL)
                .put("expire_billing_date", "0000-00-00 00:00:00")
                .put("extend_at", String.valueOf(epoch));

        String result = invokePrivate("deriveExpiryDate",
                new Class[]{JSONObject.class},
                json);

        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(epoch));
        assertEquals(expected, result);
    }

    @Test
    void applyAllowedStbTypes_setsPreferredWhenMag250MissingAndClearsWhenPresent() throws Exception {
        AccountInfo info = new AccountInfo();
        JSONObject withoutMag = new JSONObject()
                .put("allowed_stb_types", new org.json.JSONArray().put("mag270").put("mag275"));

        boolean updated = invokePrivate("applyAllowedStbTypes",
                new Class[]{AccountInfo.class, JSONObject.class},
                info, withoutMag);

        assertTrue(updated);
        assertEquals("mag270", info.getPreferredStbType());
        assertNotNull(info.getAllowedStbTypesJson());

        JSONObject withMag = new JSONObject()
                .put("allowed_stb_types", new org.json.JSONArray().put("mag250").put("mag275"));

        updated = invokePrivate("applyAllowedStbTypes",
                new Class[]{AccountInfo.class, JSONObject.class},
                info, withMag);

        assertTrue(updated);
        assertTrue(info.getPreferredStbType() == null || info.getPreferredStbType().isBlank());
    }

    @Test
    void applyPasswordHashes_hashesAndVerifies() throws Exception {
        AccountInfo info = new AccountInfo();
        JSONObject json = new JSONObject()
                .put("pass", "pass1")
                .put("parent_password", "parent1")
                .put("password", "login1")
                .put("settings_password", "settings1")
                .put("account_page_by_password", "page1");

        boolean updated = invokePrivate("applyPasswordHashes",
                new Class[]{AccountInfo.class, JSONObject.class},
                info, json);

        assertTrue(updated);
        assertTrue(verifyPassword("pass1", info.getPassHash()));
        assertTrue(verifyPassword("parent1", info.getParentPasswordHash()));
        assertTrue(verifyPassword("login1", info.getPasswordHash()));
        assertTrue(verifyPassword("settings1", info.getSettingsPasswordHash()));
        assertTrue(verifyPassword("page1", info.getAccountPagePasswordHash()));
        assertFalse(verifyPassword("wrong", info.getPassHash()));
    }

    @Test
    void applyProfileDetails_storesRawProfileJson() throws Exception {
        AccountInfo info = new AccountInfo();
        String rawJson = "{\"js\":{\"id\":\"3956661\",\"blocked\":\"0\"}}";

        boolean updated = invokePrivate("applyProfileDetails",
                new Class[]{AccountInfo.class, String.class},
                info, rawJson);

        assertTrue(updated);
        assertEquals(rawJson, info.getProfileJson());
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = HandshakeService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(service, args);
    }

    private boolean verifyPassword(String raw, String storedHash) throws Exception {
        Method method = HandshakeService.class.getDeclaredMethod("verifyPassword", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, raw, storedHash);
    }
}
