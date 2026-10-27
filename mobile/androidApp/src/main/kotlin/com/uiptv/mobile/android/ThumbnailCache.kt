package com.uiptv.mobile.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
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

    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    private val scope = CoroutineScope(SupervisorJob())
    private val inFlight = KeyedJobs(scope)

    private fun isNegativelyCached(url: String): Boolean {
        val until = negativeCache[url] ?: return false
        if (System.currentTimeMillis() < until) return true
        negativeCache.remove(url, until)
        return false
    }

    fun get(url: String): Bitmap? {
        if (url.isBlank()) return null
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
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = ThumbnailDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeFile(file.absolutePath, options)?.also { memoryCache.put(url, it) }
                ?: run { file.delete(); null }
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    private suspend fun put(url: String, bitmap: Bitmap) {
        currentCoroutineContext().ensureActive()
        memoryCache.put(url, bitmap)
        persistToDisk(url, bitmap)
    }

    fun negativeCache(url: String, statusCode: Int) {
        val ttl = if (statusCode == 404) NEGATIVE_CACHE_MS_404 else NEGATIVE_CACHE_MS_ERROR
        negativeCache[url] = System.currentTimeMillis() + ttl
    }

    fun fetchAsync(url: String): Job {
        if (url.isBlank()) return scope.launch { }

        return inFlight.start(url) {
            if (get(url) != null || isNegativelyCached(url)) return@start
            fetchSemaphore.withPermit {
                if (get(url) == null && !isNegativelyCached(url)) fetchAndCache(url)
            }
        }
    }

    fun cancelInFlight(url: String) { inFlight.cancel(url) }

    fun clearMemory() = memoryCache.evictAll()
    fun clearDisk() = diskDir.listFiles()?.forEach { it.delete() }
    fun clearAll() {
        inFlight.cancelAll()
        clearMemory()
        clearDisk()
        negativeCache.clear()
    }

    private suspend fun fetchAndCache(url: String) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as? HttpURLConnection
                    ?: throw IOException("Unsupported thumbnail URL")
                conn.connectTimeout = 4_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = true

                val code = conn.responseCode
                if (code >= 400) {
                    negativeCache(url, code)
                    return@withContext
                }

                conn.inputStream.use { stream ->
                    val bytes = ThumbnailDecodePolicy.readBytes(stream)
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = ThumbnailDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    currentCoroutineContext().ensureActive()
                    if (bitmap != null) {
                        put(url, bitmap)
                    } else {
                        negativeCache(url, code)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                negativeCache(url, statusCode = -1)
            } finally {
                conn?.disconnect()
            }
        }
    }

    private suspend fun persistToDisk(url: String, bitmap: Bitmap) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            var staged: File? = null
            try {
                currentCoroutineContext().ensureActive()
                staged = File.createTempFile("thumbnail-", ".tmp", diskDir)
                staged.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Unable to encode thumbnail")
                }
                currentCoroutineContext().ensureActive()
                if (!staged.renameTo(diskFile(url))) throw IOException("Unable to store thumbnail")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) {
                // A usable memory image does not depend on disk cache availability.
            } finally {
                staged?.delete()
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
            instances.computeIfAbsent(context.applicationContext) { ThumbnailCache(it) }

        fun clearAll(context: Context) {
            // Existing compositions retain this instance. Keep its scope usable.
            instances[context.applicationContext]?.clearAll()
        }
    }
}
