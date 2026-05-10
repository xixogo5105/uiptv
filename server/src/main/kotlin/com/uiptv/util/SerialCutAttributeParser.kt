package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

class SerialCutAttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD)
        val labelMatcher = LABEL_PATTERN.matcher(normalizedLine)
        if (!labelMatcher.find()) {
            return null
        }
        val hexMatcher = HEX_PATTERN.matcher(normalizedLine)
        return if (hexMatcher.find()) hexMatcher.group(1).uppercase(Locale.ROOT) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.SERIAL_CUT

    private companion object {
        val LABEL_PATTERN: Pattern = Pattern.compile("(?i)(?:sn|serial)\\s*cut")
        val HEX_PATTERN: Pattern = Pattern.compile("([A-F0-9]{13,16})", Pattern.CASE_INSENSITIVE)
    }
}
