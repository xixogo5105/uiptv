package com.uiptv.mobile.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val DISK_CACHE_DIR_NAME = "thumbnails"
private const val DISK_CACHE_TTL_DAYS = 7L
private const val MAX_CONCURRENT_FETCHES = 4
private const val MAX_MEMORY_CACHE_BYTES = 40 * 1024 * 1024
private const val NEGATIVE_CACHE_MS_ERROR = 15_000L
private const val NEGATIVE_CACHE_MS_404 = 5 * 60_000L

class ThumbnailCache private constructor(private val context: Context) {

    private val diskDir: File = File(context.cacheDir, DISK_CACHE_DIR_NAME).also { it.mkdirs() }

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    private val inFlight = ConcurrentHashMap<String, Job>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    private val scope = CoroutineScope(SupervisorJob())

    fun get(url: String): Bitmap? {
        if (url.isBlank()) return null
        negativeCache[url]?.let { until ->
            if (System.currentTimeMillis() < until) return null
            negativeCache.remove(url, until)
        }
        return memoryCache.get(url)
    }

    fun getFromDisk(url: String): Bitmap? {
        if (url.isBlank()) return null
        val file = diskFile(url)
        if (!file.exists()) return null

        val ageMs = System.currentTimeMillis() - file.lastModified()
        val ttlMs = TimeUnit.DAYS.toMillis(DISK_CACHE_TTL_DAYS)
        if (ageMs > ttlMs) {
            file.delete()
            return null
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.also { memoryCache.put(url, it) }
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    fun put(url: String, bitmap: Bitmap) {
        if (url.isBlank()) return
        memoryCache.put(url, bitmap)
        scope.launch { persistToDisk(url, bitmap) }
    }

    fun negativeCache(url: String, statusCode: Int) {
        val ttl = if (statusCode == 404) NEGATIVE_CACHE_MS_404 else NEGATIVE_CACHE_MS_ERROR
        negativeCache[url] = System.currentTimeMillis() + ttl
    }

    fun fetchAsync(url: String): Job {
        if (url.isBlank()) return scope.launch { }

        return inFlight.getOrPut(url) {
            scope.launch {
                try {
                    fetchSemaphore.withPermit { fetchAndCache(url) }
                } catch (_: CancellationException) {
                    // Silently propagate cancellation
                } finally {
                    inFlight.remove(url)
                }
            }
        }
    }

    fun cancelInFlight(url: String) {
        inFlight[url]?.cancel()
        inFlight.remove(url)
    }

    fun clearMemory() = memoryCache.evictAll()
    fun clearDisk() = diskDir.listFiles()?.forEach { it.delete() }
    fun clearAll() {
        clearMemory()
        clearDisk()
        negativeCache.clear()
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    private suspend fun fetchAndCache(url: String) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 4_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                if (code >= 400) {
                    negativeCache(url, code)
                    return@withContext
                }

                conn.inputStream.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        put(url, bitmap)
                    } else {
                        negativeCache(url, code)
                    }
                }
            } catch (_: IOException) {
                negativeCache(url, statusCode = -1)
            }
        }
    }

    private suspend fun persistToDisk(url: String, bitmap: Bitmap) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = diskFile(url)
            try {
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } catch (_: IOException) {
                file.delete()
            }
        }
    }

    private fun diskFile(url: String): File = File(diskDir, sha1Hex(url) + ".img")

    private fun sha1Hex(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun close() {
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instances = ConcurrentHashMap<Context, ThumbnailCache>()

        fun getInstance(context: Context): ThumbnailCache =
            instances.getOrPut(context.applicationContext) { ThumbnailCache(context.applicationContext) }

        fun clearAll(context: Context) {
            instances.remove(context.applicationContext)?.also {
                it.clearAll()
                it.close()
            }
        }
    }
}
