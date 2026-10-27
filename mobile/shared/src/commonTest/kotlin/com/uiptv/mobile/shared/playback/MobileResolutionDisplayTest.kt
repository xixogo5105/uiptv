package com.uiptv.mobile.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class MobileResolutionDisplayTest {
    @Test
    fun knownResolutionsUseDesktopLabels() {
        assertEquals("720p", MobileResolutionDisplay.shortText(1288, 718))
        assertEquals("1080p", MobileResolutionDisplay.shortText(1920, 1080))
        assertEquals("2K", MobileResolutionDisplay.shortText(2550, 1440))
        assertEquals("4K UHD", MobileResolutionDisplay.shortText(3840, 2160))
        assertEquals("8K UHD", MobileResolutionDisplay.shortText(7680, 4320))
    }

    @Test
    fun unknownAndSmallResolutionsKeepTheirDimensions() {
        assertEquals("640x480", MobileResolutionDisplay.shortText(640, 480))
        assertEquals("1111x777", MobileResolutionDisplay.shortText(1111, 777))
        assertEquals("0x-1", MobileResolutionDisplay.shortText(0, -1))
    }
}
