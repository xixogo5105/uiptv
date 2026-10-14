package com.uiptv.mobile.shared.epg

/** Cross-screen, cross-activity cache of per-channel EPG results.
 *
 * Composables lose their `remember` state when a LazyColumn item is scrolled out and back in, and the
 * embedded player is a separate activity that re-fetches on every open. Both problems disappear if the
 * result for a channel is cached once and reused: the bookmark row reads it instantly instead of
 * re-parsing the whole XMLTV feed, and the embedded player shows the guide without a network round trip.
 *
 * Reads and writes are synchronized so a composable can peek during composition and a coroutine can
 * store without a suspend context. Entries expire when the on-air programme ends, so a stale "now"
 * cannot be served forever.
 */
object MobileEpgCache {
    private val lock = Any()
    private val results = linkedMapOf<String, CachedResult>()
    private const val MAX_ENTRIES = 128

    data class CachedResult(val result: MobileEpgResult, val expiresAt: Long)

    fun key(accountId: Long, providerChannelId: String) = "$accountId|$providerChannelId"

    /** Non-blocking read for use inside a composable. */
    fun peek(accountId: Long, providerChannelId: String, now: Long): MobileEpgResult? =
        peek(key(accountId, providerChannelId), now)

    fun peek(key: String, now: Long): MobileEpgResult? = synchronized(lock) {
        results[key]?.takeIf { now < it.expiresAt }?.result
    }

    fun put(key: String, result: MobileEpgResult, now: Long, ttlMillis: Long) = synchronized(lock) {
        results[key] = CachedResult(result, now + ttlMillis)
        while (results.size > MAX_ENTRIES) results.remove(results.keys.first())
    }

    fun put(accountId: Long, providerChannelId: String, result: MobileEpgResult, now: Long) =
        put(key(accountId, providerChannelId), result, now, ttlFor(result, now))

    fun clear(accountId: Long, providerChannelId: String) = synchronized(lock) {
        results.remove(key(accountId, providerChannelId))
    }

    fun clear() = synchronized(lock) { results.clear() }

    fun ttlFor(result: MobileEpgResult, now: Long): Long {
        val boundary = result.programmes.filter { it.stop > now }.minOfOrNull { it.stop } ?: now + 3_600_000
        return (boundary - now + 2 * 60_000).coerceIn(60_000, 15 * 60_000)
    }
}