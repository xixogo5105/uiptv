package com.uiptv.util

enum class AccountType(private val display: String) {
    STALKER_PORTAL("Stalker Portal"),
    M3U8_LOCAL("M3U8 Local"),
    M3U8_URL("M3U8 URL"),
    XTREME_API("Xtreme API"),
    RSS_FEED("RSS Feed");

    fun getDisplay(): String = display

    companion object {
        @JvmStatic
        fun getAccountTypeByDisplay(display: String?): AccountType {
            require(!display.isNullOrBlank()) { "Account type display cannot be blank" }
            val normalizedDisplay = display.trim()
            return entries.firstOrNull { it.display.equals(normalizedDisplay, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown account type display: $display")
        }
    }
}
