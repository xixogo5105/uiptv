package com.uiptv.model

import com.uiptv.shared.BaseJson
import com.uiptv.util.AccountType
import com.uiptv.util.StringUtils
import java.util.Arrays
import java.util.Collections
import java.util.EnumSet
import java.util.LinkedHashMap

import com.uiptv.model.Account.AccountAction.itv
import com.uiptv.util.AccountType.M3U8_LOCAL
import com.uiptv.util.AccountType.M3U8_URL
import com.uiptv.util.AccountType.RSS_FEED
import com.uiptv.util.AccountType.STALKER_PORTAL
import com.uiptv.util.AccountType.XTREME_API
import com.uiptv.util.StringUtils.SPACE
import com.uiptv.util.StringUtils.isNotBlank

data class Account @JvmOverloads constructor(
    var serverPortalUrl: String? = null,
    var action: AccountAction = itv,
    var accountName: String? = null,
    var username: String? = null,
    var password: String? = null,
    var xtremeCredentialsJson: String? = null,
    var url: String? = null,
    var macAddress: String? = null,
    var macAddressList: String? = null,
    var serialNumber: String? = null,
    var deviceId1: String? = null,
    var deviceId2: String? = null,
    var signature: String? = null,
    var epg: String? = null,
    var m3u8Path: String? = null,
    var dbId: String? = null,
    var token: String? = null,
    @get:JvmName("isPinToTop")
    var pinToTop: Boolean = false,
    @get:JvmName("isResolveChainAndDeepRedirects")
    var resolveChainAndDeepRedirects: Boolean = false,
    var type: AccountType = STALKER_PORTAL,
    var httpMethod: String = "GET",
    var timezone: String = "Europe/London"
) : BaseJson() {

    @Suppress("java:S107")
    constructor(
        accountName: String?,
        username: String?,
        password: String?,
        url: String?,
        macAddress: String?,
        macAddressList: String?,
        serialNumber: String?,
        deviceId1: String?,
        deviceId2: String?,
        signature: String?,
        type: AccountType,
        epg: String?,
        m3u8Path: String?,
        pinToTop: Boolean
    ) : this(
        accountName = accountName,
        username = username,
        password = password,
        url = url,
        macAddress = macAddress,
        serialNumber = serialNumber,
        deviceId1 = deviceId1,
        deviceId2 = deviceId2,
        signature = signature,
        type = type,
        epg = epg,
        m3u8Path = m3u8Path,
        pinToTop = pinToTop
    ) {
        val macMap = LinkedHashMap<String, String>()
        if (isNotBlank(macAddress)) {
            macMap[macAddress!!.lowercase()] = macAddress.trim()
        }
        if (isNotBlank(macAddressList)) {
            Arrays.stream(macAddressList!!.split(",").toTypedArray())
                .filter(StringUtils::isNotBlank)
                .forEach { mac -> macMap[mac.lowercase()] = mac.replace(SPACE, "") }
        }
        this.macAddressList = macMap.values.joinToString(",")
    }

    fun isConnected(): Boolean {
        if (type != STALKER_PORTAL) {
            return true
        }
        return isNotBlank(token)
    }

    fun isNotConnected(): Boolean = !isConnected()

    enum class AccountAction {
        itv,
        vod,
        series
    }

    companion object {
        @JvmField
        val NOT_LIVE_TV_CHANNELS = Collections.unmodifiableSet(EnumSet.of(AccountAction.vod, AccountAction.series))

        @JvmField
        val VOD_AND_SERIES_SUPPORTED = Collections.unmodifiableSet(EnumSet.of(STALKER_PORTAL, XTREME_API))

        @JvmField
        val CACHE_SUPPORTED = Collections.unmodifiableSet(EnumSet.of(STALKER_PORTAL, XTREME_API, M3U8_URL, M3U8_LOCAL))

        @JvmField
        val PRE_DEFINED_URLS = Collections.unmodifiableSet(EnumSet.of(RSS_FEED, M3U8_URL, M3U8_LOCAL))

        const val LINE_SEPARATOR: String = "\n\r"
    }
}
