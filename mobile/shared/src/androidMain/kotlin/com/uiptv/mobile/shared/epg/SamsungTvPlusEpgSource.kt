package com.uiptv.mobile.shared.epg

import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.logging.AndroidAppLog
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Uses Samsung TV Plus's compressed upstream guide instead of its 1 KiB-chunk local XMLTV relay. */
internal object SamsungTvPlusEpgSource {
    private const val UPSTREAM = "https://i.mjh.nz/SamsungTVPlus"

    fun resolve(account: MobileAccount, configuredEpg: String): String? {
        if (account.type != MobileAccountType.M3U8_URL) return null
        val playlist = parse(account.url.ifBlank { account.m3u8Path }) ?: return null
        val epg = parse(configuredEpg) ?: return null
        val sameHost = playlist.host?.equals(epg.host, ignoreCase = true) == true
        val samePort = playlist.port == epg.port
        val playlistPath = playlist.path.trimEnd('/')
        val epgPath = epg.path.trimEnd('/')
        val sourceMatches = sameHost && samePort && playlistPath == "/playlist.m3u8" && epgPath == "/epg.xml"
        AndroidAppLog.d("UIPTV-EPG", "Samsung direct source check playlistPath=$playlistPath epgPath=$epgPath sameHost=$sameHost samePort=$samePort match=$sourceMatches")
        if (!sourceMatches) return null

        val configuredRegions = queryParameter(epg, "regions")
            ?: queryParameter(playlist, "regions")
            ?: inferRegion(account.accountName)
        val regions = configuredRegions.orEmpty()
            .split('|').map(String::trim).filter(String::isNotEmpty)
        val region = when {
            regions.isEmpty() || regions.size > 1 -> "all"
            else -> regions.single().lowercase()
        }
        if (!region.matches(Regex("[a-z]{2,8}"))) return null
        return "$UPSTREAM/$region.xml.gz"
    }

    private fun inferRegion(accountName: String): String? =
        if (Regex("\\bindia\\b", RegexOption.IGNORE_CASE).containsMatchIn(accountName)) "in" else null

    private fun parse(value: String): URI? = runCatching { URI(value.replace("|", "%7C")) }.getOrNull()

    private fun queryParameter(uri: URI, name: String): String? = uri.rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            if (decode(key) == name) decode(part.substringAfter('=', "")) else null
        }

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
