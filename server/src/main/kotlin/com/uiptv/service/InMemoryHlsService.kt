package com.uiptv.service

import com.uiptv.util.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object InMemoryHlsService {
    private val storage = ConcurrentHashMap<String, ByteArray>()
    private val timestamps = ConcurrentHashMap<String, Long>()
    private val pendingDeletes = ConcurrentHashMap<String, ScheduledFuture<*>>()
    @Volatile
    private var lastTsPutAt = 0L
    @Volatile
    private var lastClientAccessAt = 0L

    private const val DEFAULT_TS_DELETE_GRACE_MILLIS = 20_000L
    private val MAX_SEGMENTS: Int = Integer.getInteger("uiptv.hls.max.segments", 180)
    private val deleteScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "uiptv-hls-delete-grace").apply { isDaemon = true }
    }

    fun put(name: String, data: ByteArray) {
        if (name.endsWith(".ts")) {
            cancelPendingDelete(name)
            cleanupOldSegments()
            lastTsPutAt = System.currentTimeMillis()
        }
        storage[name] = data
        timestamps[name] = System.currentTimeMillis()
    }
    fun get(name: String): ByteArray? {
        if (storage.containsKey(name)) {
            timestamps[name] = System.currentTimeMillis()
        }
        return storage[name]
    }
    fun markClientAccess() {
        lastClientAccessAt = System.currentTimeMillis()
    }
    fun remove(name: String) {
        if (!name.endsWith(".ts")) {
            removeNow(name)
            return
        }

        pendingDeletes.remove(name)?.cancel(false)
        val scheduled = deleteScheduler.schedule({
            removeNow(name)
            pendingDeletes.remove(name)
        }, tsDeleteGraceMillis(), TimeUnit.MILLISECONDS)
        pendingDeletes[name] = scheduled
    }
    fun exists(name: String): Boolean = storage.containsKey(name)
    fun clear() {
        pendingDeletes.values.forEach { it.cancel(false) }
        pendingDeletes.clear()
        storage.clear()
        timestamps.clear()
        lastTsPutAt = 0L
        lastClientAccessAt = 0L
    }
    fun getLastTsPutAt(): Long = lastTsPutAt
    fun getLastClientAccessAt(): Long = lastClientAccessAt
    fun getTsSegmentCount(): Long = storage.keys.count { it.endsWith(".ts") }.toLong()

    private fun tsDeleteGraceMillis(): Long =
        java.lang.Long.getLong("uiptv.hls.ts.delete.grace.millis", DEFAULT_TS_DELETE_GRACE_MILLIS)

    private fun cleanupOldSegments() {
        var tsCount = storage.keys.count { it.endsWith(".ts") }
        while (tsCount >= MAX_SEGMENTS) {
            var oldestKey: String? = null
            var oldestTime = Long.MAX_VALUE
            timestamps.forEach { (key, value) ->
                if (key.endsWith(".ts") && value < oldestTime) {
                    oldestTime = value
                    oldestKey = key
                }
            }
            if (oldestKey != null) {
                removeNow(oldestKey)
                AppLog.addWarningLog(InMemoryHlsService::class.java, "InMemoryHlsService: Evicted old segment $oldestKey to free memory.")
                tsCount--
            } else {
                break
            }
        }
    }

    private fun cancelPendingDelete(name: String) {
        pendingDeletes.remove(name)?.cancel(false)
    }

    private fun removeNow(name: String) {
        cancelPendingDelete(name)
        storage.remove(name)
        timestamps.remove(name)
    }
}
