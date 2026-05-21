package com.uiptv.mobile.shared.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileAccountTest {
    @Test
    fun normalizedForSaveTrimsStalkerAccountAndNormalizesLists() {
        val account = MobileAccount(
            accountName = "  Portal  ",
            type = MobileAccountType.STALKER_PORTAL,
            url = "http://example.test",
            username = "  user  ",
            macAddress = " 00: 11 :22 ",
            macAddressList = " aa:bb ; cc:dd, aa:bb,  ",
            httpMethod = " post ",
            timezone = "  UTC  "
        )

        val normalized = account.normalizedForSave()

        assertEquals("Portal", normalized.accountName)
        assertEquals("http://example.test/", normalized.url)
        assertEquals("user", normalized.username)
        assertEquals("00:11:22", normalized.macAddress)
        assertEquals("00:11:22,aa:bb,cc:dd", normalized.macAddressList)
        assertEquals("POST", normalized.httpMethod)
        assertEquals("UTC", normalized.timezone)
    }

    @Test
    fun normalizedForSaveKeepsNonStalkerUrlAndSuppliesDefaults() {
        val account = MobileAccount(
            type = MobileAccountType.M3U8_URL,
            url = "  http://playlist.test/list.m3u8  ",
            httpMethod = "",
            timezone = ""
        )

        val normalized = account.normalizedForSave()

        assertEquals("http://playlist.test/list.m3u8", normalized.url)
        assertEquals("GET", normalized.httpMethod)
        assertEquals("Europe/London", normalized.timezone)
    }

    @Test
    fun canRefreshCacheFollowsAccountTypeSupport() {
        assertTrue(MobileAccount(type = MobileAccountType.STALKER_PORTAL).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.XTREME_API).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.M3U8_URL).canRefreshCache)
        assertTrue(MobileAccount(type = MobileAccountType.M3U8_LOCAL).canRefreshCache)
    }

    @Test
    fun accountCacheSummaryTotalsAllItemTypes() {
        val summary = AccountCacheSummary(
            liveCategories = 1,
            liveChannels = 2,
            vodCategories = 3,
            vodChannels = 4,
            seriesCategories = 5,
            seriesChannels = 6,
            seriesEpisodes = 7
        )

        assertEquals(28, summary.totalItems)
    }
}
