package com.uiptv.util

import com.uiptv.service.ConfigurationService

object EmbeddedPlayerWideViewUtil {
    @JvmStatic
    fun isWideViewEnabled(): Boolean {
        val configuration = ConfigurationService.getInstance().read()
        return configuration != null && configuration.embeddedPlayer && configuration.wideView
    }
}
