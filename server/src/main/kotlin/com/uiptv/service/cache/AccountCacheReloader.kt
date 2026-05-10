package com.uiptv.service.cache

import com.uiptv.api.LoggerCallback
import com.uiptv.model.Account
import java.io.IOException

interface AccountCacheReloader {
    @Throws(IOException::class)
    fun reloadCache(account: Account, logger: LoggerCallback?)
}
