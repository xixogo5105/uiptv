package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.util.regex.Pattern

class UrlAttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val matcher = URL_PATTERN.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.URL

    private companion object {
        val URL_PATTERN: Pattern = Pattern.compile("(https?://\\S+)")
    }
}
