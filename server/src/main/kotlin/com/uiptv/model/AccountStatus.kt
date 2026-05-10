package com.uiptv.model

import java.util.Locale

enum class AccountStatus {
    ACTIVE,
    SUSPENDED;

    fun toDisplay(): String = name.lowercase(Locale.ROOT)

    companion object {
        @JvmStatic
        fun fromValue(value: String?): AccountStatus? {
            if (value.isNullOrBlank()) {
                return null
            }
            return try {
                valueOf(value.trim().uppercase(Locale.ROOT))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
