package com.uiptv.service

import com.uiptv.api.LoggerCallback
import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.service.cache.AccountCacheReloaderFactory
import com.uiptv.util.AccountType
import com.uiptv.util.FetchAPI
import java.util.Date
import java.util.HashMap

class CacheServiceImpl(
    private val handshakeServiceProvider: () -> HandshakeService,
    private val categoryServiceProvider: () -> CategoryService,
    private val configurationServiceProvider: () -> ConfigurationService,
    private val channelServiceProvider: () -> ChannelService,
    private val fetchProvider: (Map<String, String>, Account) -> String
) : CacheService {
    constructor() : this(
        handshakeServiceProvider = { HandshakeService.INSTANCE },
        categoryServiceProvider = { CategoryService.INSTANCE },
        configurationServiceProvider = { ConfigurationService },
        channelServiceProvider = { ChannelService.INSTANCE },
        fetchProvider = FetchAPI::fetch
    )

    constructor(
        handshakeServiceProvider: () -> HandshakeService,
        categoryServiceProvider: () -> CategoryService
    ) : this(
        handshakeServiceProvider = handshakeServiceProvider,
        categoryServiceProvider = categoryServiceProvider,
        configurationServiceProvider = { ConfigurationService },
        channelServiceProvider = { ChannelService.INSTANCE },
        fetchProvider = FetchAPI::fetch
    )

    private val reloaderFactory by lazy(LazyThreadSafetyMode.NONE) {
        AccountCacheReloaderFactory(
            categoryServiceProvider = categoryServiceProvider,
            configurationServiceProvider = configurationServiceProvider,
            handshakeServiceProvider = handshakeServiceProvider,
            channelServiceProvider = channelServiceProvider,
            fetchProvider = fetchProvider
        )
    }

    override fun reloadCache(account: Account, logger: LoggerCallback) {
        reloaderFactory.get(account.type).reloadCache(account, logger)
    }

    override fun verifyMacAddress(account: Account?, macAddress: String?): Boolean {
        if (account == null || macAddress.isNullOrBlank() || account.type != AccountType.STALKER_PORTAL) {
            return false
        }
        val originalMac = account.macAddress
        return try {
            account.macAddress = macAddress
            handshakeServiceProvider.invoke().connect(account)
            if (account.isNotConnected()) {
                false
            } else {
                val jsonCategories = fetchProvider.invoke(getCategoryParams(account.action), account)
                categoryServiceProvider.invoke().parseCategories(jsonCategories, false).isNotEmpty()
            }
        } catch (_: Exception) {
            false
        } finally {
            account.macAddress = originalMac
        }
    }

    override fun getChannelCountForAccount(accountId: String): Int = ChannelDb.get().getChannelCountForAccount(accountId)

    private fun getCategoryParams(accountAction: Account.AccountAction): Map<String, String> {
        val params = HashMap<String, String>()
        params["JsHttpRequest"] = Date().time.toString() + "-xml"
        params["type"] = accountAction.name
        params["action"] = if (accountAction == Account.AccountAction.itv) "get_genres" else "get_categories"
        return params
    }

    companion object {
        @JvmField
        val INSTANCE: CacheService = CacheServiceImpl()
    }
}
