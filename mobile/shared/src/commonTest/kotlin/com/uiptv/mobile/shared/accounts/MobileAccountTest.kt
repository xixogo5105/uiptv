package com.uiptv.mobile.shared.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileAccountTest {
    @Test
    fun normalizesStalkerPortalUrlAndMacListForSave() {
        val account = MobileAccount(
            accountName = "  Portal  ",
            type = MobileAccountType.STALKER_PORTAL,
            url = "http://example.test",
            macAddress = "00: 11:22",
            macAddressList = "00: 11:22, AA:BB:CC, AA:BB:CC",
            httpMethod = "",
            timezone = ""
        ).normalizedForSave()

        assertEquals("Portal", account.accountName)
        assertEquals("http://example.test/", account.url)
        assertEquals("00:11:22", account.macAddress)
        assertEquals("00:11:22,AA:BB:CC", account.macAddressList)
        assertEquals("GET", account.httpMethod)
        assertEquals("Europe/London", account.timezone)
    }

    @Test
    fun cacheSupportMatchesPhaseTwoAccountTypes() {
        assertTrue(MobileAccount(type = MobileAccountType.STALKER_PORTAL).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.XTREME_API).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.M3U8_URL).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.M3U8_LOCAL).canRefreshCache)
        assertFalse(MobileAccount(type = MobileAccountType.RSS_FEED).canRefreshCache)
    }
}
