package com.uiptv.api

import com.uiptv.util.StalkerAttributeType
import java.text.Normalizer
import java.util.regex.Pattern

interface StalkerAttributeParser {
    fun parse(line: String): String?

    fun getAttributeType(): StalkerAttributeType

    fun lineContainsKeyword(line: String, keywords: List<String>): Boolean {
        val normalizedLine = Normalizer.normalize(line, Normalizer.Form.NFKC).lowercase()
        return keywords.any(normalizedLine::contains)
    }

    fun extractHexValueFromLine(line: String): String? {
        val normalized = Normalizer.normalize(line, Normalizer.Form.NFKC)
        val cleaned = normalized.replace(Regex("[^\\x20-\\x7E]"), " ").trim()
        val tokens = cleaned.split(Regex("\\s+"))
        for (token in tokens.asReversed()) {
            if (Pattern.compile("^[0-9A-Fa-f]{6,}$").matcher(token).matches()) {
                return token
            }
        }
        return null
    }
}
