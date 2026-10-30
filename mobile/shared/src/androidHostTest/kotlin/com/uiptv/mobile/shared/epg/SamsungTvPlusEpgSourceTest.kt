package com.uiptv.mobile.shared.epg

import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SamsungTvPlusEpgSourceTest {
    @Test fun resolvesSingleRegionToCompressedProviderGuide() {
        assertEquals(
            "https://i.mjh.nz/SamsungTVPlus/in.xml.gz",
            SamsungTvPlusEpgSource.resolve(account("?regions=in"), "http://guide.local:8182/epg.xml?regions=in")
        )
    }

    @Test fun resolvesDefaultAndMultipleRegionsToCombinedGuide() {
        assertEquals(
            "https://i.mjh.nz/SamsungTVPlus/all.xml.gz",
            SamsungTvPlusEpgSource.resolve(account(), "http://guide.local:8182/epg.xml")
        )
        assertEquals(
            "https://i.mjh.nz/SamsungTVPlus/all.xml.gz",
            SamsungTvPlusEpgSource.resolve(account("?regions=in|gb"), "http://guide.local:8182/epg.xml?regions=in%7Cgb")
        )
    }

    @Test fun usesIndiaGuideForAnIndiaAccountWhenTheFeedHasNoRegionFilter() {
        assertEquals(
            "https://i.mjh.nz/SamsungTVPlus/in.xml.gz",
            SamsungTvPlusEpgSource.resolve(
                account(accountName = "docker samsung India"),
                "http://guide.local:8182/epg.xml"
            )
        )
    }

    @Test fun leavesUnrelatedFeedsAndAccountsUntouched() {
        assertNull(SamsungTvPlusEpgSource.resolve(account(), "https://guide.example/epg.xml"))
        assertNull(SamsungTvPlusEpgSource.resolve(
            MobileAccount(type = MobileAccountType.M3U8_LOCAL),
            "http://guide.local:8182/epg.xml"
        ))
    }

    private fun account(query: String = "", accountName: String = "") = MobileAccount(
        accountName = accountName,
        type = MobileAccountType.M3U8_URL,
        url = "http://guide.local:8182/playlist.m3u8$query"
    )
}
