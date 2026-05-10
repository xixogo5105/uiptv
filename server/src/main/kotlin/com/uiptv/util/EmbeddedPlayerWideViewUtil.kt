package com.uiptv.util

import com.uiptv.service.ConfigurationService

object EmbeddedPlayerWideViewUtil {
    @JvmStatic
    fun isWideViewEnabled(): Boolean {
        val configuration: com.uiptv.model.Configuration? = ConfigurationService.getInstance().read()
        if (configuration == null) {
            return false
        }
        return configuration.embeddedPlayer && configuration.wideView
    }
}
