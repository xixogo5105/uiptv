package com.uiptv.util

object M3uPlaylistUtils {
    @JvmStatic
    fun parseAttribute(line: String?, key: String?): String {
        if (StringUtils.isBlank(line) || StringUtils.isBlank(key)) {
            return StringUtils.EMPTY
        }
        val keyIndex = line!!.indexOf(key!!)
        if (keyIndex < 0) {
            return StringUtils.EMPTY
        }
        val equalsIndex = line.indexOf('=', keyIndex + key.length)
        if (equalsIndex < 0) {
            return StringUtils.EMPTY
        }
        var valueStart = equalsIndex + 1
        while (valueStart < line.length && Character.isWhitespace(line[valueStart])) {
            valueStart++
        }
        if (valueStart >= line.length) {
            return StringUtils.EMPTY
        }

        val quote = line[valueStart]
        if (quote == '"' || quote == '\'') {
            val valueEnd = line.indexOf(quote, valueStart + 1)
            return if (valueEnd < 0) {
                line.substring(valueStart + 1).trim()
            } else {
                line.substring(valueStart + 1, valueEnd).trim()
            }
        }

        var valueEnd = valueStart
        while (valueEnd < line.length) {
            val current = line[valueEnd]
            if (Character.isWhitespace(current) || current == ',') {
                break
            }
            valueEnd++
        }
        return line.substring(valueStart, valueEnd).trim()
    }

    @JvmStatic
    fun splitGroupTitles(rawGroupTitle: String?): List<String> {
        if (StringUtils.isBlank(rawGroupTitle)) {
            return emptyList()
        }
        val titles = linkedSetOf<String>()
        rawGroupTitle!!.split(";").forEach { candidate ->
            val trimmed = candidate.trim()
            if (trimmed.isNotEmpty()) {
                titles.add(trimmed)
            }
        }
        return titles.toList()
    }

    @JvmStatic
    fun escapeAttributeValue(value: String?): String {
        return value?.replace("\r", " ")?.replace("\n", " ")?.replace("\"", "'") ?: StringUtils.EMPTY
    }

    @JvmStatic
    fun sanitizeTitle(value: String?): String {
        return value?.replace("\r", " ")?.replace("\n", " ")?.trim() ?: StringUtils.EMPTY
    }
}
