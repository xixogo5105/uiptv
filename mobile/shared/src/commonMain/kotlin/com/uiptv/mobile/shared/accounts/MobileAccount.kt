package com.uiptv.mobile.shared.accounts

enum class MobileAccountType(val displayName: String, val cacheRefreshSupported: Boolean) {
    STALKER_PORTAL("Stalker Portal", true),
    XTREME_API("Xtreme API", true),
    M3U8_URL("M3U URL", true),
    M3U8_LOCAL("M3U Local", true),
    RSS_FEED("RSS Feed", false)
}

data class MobileAccount(
    val id: Long? = null,
    val accountName: String = "",
    val type: MobileAccountType = MobileAccountType.STALKER_PORTAL,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val xtremeCredentialsJson: String = "",
    val macAddress: String = "",
    val macAddressList: String = "",
    val serialNumber: String = "",
    val deviceId1: String = "",
    val deviceId2: String = "",
    val signature: String = "",
    val epg: String = "",
    val m3u8Path: String = "",
    val serverPortalUrl: String = "",
    val pinToTop: Boolean = false,
    val resolveChainAndDeepRedirects: Boolean = false,
    val httpMethod: String = "GET",
    val timezone: String = "Europe/London"
) {
    fun normalizedForSave(): MobileAccount {
        val normalizedType = type
        val normalizedUrl = if (
            normalizedType == MobileAccountType.STALKER_PORTAL &&
            url.isNotBlank() &&
            !url.endsWith("/")
        ) {
            "$url/"
        } else {
            url
        }
        val normalizedMac = normalizedMacAddress()
        return copy(
            accountName = accountName.trim(),
            url = normalizedUrl.trim(),
            username = username.trim(),
            macAddress = normalizedMac,
            macAddressList = normalizeMacAddressCsv(
                value = macAddressList,
                primaryMacAddress = if (normalizedType == MobileAccountType.STALKER_PORTAL) normalizedMac else ""
            ),
            httpMethod = httpMethod.ifBlank { "GET" }.trim().uppercase(),
            timezone = timezone.ifBlank { "Europe/London" }.trim()
        )
    }

    val canRefreshCache: Boolean
        get() = type.cacheRefreshSupported

    private fun normalizedMacAddress(): String =
        macAddress.filterNot { it.isWhitespace() }

    private fun normalizeMacAddressCsv(value: String, primaryMacAddress: String): String =
        (listOf(primaryMacAddress) + value.split(","))
            .map { it.filterNot { char -> char.isWhitespace() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(",")
}

data class AccountCacheSummary(
    val liveCategories: Int = 0,
    val liveChannels: Int = 0,
    val vodCategories: Int = 0,
    val vodChannels: Int = 0,
    val seriesCategories: Int = 0,
    val seriesChannels: Int = 0,
    val seriesEpisodes: Int = 0
) {
    val totalItems: Int
        get() = liveCategories + liveChannels + vodCategories + vodChannels +
            seriesCategories + seriesChannels + seriesEpisodes
}

interface AccountRepository {
    suspend fun listAccounts(): List<MobileAccount>

    suspend fun saveAccount(account: MobileAccount): MobileAccount

    suspend fun deleteAccount(accountId: Long)

    suspend fun clearAccountCache(accountId: Long): AccountCacheSummary

    suspend fun clearAllCache(): AccountCacheSummary

    suspend fun cacheSummary(accountId: Long): AccountCacheSummary
}
