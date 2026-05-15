package com.uiptv.mobile.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AndroidFilterSettingsTest {
    @Test
    fun defaultsLeaveFilteringEnabledAndThumbnailsDisabled() {
        val settings = AndroidFilterSettings()

        assertEquals("", settings.categoryFilters)
        assertEquals("", settings.channelFilters)
        assertFalse(settings.paused)
        assertFalse(settings.enableThumbnails)
    }
}
