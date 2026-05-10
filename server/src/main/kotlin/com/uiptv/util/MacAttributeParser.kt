package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.util.regex.Pattern

class MacAttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val matcher = MAC_PATTERN.matcher(line)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.MAC

    private companion object {
        val MAC_PATTERN: Pattern = Pattern.compile("(?i)(([0-9A-F]{2}[:-]){5}([0-9A-F]{2}))")
    }
}
