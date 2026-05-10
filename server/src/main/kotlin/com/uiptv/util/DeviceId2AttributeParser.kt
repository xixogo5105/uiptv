package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.text.Normalizer
import java.util.regex.Pattern

class DeviceId2AttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD)
        val matcher = DEVICE_ID2_PATTERN.matcher(normalizedLine)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.DEVICE_ID_2

    private companion object {
        val DEVICE_ID2_PATTERN: Pattern =
            Pattern.compile("(?:id\\s*2|device\\s*id\\s*2).*?([A-F0-9]{10,64})\\b", Pattern.CASE_INSENSITIVE)
    }
}
