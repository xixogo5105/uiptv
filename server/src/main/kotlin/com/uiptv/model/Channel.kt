package com.uiptv.model

import com.uiptv.shared.BaseJson
import java.util.stream.Collectors

import com.uiptv.util.StringUtils.safeGetString
import com.uiptv.util.json.optBoolean
import com.uiptv.util.json.optInt
import com.uiptv.util.json.parseJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private const val KEY_SEASON = "season"

data class Channel @JvmOverloads constructor(
    var dbId: String? = null,
    var channelId: String? = null,
    var categoryId: String? = null,
    var name: String? = null,
    var number: String? = null,
    var cmd: String? = null,
    @Suppress("PropertyName")
    var cmd_1: String? = null,
    @Suppress("PropertyName")
    var cmd_2: String? = null,
    @Suppress("PropertyName")
    var cmd_3: String? = null,
    var logo: String? = null,
    var description: String? = null,
    var season: String? = null,
    var episodeNum: String? = null,
    var releaseDate: String? = null,
    var rating: String? = null,
    var duration: String? = null,
    var extraJson: String? = null,
    var censored: Int = 0,
    var status: Int = 0,
    var hd: Int = 0,
    @get:JvmName("isWatched")
    var watched: Boolean = false,
    var drmType: String? = null,
    var drmLicenseUrl: String? = null,
    var clearKeysJson: String? = null,
    var inputstreamaddon: String? = null,
    var manifestType: String? = null
) : BaseJson() {
    @Suppress("java:S107")
    constructor(
        channelId: String?,
        name: String?,
        number: String?,
        cmd: String?,
        cmd1: String?,
        cmd2: String?,
        cmd3: String?,
        logo: String?,
        censored: Int,
        status: Int,
        hd: Int,
        drmType: String?,
        drmLicenseUrl: String?,
        clearKeys: Map<String, String>?,
        inputstreamaddon: String?,
        manifestType: String?
    ) : this(
        channelId = channelId,
        name = name,
        number = number,
        cmd = cmd,
        cmd_1 = cmd1,
        cmd_2 = cmd2,
        cmd_3 = cmd3,
        logo = logo,
        censored = censored,
        status = status,
        hd = hd,
        drmType = drmType,
        drmLicenseUrl = drmLicenseUrl,
        inputstreamaddon = inputstreamaddon,
        manifestType = manifestType
    ) {
        setClearKeys(clearKeys)
    }

    fun setClearKeys(clearKeys: Map<String, String>?) {
        clearKeysJson = if (clearKeys.isNullOrEmpty()) {
            null
        } else {
            clearKeys.entries.stream()
                .map { entry -> "\"${entry.key}\":\"${entry.value}\"" }
                .collect(Collectors.joining(",", "{", "}"))
        }
    }

    fun getCompareSeason(): Int {
        return try {
            name!!.split("-")[0]
                .lowercase()
                .replace(" ", "")
                .replace(KEY_SEASON, "")
                .replace("episode", "")
                .replace("-", "")
                .toInt()
        } catch (_: Exception) {
            0
        }
    }

    fun getCompareEpisode(): Int {
        return try {
            name!!.split("-")[1]
                .lowercase()
                .replace(" ", "")
                .replace(KEY_SEASON, "")
                .replace("episode", "")
                .replace("-", "")
                .toInt()
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        @JvmStatic
        fun fromJson(json: String): Channel? {
            return try {
                val jsonObj = parseJsonObject(json) ?: return null
                val channel = Channel(
                    dbId = safeGetString(jsonObj, "dbId"),
                    channelId = safeGetString(jsonObj, "channelId"),
                    categoryId = safeGetString(jsonObj, "categoryId"),
                    name = safeGetString(jsonObj, "name"),
                    number = safeGetString(jsonObj, "number"),
                    cmd = safeGetString(jsonObj, "cmd"),
                    cmd_1 = safeGetString(jsonObj, "cmd_1"),
                    cmd_2 = safeGetString(jsonObj, "cmd_2"),
                    cmd_3 = safeGetString(jsonObj, "cmd_3"),
                    logo = safeGetString(jsonObj, "logo"),
                    description = safeGetString(jsonObj, "description"),
                    season = safeGetString(jsonObj, KEY_SEASON),
                    episodeNum = safeGetString(jsonObj, "episodeNum"),
                    releaseDate = safeGetString(jsonObj, "releaseDate"),
                    rating = safeGetString(jsonObj, "rating"),
                    duration = safeGetString(jsonObj, "duration"),
                    extraJson = safeGetString(jsonObj, "extraJson"),
                    censored = jsonObj.optInt("censored"),
                    status = jsonObj.optInt("status"),
                    hd = jsonObj.optInt("hd"),
                    drmType = safeGetString(jsonObj, "drmType"),
                    drmLicenseUrl = safeGetString(jsonObj, "drmLicenseUrl"),
                    clearKeysJson = safeGetString(jsonObj, "clearKeysJson"),
                    inputstreamaddon = safeGetString(jsonObj, "inputstreamaddon"),
                    manifestType = safeGetString(jsonObj, "manifestType")
                )
                val watched = jsonObj["watched"]
                channel.watched = when (watched) {
                    is JsonPrimitive -> watched.booleanOrNull ?: safeGetString(jsonObj, "watched").let {
                        "1".equals(it) || "true".equals(it, ignoreCase = true)
                    }
                    else -> {
                        val watchedStr = safeGetString(jsonObj, "watched")
                        "1".equals(watchedStr) || "true".equals(watchedStr, ignoreCase = true)
                    }
                }
                channel
            } catch (_: Exception) {
                null
            }
        }
    }
}
