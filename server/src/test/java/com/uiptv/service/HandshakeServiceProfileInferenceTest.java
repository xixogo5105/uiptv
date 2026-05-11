package com.uiptv.service;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;
import kotlinx.serialization.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.uiptv.util.json.JsonAccessKt.parseJsonObject;

class HandshakeServiceProfileInferenceTest {

    private final HandshakeService service = new HandshakeService();

    @Test
    void deriveAccountStatus_activeWhenStatusZeroAndNotBlocked() throws Exception {
        JsonObject json = parseJsonObject("{\"status\":0,\"blocked\":\"0\"}");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JsonObject.class},
                json);

        assertEquals(AccountStatus.ACTIVE, status);
    }

    @Test
    void deriveAccountStatus_suspendedWhenBlocked() throws Exception {
        JsonObject json = parseJsonObject("{\"status\":0,\"blocked\":\"1\"}");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JsonObject.class},
                json);

        assertEquals(AccountStatus.SUSPENDED, status);
    }

    @Test
    void deriveAccountStatus_activeWhenNotBlockedEvenIfStatusNonZero() throws Exception {
        JsonObject json = parseJsonObject("{\"status\":2,\"blocked\":\"0\"}");

        AccountStatus status = invokePrivate("deriveAccountStatus",
                new Class[]{JsonObject.class},
                json);

        assertEquals(AccountStatus.ACTIVE, status);
    }

    @Test
    void deriveExpiryDate_prefersTariffExpiredDate() throws Exception {
        JsonObject json = parseJsonObject("""
                {"tariff_expired_date":"2027-02-16 00:00:00","expire_billing_date":"0000-00-00 00:00:00","extend_at":"1773074900"}
                """);

        String result = invokePrivate("deriveExpiryDate",
                new Class[]{JsonObject.class},
                json);

        assertEquals("2027-02-16 00:00:00", result);
    }

    @Test
    void deriveExpiryDate_fallsBackToExtendAt() throws Exception {
        long epoch = 1_700_000_000L;
        JsonObject json = parseJsonObject("""
                {"tariff_expired_date":null,"expire_billing_date":"0000-00-00 00:00:00","extend_at":"%s"}
                """.formatted(epoch));

        String result = invokePrivate("deriveExpiryDate",
                new Class[]{JsonObject.class},
                json);

        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(epoch));
        assertEquals(expected, result);
    }

    @Test
    void applyAllowedStbTypes_setsPreferredWhenMag250MissingAndClearsWhenPresent() throws Exception {
        AccountInfo info = new AccountInfo();
        JsonObject withoutMag = parseJsonObject("""
                {"allowed_stb_types":["mag270","mag275"]}
                """);

        boolean updated = invokePrivate("applyAllowedStbTypes",
                new Class[]{AccountInfo.class, JsonObject.class},
                info, withoutMag);

        assertTrue(updated);
        assertEquals("mag270", info.getPreferredStbType());
        assertNotNull(info.getAllowedStbTypesJson());

        JsonObject withMag = parseJsonObject("""
                {"allowed_stb_types":["mag250","mag275"]}
                """);

        updated = invokePrivate("applyAllowedStbTypes",
                new Class[]{AccountInfo.class, JsonObject.class},
                info, withMag);

        assertTrue(updated);
        assertTrue(info.getPreferredStbType() == null || info.getPreferredStbType().isBlank());
    }

    @Test
    void applyPasswordHashes_hashesAndVerifies() throws Exception {
        AccountInfo info = new AccountInfo();
        JsonObject json = parseJsonObject("""
                {"pass":"pass1","parent_password":"parent1","password":"login1","settings_password":"settings1","account_page_by_password":"page1"}
                """);

        boolean updated = invokePrivate("applyPasswordHashes",
                new Class[]{AccountInfo.class, JsonObject.class},
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
