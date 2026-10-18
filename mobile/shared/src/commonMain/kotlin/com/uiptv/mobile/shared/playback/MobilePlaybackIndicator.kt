package com.uiptv.mobile.shared.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uiptv.mobile.shared.browse.BrowseMode

data class MobilePlaybackIdentity(
    val accountId: Long,
    val mode: BrowseMode,
    val channelId: String
)

/** Process-local identity of the target currently playing in an in-app player. */
object MobilePlaybackIndicator {
    var current: MobilePlaybackIdentity? by mutableStateOf(null)
        private set

    fun set(target: PlaybackTarget?) {
        current = target?.let {
            MobilePlaybackIdentity(it.accountId, it.mode, it.channelId.trim())
        }
    }

    fun clear(target: PlaybackTarget? = null) {
        val expected = target?.let {
            MobilePlaybackIdentity(it.accountId, it.mode, it.channelId.trim())
        }
        if (expected == null || current == expected) current = null
    }

    fun isActive(accountId: Long, mode: BrowseMode, channelId: String): Boolean =
        current == MobilePlaybackIdentity(accountId, mode, channelId.trim())
}
