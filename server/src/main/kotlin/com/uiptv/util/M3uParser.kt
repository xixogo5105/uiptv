package com.uiptv.util

import com.uiptv.api.AccountParser
import com.uiptv.model.Account
import com.uiptv.service.AccountService
import java.util.function.Consumer

class M3uParser(
    private val accountSaver: Consumer<Account> = Consumer { AccountService.save(it) }
) : AccountParser {
    override fun parseAndSave(text: String, groupAccountsByMac: Boolean, convertM3uToXtreme: Boolean): List<Account> {
        val createdAccounts = ArrayList<Account>()
        for (line in text.split(Regex("\\R"))) {
            for (potentialUrl in UiptUtils.replaceAllNonPrintableChars(line).orEmpty().split(UiptUtils.SPACER.toRegex())) {
                if (!UiptUtils.isValidURL(potentialUrl)) {
                    continue
                }

                var playlistUrl = potentialUrl
                var username: String? = null
                var password: String? = null
                var accountType = AccountType.M3U8_URL

                if (convertM3uToXtreme && UiptUtils.isUrlValidXtremeLink(potentialUrl)) {
                    accountType = AccountType.XTREME_API
                    username = UiptUtils.getUserNameFromUrl(potentialUrl)
                    password = UiptUtils.getPasswordNameFromUrl(potentialUrl)
                    playlistUrl = potentialUrl.split("get.php?")[0]
                }

                val uniqueName = UiptUtils.getUniqueNameFromUrl(playlistUrl)
                val account = Account(
                    uniqueName,
                    username,
                    password,
                    playlistUrl,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    accountType,
                    null,
                    playlistUrl,
                    false
                )
                if (accountType == AccountType.XTREME_API && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                    account.xtremeCredentialsJson = XtremeCredentialsJson.toJson(
                        listOf(XtremeCredentialsJson.Entry(username!!, password!!, true))
                    )
                }
                accountSaver.accept(account)
                createdAccounts += account
            }
        }
        return createdAccounts
    }
}
