package com.uiptv.mobile.android

import com.uiptv.mobile.shared.playback.PlaybackTarget
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AndroidBingeWatchSession(
    val id: String,
    val seriesTitle: String,
    val targets: List<PlaybackTarget>,
    val startIndex: Int
)

object AndroidBingeWatchSessionStore {
    private val sessions = ConcurrentHashMap<String, AndroidBingeWatchSession>()

    fun create(seriesTitle: String, targets: List<PlaybackTarget>, startIndex: Int = 0): AndroidBingeWatchSession {
        val session = AndroidBingeWatchSession(
            id = UUID.randomUUID().toString(),
            seriesTitle = seriesTitle,
            targets = targets,
            startIndex = startIndex.coerceIn(0, (targets.size - 1).coerceAtLeast(0))
        )
        sessions[session.id] = session
        return session
    }

    fun get(id: String): AndroidBingeWatchSession? =
        sessions[id]

    fun remove(id: String) {
        sessions.remove(id)
    }

    fun clear() {
        sessions.clear()
    }
}
