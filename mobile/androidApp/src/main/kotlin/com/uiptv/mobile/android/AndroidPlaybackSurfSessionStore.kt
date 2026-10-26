package com.uiptv.mobile.android

import com.uiptv.mobile.shared.playback.PlaybackSurfQueue
import com.uiptv.mobile.shared.playback.PlaybackTarget
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AndroidPlaybackSurfSession(
    val id: String,
    val targets: List<PlaybackTarget>,
    val startIndex: Int
)

object AndroidPlaybackSurfSessionStore {
    private val sessions = ConcurrentHashMap<String, AndroidPlaybackSurfSession>()

    fun create(queue: PlaybackSurfQueue): AndroidPlaybackSurfSession? {
        if (queue.targets.size < 2 || queue.currentIndex !in queue.targets.indices) return null
        val session = AndroidPlaybackSurfSession(UUID.randomUUID().toString(), queue.targets, queue.currentIndex)
        sessions[session.id] = session
        return session
    }

    fun get(id: String): AndroidPlaybackSurfSession? = sessions[id]

    fun remove(id: String) {
        sessions.remove(id)
    }
}
