package com.uiptv.mobile.shared.playback

/**
 * Formats decoded video dimensions using the same display tiers as the desktop VLC player.
 */
object MobileResolutionDisplay {
    private val profiles = listOf(
        ResolutionProfile(1280, 720, "720p"),
        ResolutionProfile(1920, 1080, "1080p"),
        ResolutionProfile(2560, 1440, "2K"),
        ResolutionProfile(3840, 2160, "4K UHD"),
        ResolutionProfile(7680, 4320, "8K UHD")
    )

    fun shortText(width: Int, height: Int): String {
        if (width <= 0 || height <= 0 || height < 701) {
            return "${width}x$height"
        }

        val matched = profiles
            .filter { profile ->
                withinTolerance(width, profile.width) && withinTolerance(height, profile.height)
            }
            .minByOrNull { profile ->
                kotlin.math.abs(width - profile.width) + kotlin.math.abs(height - profile.height)
            }
        return matched?.label ?: "${width}x$height"
    }

    private fun withinTolerance(actual: Int, expected: Int): Boolean {
        val tolerance = maxOf(24, kotlin.math.round(expected * 0.03).toInt())
        return kotlin.math.abs(actual - expected) <= tolerance
    }

    private data class ResolutionProfile(val width: Int, val height: Int, val label: String)
}
