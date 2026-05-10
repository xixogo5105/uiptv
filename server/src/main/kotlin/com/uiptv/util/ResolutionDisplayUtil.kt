package com.uiptv.util

object ResolutionDisplayUtil {
    private val PROFILES = arrayOf(
        ResolutionProfile(1280, 720, "720p"),
        ResolutionProfile(1920, 1080, "1080p"),
        ResolutionProfile(2560, 1440, "2K"),
        ResolutionProfile(3840, 2160, "4K UHD"),
        ResolutionProfile(7680, 4320, "8K UHD")
    )

    @JvmStatic
    fun normalize(width: Int, height: Int): ResolutionDisplay {
        if (width <= 0 || height <= 0) {
            return ResolutionDisplay(width, height, "")
        }
        if (height < 701) {
            return ResolutionDisplay(width, height, "")
        }

        val matched = findClosestProfile(width, height) ?: return ResolutionDisplay(width, height, "")
        return ResolutionDisplay(matched.width, matched.height, matched.label)
    }

    private fun findClosestProfile(width: Int, height: Int): ResolutionProfile? {
        var bestMatch: ResolutionProfile? = null
        var bestScore = Int.MAX_VALUE
        for (profile in PROFILES) {
            if (!isWithinTolerance(width, profile.width) || !isWithinTolerance(height, profile.height)) {
                continue
            }
            val score = kotlin.math.abs(width - profile.width) + kotlin.math.abs(height - profile.height)
            if (score < bestScore) {
                bestScore = score
                bestMatch = profile
            }
        }
        return bestMatch
    }

    private fun isWithinTolerance(actual: Int, expected: Int): Boolean {
        val tolerance = maxOf(24, kotlin.math.round(expected * 0.03).toInt())
        return kotlin.math.abs(actual - expected) <= tolerance
    }

    data class ResolutionDisplay(val width: Int, val height: Int, val label: String) {
        fun dimensionsText(): String = "${width}x$height"
        fun shortText(): String = if (label.isBlank()) dimensionsText() else label
    }

    private data class ResolutionProfile(val width: Int, val height: Int, val label: String)
}
