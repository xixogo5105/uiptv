package com.uiptv.model

enum class CategoryType(private val displayNameValue: String, private val identifierValue: String) {
    ALL("All", "all"),
    UNCATEGORIZED("Uncategorized", "uncategorized");

    fun displayName(): String = displayNameValue

    fun identifier(): String = identifierValue

    fun matches(value: String?): Boolean {
        if (value == null) {
            return false
        }
        val trimmed = value.trim()
        return displayNameValue.equals(trimmed, ignoreCase = true) || identifierValue.equals(trimmed, ignoreCase = true)
    }

    companion object {
        @JvmStatic
        fun fromString(value: String?): CategoryType? = entries.firstOrNull { it.matches(value) }

        @JvmStatic
        fun isAll(value: String?): Boolean = ALL.matches(value)

        @JvmStatic
        fun isUncategorized(value: String?): Boolean = UNCATEGORIZED.matches(value)
    }
}
