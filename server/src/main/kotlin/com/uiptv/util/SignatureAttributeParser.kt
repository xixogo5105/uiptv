package com.uiptv.util

import com.uiptv.api.StalkerAttributeParser
import java.text.Normalizer
import java.util.regex.Pattern

class SignatureAttributeParser : StalkerAttributeParser {
    override fun parse(line: String): String? {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKD)
        val matcher = SIGNATURE_PATTERN.matcher(normalizedLine)
        return if (matcher.find()) matcher.group(1) else null
    }

    override fun getAttributeType(): StalkerAttributeType = StalkerAttributeType.SIGNATURE

    private companion object {
        val SIGNATURE_PATTERN: Pattern =
            Pattern.compile("(?:signature|signature1|sig).*?([A-F0-9]{64})\\b", Pattern.CASE_INSENSITIVE)
    }
}
