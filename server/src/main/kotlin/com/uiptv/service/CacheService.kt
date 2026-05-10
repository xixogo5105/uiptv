package com.uiptv.service

import com.uiptv.api.LoggerCallback
import com.uiptv.model.Account
import java.io.IOException

interface CacheService {
    fun clearAllCache() {
        ConfigurationService.getInstance().clearAllCache()
    }

    fun clearCache(account: Account) {
        ConfigurationService.getInstance().clearCache(account)
    }

    @Throws(IOException::class)
    fun reloadCache(account: Account, logger: LoggerCallback)

    fun verifyMacAddress(account: Account?, macAddress: String?): Boolean

    fun getChannelCountForAccount(accountId: String): Int
}
