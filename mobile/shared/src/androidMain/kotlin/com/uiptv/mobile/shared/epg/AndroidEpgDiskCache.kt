package com.uiptv.mobile.shared.epg

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Persist validated XMLTV privately; source credentials never appear in filenames. */
internal class AndroidEpgDiskCache(context: Context) {
    private val directory = File(context.filesDir, "epg")
    private val ttl = 24 * 3600_000L
    private val maxDiskBytes = 64L * 1024 * 1024
    internal fun file(source: String): File {
        check(directory.isDirectory || directory.mkdirs())
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(directory, "epg-$hash.xml")
    }
    fun expires(source: String, ttlMillis: Long = ttl): Long = file(source).lastModified() + ttlMillis
    fun read(source: String, now: Long, stale: Boolean = false, ttlMillis: Long = ttl): MobileXmltvGuide? {
        val file = file(source)
        if (!file.isFile || (!stale && now >= file.lastModified() + ttlMillis)) return null
        return try {
            file.inputStream().use { AndroidXmltvReader.read(AndroidEpgRepository.LimitedStream(it), now, source) }
        } catch (error: OutOfMemoryError) { null }
    }
    fun parseAndSave(source: String, xml: InputStream, now: Long): MobileXmltvGuide {
        val target = runCatching { file(source) }.getOrElse { return AndroidXmltvReader.read(xml, now, source) }
        val temporary = runCatching { File.createTempFile("epg-", ".part", directory) }
            .getOrElse { return AndroidXmltvReader.read(xml, now, source) }
        try {
            temporary.outputStream().buffered(256 * 1024).use { xml.copyTo(it) }
            val guide = try {
                temporary.inputStream().use { AndroidXmltvReader.read(it, now, source) }
            } catch (error: OutOfMemoryError) {
                temporary.delete(); throw error
            }
            temporary.renameTo(target)
            pruneDirectory()
            return guide
        } finally { temporary.delete() }
    }
    private fun pruneDirectory() {
        val files = directory.listFiles { it.extension == "xml" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxDiskBytes) return@forEach
            total -= if (file.delete()) file.length() else 0
        }
    }
    fun clear() { directory.listFiles()?.filter { it.name.startsWith("epg-") && it.extension == "xml" }?.forEach { it.delete() } }
}
