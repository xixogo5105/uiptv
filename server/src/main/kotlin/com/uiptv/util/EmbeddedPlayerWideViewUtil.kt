package com.uiptv.util

import com.uiptv.service.ConfigurationService

object EmbeddedPlayerWideViewUtil {
    private var configurationServiceProvider: () -> ConfigurationService = { ConfigurationService }

    @JvmStatic
    fun configureDependencies(configurationService: ConfigurationService) {
        this.configurationServiceProvider = { configurationService }
    }

    @JvmStatic
    fun resetDependencies() {
        configurationServiceProvider = { ConfigurationService }
    }

    @JvmStatic
    fun isWideViewEnabled(): Boolean {
        val configuration: com.uiptv.model.Configuration? = configurationServiceProvider().read()
        if (configuration == null) {
            return false
        }
        return configuration.embeddedPlayer && configuration.wideView
    }
}
