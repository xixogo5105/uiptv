package com.uiptv.mobile.shared.cache

import com.uiptv.mobile.shared.logging.AndroidAppLog
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Android adapter for the same allow-list as desktop. M3U options stay opaque and intact. */
object ChannelExtraJsonCompactor {
    private val malformedInputs = AtomicLong()

    fun compact(source: String?): String {
        if (source.isNullOrBlank()) return "{}"
        return try { compact(JSONObject(source)) }
        catch (_: RuntimeException) {
            if (malformedInputs.incrementAndGet() == 1L) {
                AndroidAppLog.w("UIPTV-Metadata", "Invalid channel metadata discarded during import")
            }
            "{}"
        }
    }

    fun compact(source: JSONObject): String = select(source, ChannelMetadataFields.channel).apply {
        source.optJSONObject("m3u")?.let { put("m3u", JSONObject(it.toString())) }
    }.toString()

    // These are provider episode rows, not serialized desktop Episode models.
    fun compactEpisode(source: JSONObject): String = select(
        source, ChannelMetadataFields.channel + ChannelMetadataFields.episode
    ).apply {
        source.optJSONObject("info")?.let {
            put("info", select(it, ChannelMetadataFields.channel + ChannelMetadataFields.episodeInfo + listOf("logo")))
        }
    }.toString()

    private fun select(source: JSONObject, fields: List<String>): JSONObject = JSONObject().apply {
        fields.forEach { field ->
            val value = source.opt(field)
            if (value is String || value is Number || value is Boolean) put(field, value)
        }
    }
}
