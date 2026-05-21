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

        assertEquals(MobileAccountType.M3U8_URL, normalized.type)
        assertEquals("http://playlist.test/list.m3u8", normalized.url)
        assertEquals("", normalized.m3u8Path)
        assertEquals("GET", normalized.httpMethod)
        assertEquals("Europe/London", normalized.timezone)
    }

    @Test
    fun normalizedForSaveGivesM3uUrlPriorityOverLocalFile() {
        val account = MobileAccount(
            type = MobileAccountType.M3U8_LOCAL,
            url = " https://playlist.test/live.m3u ",
            m3u8Path = " content://local/playlist.m3u "
        )

        val normalized = account.normalizedForSave()

        assertEquals(MobileAccountType.M3U8_URL, normalized.type)
        assertEquals("https://playlist.test/live.m3u", normalized.url)
        assertEquals("", normalized.m3u8Path)
    }

    @Test
    fun normalizedForSaveInfersM3uLocalFromFileOnly() {
        val account = MobileAccount(
            type = MobileAccountType.M3U8_URL,
            m3u8Path = " content://local/playlist.m3u "
        )

        val normalized = account.normalizedForSave()

        assertEquals(MobileAccountType.M3U8_LOCAL, normalized.type)
        assertEquals("", normalized.url)
        assertEquals("content://local/playlist.m3u", normalized.m3u8Path)
    }

    @Test
    fun normalizedForSaveMovesLegacyM3uPathUrlIntoUrlField() {
        val account = MobileAccount(
            type = MobileAccountType.M3U8_URL,
            m3u8Path = " https://playlist.test/legacy.m3u8 "
        )

        val normalized = account.normalizedForSave()

        assertEquals(MobileAccountType.M3U8_URL, normalized.type)
        assertEquals("https://playlist.test/legacy.m3u8", normalized.url)
        assertEquals("", normalized.m3u8Path)
    }

    @Test
    fun normalizedForSaveSeedsXtremeCredentialJsonFromFields() {
        val account = MobileAccount(
            type = MobileAccountType.XTREME_API,
            username = "  alpha  ",
            password = "pass1"
        )

        val normalized = account.normalizedForSave()

        assertEquals("alpha", normalized.username)
        assertEquals("pass1", normalized.password)
        assertEquals(
            """[{"username":"alpha","password":"pass1","default":true}]""",
            normalized.xtremeCredentialsJson
        )
    }

    @Test
    fun normalizedForSaveSelectsXtremeCredentialFromJson() {
        val account = MobileAccount(
            type = MobileAccountType.XTREME_API,
            username = "beta",
            password = "pass2",
            xtremeCredentialsJson = """
                [
                  {"username":"alpha","password":"pass1","default":true},
                  {"username":"beta","password":"pass2"}
                ]
            """.trimIndent()
        )

        val normalized = account.normalizedForSave()

        assertEquals("beta", normalized.username)
        assertEquals("pass2", normalized.password)
        assertEquals(
            """[{"username":"alpha","password":"pass1"},{"username":"beta","password":"pass2","default":true}]""",
            normalized.xtremeCredentialsJson
        )
    }

    @Test
    fun xtremeCredentialSelectionUpdatesDefaultAndFields() {
        val account = MobileAccount(
            type = MobileAccountType.XTREME_API,
            username = "alpha",
            password = "pass1",
            xtremeCredentialsJson = """[{"username":"alpha","password":"pass1","default":true},{"username":"beta","password":"pass2"}]"""
        )

        val selected = account.withXtremeCredentialSelection(XtremeCredential("beta", "pass2"))

        assertEquals("beta", selected.username)
        assertEquals("pass2", selected.password)
        assertEquals(
            """[{"username":"alpha","password":"pass1"},{"username":"beta","password":"pass2","default":true}]""",
            selected.xtremeCredentialsJson
        )
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
