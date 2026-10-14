package com.uiptv.mobile.shared.epg

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Persist validated XMLTV privately; source credentials never appear in filenames. */
internal class AndroidEpgDiskCache(context: Context) {
    private val directory = File(context.filesDir, "epg")
    private val ttl = 24 * 3600_000L
    internal fun file(source: String): File {
        check(directory.isDirectory || directory.mkdirs())
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(directory, "epg-$hash.xml")
    }
    fun expires(source: String): Long = file(source).lastModified() + ttl
    fun read(source: String, now: Long, stale: Boolean = false): MobileXmltvGuide? = runCatching {
        val file = file(source)
        if (!file.isFile || (!stale && now >= file.lastModified() + ttl)) null
        else file.inputStream().use { AndroidXmltvReader.read(AndroidEpgRepository.LimitedStream(it), now) }
    }.getOrNull()
    fun parseAndSave(source: String, xml: InputStream, now: Long): MobileXmltvGuide {
        val target = runCatching { file(source) }.getOrElse { return AndroidXmltvReader.read(xml, now) }
        val temporary = runCatching { File.createTempFile("epg-", ".part", directory) }
            .getOrElse { return AndroidXmltvReader.read(xml, now) }
        try {
            temporary.outputStream().buffered(256 * 1024).use { xml.copyTo(it) }
            val guide = temporary.inputStream().use { AndroidXmltvReader.read(it, now) }
            // Same-directory rename is atomic, keeping the previous valid feed until validation succeeds.
            temporary.renameTo(target)
            return guide
        } finally { temporary.delete() }
    }
    fun clear() { directory.listFiles()?.filter { it.name.startsWith("epg-") && it.extension == "xml" }?.forEach { it.delete() } }
}
