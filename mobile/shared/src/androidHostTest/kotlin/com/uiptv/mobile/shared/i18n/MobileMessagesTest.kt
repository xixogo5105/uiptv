package com.uiptv.mobile.shared.i18n

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertNotEquals

class MobileMessagesTest {
    @Test
    fun embeddedPlaybackErrorsResolveToLocalizedText() {
        val originalLocale = Locale.getDefault()
        val keys = listOf(
            "autoCouldNotPlayVideoUnsupportedFormatOrNetworkError",
            "autoFailedToReconnectAfter5Attempts"
        )
        try {
            for (locale in listOf(Locale.US, Locale.UK, Locale.FRANCE, Locale.forLanguageTag("ar-SA"))) {
                Locale.setDefault(locale)
                keys.forEach { key ->
                    assertNotEquals(key, MobileMessages.text(key), "$key is missing for $locale")
                }
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
