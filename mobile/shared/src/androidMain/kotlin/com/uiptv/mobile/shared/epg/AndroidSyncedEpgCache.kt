package com.uiptv.mobile.shared.epg

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.InflaterInputStream
import org.json.JSONObject

/** Reads the desktop cache in a cloned snapshot. Never matches by a local channel row id. */
internal class AndroidSyncedEpgCache(private val db: SQLiteDatabase) {
    data class Entry(val result: MobileEpgResult, val expiresAt: Long)

    fun read(accountId: Long, providerId: String, now: Long): Entry? = runCatching {
        val signature = signature(accountId, providerId) ?: return null
        val sources = linkedMapOf<String, MutableList<MobileEpgProgramme>>()
        var expires = Long.MAX_VALUE
        var warning = false
        db.rawQuery("""
            SELECT m.source_id,p.title,p.description,p.starts_at,p.stops_at,s.retry_at,s.warning
            FROM epg_channel_mapping m
            JOIN epg_programme p ON p.source_id=m.source_id AND p.guide_channel_id=m.guide_channel_id
            JOIN epg_source s ON s.source_id=m.source_id
            WHERE m.account_id=? AND m.provider_channel_id=? AND m.signature=?
              AND p.stops_at>? AND p.starts_at<?
            ORDER BY m.priority,p.starts_at LIMIT 8000
        """.trimIndent(), arrayOf(accountId.toString(), providerId, signature,
            (now - 6 * 3_600_000L).toString(), (now + 7 * 86_400_000L).toString())).use { rows ->
            while (rows.moveToNext()) {
                expires = minOf(expires, rows.getLong(5))
                warning = warning || !rows.getString(6).isNullOrBlank()
                sources.getOrPut(rows.getString(0)) { mutableListOf() }.add(MobileEpgProgramme(
                    rows.getString(1), expand(rows.getBlob(2)), rows.getLong(3), rows.getLong(4)))
            }
        }
        val programmes = MobileEpgSchedules.preferSources(sources.values.toList()).take(2000)
        if (programmes.none { it.stop > now }) return null
        Entry(MobileEpgResult(if (warning || now >= expires) MobileEpgStatus.STALE else MobileEpgStatus.AVAILABLE,
            programmes), expires)
    }.getOrNull() // Older snapshots and invalid disposable cache rows fall back to the normal loader.

    fun icon(accountId: Long, providerId: String): String = runCatching {
        val signature = signature(accountId, providerId) ?: return ""
        db.rawQuery("""
            SELECT i.icon_url FROM epg_channel_mapping m JOIN epg_channel_icon i
              ON i.source_id=m.source_id AND i.guide_channel_id=m.guide_channel_id
            WHERE m.account_id=? AND m.provider_channel_id=? AND m.signature=?
            ORDER BY m.priority LIMIT 1
        """.trimIndent(), arrayOf(accountId.toString(), providerId, signature)).use {
            if (it.moveToFirst()) it.getString(0).orEmpty() else ""
        }
    }.getOrDefault("")

    /** Must match EpgCacheApplicationService.identity, including the raw catalogue metadata. */
    private fun signature(accountId: Long, providerId: String): String? {
        val fields = db.rawQuery("""
            SELECT a.type,a.url,a.serverPortalUrl,a.username,a.password,a.macAddress,a.epg,a.m3u8Path,ch.extraJson,ch.name
            FROM Account a JOIN Category cat ON cat.accountId=a.id JOIN Channel ch ON ch.categoryId=cat.id
            WHERE a.id=? AND ch.channelId=? ORDER BY ch.id LIMIT 1
        """.trimIndent(), arrayOf(accountId.toString(), providerId)).use { row ->
            if (!row.moveToFirst()) return null
            List(10) { row.getString(it).orEmpty() }
        }
        val config = db.rawQuery("SELECT globalEpgSource,epgMappingsJson FROM Configuration LIMIT 1", null).use { row ->
            if (row.moveToFirst()) row.getString(0).orEmpty() to row.getString(1).orEmpty() else "" to ""
        }
        val override = runCatching { JSONObject(config.second).optJSONObject(accountId.toString())?.optString(providerId) }
            .getOrNull().orEmpty().trim()
        val native = hash("native:utc-v2:$accountId|${fields[0].ifBlank { "STALKER_PORTAL" }}|" +
            "${fields[1].ifBlank { fields[2] }}|${fields[3]}|${fields[4]}|${fields[5]}|$providerId")
        return hash("$native|${fields[6]}|${fields[7]}|${config.first}|$override|${fields[8]}|${fields[9]}")
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun expand(bytes: ByteArray): String = InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 8192) { "EPG description exceeds cache limit" }
            output.write(buffer, 0, count)
        }
        output.toString("UTF-8")
    }
}
