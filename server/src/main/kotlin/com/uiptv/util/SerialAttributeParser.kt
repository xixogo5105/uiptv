package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.text.Normalizer
import java.util.regex.Pattern

class SerialAttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD)
        val matcher = SERIAL_PATTERN.matcher(normalizedLine)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.SERIAL

    private companion object {
        val SERIAL_PATTERN: Pattern =
            Pattern.compile("(?:sn|serial).*?([A-F0-9]{10,32})\\b", Pattern.CASE_INSENSITIVE)
    }
}
