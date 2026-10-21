package com.uiptv.mobile.android

/** Selection rules for mpv track IDs. A null subtitle selection means subtitles are off. */
internal object MpvTrackSelection {
    fun nextAudioTrack(trackIds: List<Int>, currentId: Int?): Int? =
        nextTrack(trackIds, currentId, wrapToStart = true)

    fun nextSubtitleTrack(trackIds: List<Int>, currentId: Int?): Int? {
        if (trackIds.isEmpty()) return null
        if (currentId == null) return trackIds.first()
        val currentIndex = trackIds.indexOf(currentId)
        return if (currentIndex < 0) trackIds.first() else trackIds.getOrNull(currentIndex + 1)
    }

    private fun nextTrack(trackIds: List<Int>, currentId: Int?, wrapToStart: Boolean): Int? {
        if (trackIds.isEmpty()) return null
        val currentIndex = currentId?.let(trackIds::indexOf) ?: -1
        val nextIndex = if (currentIndex < 0) 0 else currentIndex + 1
        return trackIds.getOrNull(nextIndex) ?: if (wrapToStart) trackIds.first() else null
    }
}
