package com.uiptv.mobile.shared.ui

import com.uiptv.mobile.shared.accounts.AccountCacheSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSummaryPresentationTest {
    @Test
    fun emptyCatalogueDoesNotRenderMisleadingZeroCounts() {
        assertEquals("No catalogue cached", AccountCacheSummary().accountRowLabel())
    }

    @Test
    fun cachedCatalogueShowsCountsOnlyForPopulatedContentTypes() {
        val summary = AccountCacheSummary(
            liveCategories = 2,
            liveChannels = 18,
            vodCategories = 1,
            vodChannels = 7,
            seriesCategories = 3,
            seriesChannels = 4,
            seriesEpisodes = 22
        )

        assertEquals(
            "2 live categories · 18 channels  •  1 movie category · 7 movies  •  3 series categories · 4 series · 22 episodes",
            summary.accountRowLabel()
        )
    }
}
