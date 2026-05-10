package com.uiptv.util

import com.uiptv.api.AccountParser
import com.uiptv.model.Account

object TextParserService {
    const val MODE_STALKER: String = "Stalker Portal"
    const val MODE_XTREME: String = "Xtreme"
    const val MODE_M3U: String = "M3U Playlists"

    @JvmStatic
    fun saveBulkAccounts(
        text: String,
        mode: String,
        groupAccountsByMac: Boolean,
        convertM3uToXtreme: Boolean
    ): List<Account> {
        val parser: AccountParser = when (mode) {
            MODE_STALKER -> StalkerPortalParser()
            MODE_XTREME -> XtremeParser()
            else -> M3uParser()
        }
        return parser.parseAndSave(text, groupAccountsByMac, convertM3uToXtreme)
    }
}
