package com.uiptv.db;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountInfoDbTest extends DbBackedTest {

    @Test
    void saveAndLoadAccountInfoPersistsEnumAndFields() {
        AccountInfo info = new AccountInfo();
        info.setAccountId("account-1");
        info.setExpireDate("2027-02-16 00:00:00");
        info.setAccountStatus(AccountStatus.ACTIVE);
        info.setAccountBalance("10.50");
        info.setTariffName("gold");
        info.setTariffPlan("plan-1");
        info.setDefaultTimezone("Europe/Amsterdam");
        info.setProfileJson("{\"js\":{\"id\":\"1\"}}");
        info.setPassHash("hash-pass");
        info.setParentPasswordHash("hash-parent");
        info.setPasswordHash("hash-password");
        info.setSettingsPasswordHash("hash-settings");
        info.setAccountPagePasswordHash("hash-page");
        info.setAllowedStbTypesJson("[\"mag250\",\"mag270\"]");
        info.setAllowedStbTypesForLocalRecordingJson("[\"mag250\"]");
        info.setPreferredStbType("mag270");

        AccountInfoDb.get().save(info);

        AccountInfo loaded = AccountInfoDb.get().getByAccountId("account-1");
        assertNotNull(loaded);
        assertEquals(AccountStatus.ACTIVE, loaded.getAccountStatus());
        assertEquals("2027-02-16 00:00:00", loaded.getExpireDate());
        assertEquals("gold", loaded.getTariffName());
        assertEquals("plan-1", loaded.getTariffPlan());
        assertEquals("Europe/Amsterdam", loaded.getDefaultTimezone());
        assertEquals("{\"js\":{\"id\":\"1\"}}", loaded.getProfileJson());
        assertEquals("hash-pass", loaded.getPassHash());
        assertEquals("hash-parent", loaded.getParentPasswordHash());
        assertEquals("hash-password", loaded.getPasswordHash());
        assertEquals("hash-settings", loaded.getSettingsPasswordHash());
        assertEquals("hash-page", loaded.getAccountPagePasswordHash());
        assertEquals("[\"mag250\",\"mag270\"]", loaded.getAllowedStbTypesJson());
        assertEquals("[\"mag250\"]", loaded.getAllowedStbTypesForLocalRecordingJson());
        assertEquals("mag270", loaded.getPreferredStbType());
    }
}
