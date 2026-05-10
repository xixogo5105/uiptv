package com.uiptv.util

import com.uiptv.api.JsonCompliant
import com.uiptv.model.Account
import org.json.JSONObject
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import java.text.Normalizer

object StringUtils {
    const val SPACE = " "
    const val EMPTY = ""
    const val LF = "\n"
    const val CR = "\r"
    const val INDEX_NOT_FOUND = -1

    @JvmStatic
    fun isBlank(cs: CharSequence?): Boolean {
        val strLen = length(cs)
        if (strLen == 0) {
            return true
        }
        for (i in 0 until strLen) {
            if (!Character.isWhitespace(cs!![i])) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun isNotBlank(cs: CharSequence?): Boolean = !isBlank(cs)

    @JvmStatic
    fun length(cs: CharSequence?): Int = cs?.length ?: 0

    @JvmStatic
    fun nullSafeEncode(s: String?): String = encode(if (isNotBlank(s)) s else EMPTY, UTF_8)

    @JvmStatic
    fun safeGetString(jsonCategory: JSONObject?, key: String?): String? {
        if (jsonCategory == null || key == null) {
            return null
        }
        return try {
            jsonCategory.get(key).toString()
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun safeGetString(map: Map<*, *>?, key: String?): String? {
        if (map == null || key == null) {
            return null
        }
        return try {
            map[key].toString()
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun safeJson(`val`: String?): String {
        return try {
            if (!isBlank(`val`)) {
                val safeValue = `val` ?: return EMPTY
                safeValue.replace("\\p{C}".toRegex(), "").replace("\\", "\\\\").replace("\"", "\\\"")
            } else {
                EMPTY
            }
        } catch (_: Exception) {
            EMPTY
        }
    }

    @JvmStatic
    fun getXtremeStreamUrl(account: Account?, streamId: String?, extension: String?): String {
        if (account == null) {
            return ""
        }
        return when (account.action) {
            Account.AccountAction.vod -> account.m3u8Path + "movie/" + account.username + "/" + account.password + "/" + streamId.orEmpty() + "." + extension.orEmpty()
            Account.AccountAction.series -> account.m3u8Path + "series/" + account.username + "/" + account.password + "/" + streamId.orEmpty() + "." + extension.orEmpty()
            Account.AccountAction.itv -> account.m3u8Path + account.username + "/" + account.password + "/" + streamId.orEmpty()
        }
    }

    @JvmStatic
    fun <T : JsonCompliant> toJson(t: T?): String = t?.toJson() ?: "{}"

    @JvmStatic
    fun split(str: String?): Array<String> = if (isBlank(str)) emptyArray() else str!!.split(SPACE).toTypedArray()

    @JvmStatic
    fun safeUtf(input: String?): String {
        if (isBlank(input)) return ""
        val trimmed = input!!.trim()
        if (trimmed.isEmpty()) return ""
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        val bytes = normalized.toByteArray(UTF_8)
        return String(bytes, UTF_8)
    }
}
