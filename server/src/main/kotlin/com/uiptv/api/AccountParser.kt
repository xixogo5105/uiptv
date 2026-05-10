package com.uiptv.api

import com.uiptv.model.Account

interface AccountParser {
    fun parseAndSave(text: String, groupAccountsByMac: Boolean, convertM3uToXtreme: Boolean): List<Account>
}
