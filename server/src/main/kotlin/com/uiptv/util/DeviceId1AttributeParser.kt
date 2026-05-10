package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.text.Normalizer
import java.util.regex.Pattern

class DeviceId1AttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD)
        val matcher = DEVICE_ID1_PATTERN.matcher(normalizedLine)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.DEVICE_ID_1

    private companion object {
        val DEVICE_ID1_PATTERN: Pattern =
            Pattern.compile("(?:id\\s*1|device\\s*id\\s*1).*?([A-F0-9]{32,64})\\b", Pattern.CASE_INSENSITIVE)
    }
}
