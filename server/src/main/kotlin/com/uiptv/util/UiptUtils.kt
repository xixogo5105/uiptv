package com.uiptv.util

import com.uiptv.service.AccountService
import java.net.URI
import java.util.regex.Pattern

object UiptUtils {
    private const val MAC_ADDRESS_REGEX = "^(?:([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})|([0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}\\.[0-9a-fA-F]{4}))$"
    const val SPACER = " "
    private val ILLEGAL_URI_CHAR_REPLACEMENTS = mapOf(
        " " to "%20",
        "|" to "%7C",
        "<" to "%3C",
        ">" to "%3E",
        "\"" to "%22",
        "{" to "%7B",
        "}" to "%7D",
        "\\" to "%5C",
        "^" to "%5E",
        "`" to "%60"
    )

    @JvmStatic
    fun replaceAllNonPrintableChars(line: String?): String? {
        return try {
            if (StringUtils.isBlank(line)) StringUtils.EMPTY else line!!.replace("\\p{Cntrl}".toRegex(), SPACER).replace("[^\\p{Print}]".toRegex(), SPACER).replace("\\p{C}".toRegex(), SPACER)
        } catch (_: Exception) {
            line
        }
    }

    @JvmStatic
    fun isValidURL(urlString: String?): Boolean {
        return try {
            val uri = parseUrlLikeUri(urlString)
            !StringUtils.isBlank(uri.scheme) && !StringUtils.isBlank(uri.host)
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isValidMACAddress(line: String?): Boolean =
        !StringUtils.isBlank(line) && Pattern.compile(MAC_ADDRESS_REGEX).matcher(line).matches()

    @JvmStatic
    fun getUniqueNameFromUrl(urlString: String?): String {
        return try {
            val uri = parseUrlLikeUri(urlString)
            var index = 1
            var validName: String
            do {
                validName = uri.host + " (" + index++ + ")"
            } while (AccountService.getInstance().getByName(validName) != null)
            validName
        } catch (_: Exception) {
            urlString.orEmpty()
        }
    }

    @JvmStatic
    fun getNameFromUrl(urlString: String?): String {
        return try {
            parseUrlLikeUri(urlString).host
        } catch (_: Exception) {
            urlString.orEmpty()
        }
    }

    @JvmStatic
    fun isUrlValidXtremeLink(urlString: String?): Boolean {
        return try {
            val queryString = parseUrlLikeUri(urlString).query
            !StringUtils.isBlank(queryString) &&
                urlString!!.lowercase().contains("get.php?") &&
                queryString.lowercase().contains("username") &&
                queryString.lowercase().contains("password")
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun getPathFromUrl(urlString: String?): String {
        return try {
            urlString!!.split("get.php?")[0]
        } catch (_: Exception) {
            urlString.orEmpty()
        }
    }

    @JvmStatic
    fun getUserNameFromUrl(urlString: String?): String {
        return try {
            val queryString = parseUrlLikeUri(urlString).query
            getQueryMap(queryString)["username"].orEmpty()
        } catch (_: Exception) {
            urlString.orEmpty()
        }
    }

    @JvmStatic
    fun getPasswordNameFromUrl(urlString: String?): String {
        return try {
            val queryString = parseUrlLikeUri(urlString).query
            getQueryMap(queryString)["password"].orEmpty()
        } catch (_: Exception) {
            urlString.orEmpty()
        }
    }

    @JvmStatic
    fun parseUrlLikeUri(urlString: String?): URI {
        val normalized = urlString?.trim().orEmpty()
        require(normalized.isNotEmpty()) { "urlString cannot be blank" }
        return try {
            URI(normalized)
        } catch (_: Exception) {
            URI(sanitizeIllegalUriChars(normalized))
        }
    }

    private fun sanitizeIllegalUriChars(value: String): String {
        var sanitized = value
        ILLEGAL_URI_CHAR_REPLACEMENTS.forEach { (raw, escaped) ->
            sanitized = sanitized.replace(raw, escaped)
        }
        return sanitized
    }

    private fun getQueryMap(query: String): Map<String, String> {
        val params = query.split("&")
        val map = HashMap<String, String>()
        for (param in params) {
            val parts = param.split("=")
            val name = parts[0]
            if (parts.size > 1) {
                map[name] = parts[1]
            }
        }
        return map
    }

    @JvmStatic
    fun sanitizeStalkerText(text: String?): String {
        if (StringUtils.isBlank(text)) return StringUtils.EMPTY
        val sb = StringBuilder()
        text!!.codePoints().forEach { cp -> sb.appendCodePoint(mapSpecialCodePoint(cp)) }
        return sb.toString()
    }

    private fun mapSpecialCodePoint(c: Int): Int {
        if (c in 0x1D400..0x1D419) return 'A'.code + (c - 0x1D400)
        if (c in 0x1D41A..0x1D433) return 'a'.code + (c - 0x1D41A)
        if (c in 0x1D7CE..0x1D7D7) return '0'.code + (c - 0x1D7CE)
        if (c in 0x1F150..0x1F169) return 'A'.code + (c - 0x1F150)
        if (c in 0x1F170..0x1F189) return 'A'.code + (c - 0x1F170)

        return when (c) {
            0x029C -> 'H'.code
            0x1D0F -> 'O'.code
            0x1D1B -> 'T'.code
            0x1D07 -> 'E'.code
            0x1D00 -> 'A'.code
            0x029F -> 'L'.code
            0x1D18 -> 'P'.code
            0x0280 -> 'R'.code
            0x1D1C -> 'U'.code
            0x0274 -> 'N'.code
            0x1D0D -> 'M'.code
            0x026A -> 'I'.code
            0x1D04 -> 'C'.code
            0x1D20 -> 'V'.code
            0x1D05 -> 'D'.code
            0x2776 -> '1'.code
            0x2777 -> '2'.code
            0x278C -> '3'.code
            0x279F, 0x27A4, 0x27D0, 0x1F511, 0x1F194, 0x1F4DD, 0x1F538, 0x25CF,
            0x251C, 0x2500, 0x2502, 0x2570, 0x256D, 0x1F3C1, 0x23F0, 0x1F47D,
            0x1F510, 0x1FA62, 0x1F3AF, 0x1F4EE, 0x1F310, 0x26D1, 0x1F30D, 0x269C,
            0x2620, 0x2605, 0x2606, 0x1F5A5 -> ' '.code
            else -> c
        }
    }
}
