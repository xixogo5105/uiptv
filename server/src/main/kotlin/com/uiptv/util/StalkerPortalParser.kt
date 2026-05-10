package com.uiptv.util

import com.uiptv.api.AccountParser
import com.uiptv.api.StalkerAttributeParser
import com.uiptv.model.Account
import com.uiptv.service.AccountService
import java.time.ZoneId
import java.util.LinkedHashMap
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function

class StalkerPortalParser(
    private val accountProvider: Function<String, Account?> = Function { AccountService.getInstance().getByName(it) },
    private val accountSaver: Consumer<Account> = Consumer { AccountService.getInstance().save(it) }
) : AccountParser {
    override fun parseAndSave(text: String, groupAccountsByMac: Boolean, convertM3uToXtreme: Boolean): List<Account> {
        val sanitizedText = sanitizeInput(text)
        val parsedAccounts = ArrayList<Account>()
        var account = Account()
        val macParser = MacAttributeParser()
        val urlParser = UrlAttributeParser()
        var lastSeenUrl: String? = null

        for (line in sanitizedText.split(Regex("\\R"))) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                continue
            }

            val parsedLine = parseLine(line, trimmedLine, urlParser, macParser)
            lastSeenUrl = parsedLine.url ?: lastSeenUrl
            if (shouldStartNewAccount(account, parsedLine.mac)) {
                addParsedAccount(parsedAccounts, account)
                account = newAccountWithLastSeenUrl(lastSeenUrl)
            }
            applyLineMetadata(account, trimmedLine)
            applyParsedAttributes(account, line)
        }
        addParsedAccount(parsedAccounts, account)
        return saveAccounts(parsedAccounts, groupAccountsByMac)
    }

    private fun applyValueToAccount(account: Account, value: String, type: StalkerAttributeType) {
        when (type) {
            StalkerAttributeType.URL -> if (!StringUtils.isNotBlank(account.url)) {
                account.url = value
                account.type = AccountType.STALKER_PORTAL
            }
            StalkerAttributeType.MAC -> account.macAddress = value
            StalkerAttributeType.SERIAL -> if (!StringUtils.isNotBlank(account.serialNumber)) {
                account.serialNumber = value
            }
            StalkerAttributeType.SERIAL_CUT -> account.serialNumber = value
            StalkerAttributeType.DEVICE_ID_1 -> account.deviceId1 = value
            StalkerAttributeType.DEVICE_ID_2 -> account.deviceId2 = value
            StalkerAttributeType.SIGNATURE -> if (!StringUtils.isNotBlank(account.signature)) {
                account.signature = value
            }
        }
    }

    private fun saveAccounts(accounts: List<Account>, groupAccountsByMac: Boolean): List<Account> {
        val groupedAccounts = LinkedHashMap<String, Account>()
        val groupedExtraAccounts = LinkedHashMap<String, Account>()
        val individualAccounts = ArrayList<Account>()
        val createdAccounts = ArrayList<Account>()
        val processedNames = HashSet<String>()

        val validAccounts = accounts.filter { StringUtils.isNotBlank(it.url) && StringUtils.isNotBlank(it.macAddress) }
        for (currentAccount in validAccounts) {
            if (groupAccountsByMac) {
                if (hasExtraParams(currentAccount)) {
                    saveGroupedExtraParamAccount(currentAccount, groupedExtraAccounts, individualAccounts, createdAccounts, processedNames)
                } else {
                    saveGroupedAccount(currentAccount, groupedAccounts, createdAccounts, processedNames)
                }
            } else {
                saveIndividualAccount(currentAccount, individualAccounts, createdAccounts, processedNames)
            }
        }

        individualAccounts.forEach(accountSaver::accept)
        groupedAccounts.values.forEach(accountSaver::accept)
        return createdAccounts
    }

    private fun sanitizeInput(text: String): String =
        text.split(Regex("\\R"))
            .filterNot { it.contains("http") && it.contains("?") && it.contains("=") }
            .joinToString("\n") { UiptUtils.sanitizeStalkerText(it) }

    private fun parseLine(
        line: String,
        trimmedLine: String,
        urlParser: UrlAttributeParser,
        macParser: MacAttributeParser
    ): ParsedLine {
        val url = urlParser.parse(line)
        val mac = if (url == null) macParser.parse(line) else null
        return ParsedLine(trimmedLine, url, mac)
    }

    private fun shouldStartNewAccount(account: Account, mac: String?): Boolean =
        mac != null && StringUtils.isNotBlank(account.macAddress)

    private fun addParsedAccount(parsedAccounts: MutableList<Account>, account: Account) {
        if (StringUtils.isNotBlank(account.url) && StringUtils.isNotBlank(account.macAddress)) {
            parsedAccounts += account
        }
    }

    private fun newAccountWithLastSeenUrl(lastSeenUrl: String?): Account {
        val account = Account()
        if (lastSeenUrl != null) {
            account.url = lastSeenUrl
            account.type = AccountType.STALKER_PORTAL
        }
        return account
    }

    private fun applyLineMetadata(account: Account, trimmedLine: String) {
        if ("POST".equals(trimmedLine, true)) {
            account.httpMethod = "POST"
        }
        detectTimezone(trimmedLine)?.let { account.timezone = it }
    }

    private fun applyParsedAttributes(account: Account, line: String) {
        for (parser in ATTRIBUTE_PARSERS) {
            val value = parser.parse(line) ?: continue
            val type = parser.getAttributeType()
            applyValueToAccount(account, value, type)
            if (type == StalkerAttributeType.DEVICE_ID_1) {
                val lower = line.lowercase()
                if (Regex(".*1\\s*/\\s*2.*").matches(lower)) {
                    applyValueToAccount(account, value, StalkerAttributeType.DEVICE_ID_2)
                }
            }
            return
        }
    }

    private fun hasExtraParams(account: Account): Boolean =
        StringUtils.isNotBlank(account.serialNumber) ||
            StringUtils.isNotBlank(account.deviceId1) ||
            StringUtils.isNotBlank(account.deviceId2) ||
            StringUtils.isNotBlank(account.signature)

    private fun saveGroupedAccount(
        currentAccount: Account,
        groupedAccounts: MutableMap<String, Account>,
        createdAccounts: MutableList<Account>,
        processedNames: MutableSet<String>
    ) {
        val name = UiptUtils.getNameFromUrl((currentAccount.url ?: "").replace("_", ""))
        currentAccount.accountName = name
        val existing = groupedAccounts[name]
        if (existing != null) {
            appendMacAddress(existing, currentAccount.macAddress ?: "")
            mergeExtraParams(existing, currentAccount)
            return
        }
        val existingInDb = accountProvider.apply(name)
        if (existingInDb != null) {
            appendMacAddress(existingInDb, currentAccount.macAddress ?: "")
            mergeExtraParams(existingInDb, currentAccount)
            groupedAccounts[name] = existingInDb
            existingInDb.accountName?.let(processedNames::add)
            return
        }
        currentAccount.macAddressList = currentAccount.macAddress
        groupedAccounts[name] = currentAccount
        processedNames.add(name)
        createdAccounts += currentAccount
    }

    private fun saveGroupedExtraParamAccount(
        currentAccount: Account,
        groupedExtraAccounts: MutableMap<String, Account>,
        individualAccounts: MutableList<Account>,
        createdAccounts: MutableList<Account>,
        processedNames: MutableSet<String>
    ) {
        val identityKey = buildExtraParamIdentityKey(currentAccount)
        val existing = groupedExtraAccounts[identityKey]
        if (existing != null) {
            appendMacAddress(existing, currentAccount.macAddress ?: "")
            mergeExtraParams(existing, currentAccount)
            return
        }

        val existingInDb = findExistingExtraParamAccount(currentAccount)
        if (existingInDb != null) {
            appendMacAddress(existingInDb, currentAccount.macAddress ?: "")
            mergeExtraParams(existingInDb, currentAccount)
            groupedExtraAccounts[identityKey] = existingInDb
            individualAccounts += existingInDb
            existingInDb.accountName?.let(processedNames::add)
            return
        }

        saveIndividualAccount(currentAccount, individualAccounts, createdAccounts, processedNames)
        groupedExtraAccounts[identityKey] = currentAccount
    }

    private fun saveIndividualAccount(
        currentAccount: Account,
        individualAccounts: MutableList<Account>,
        createdAccounts: MutableList<Account>,
        processedNames: MutableSet<String>
    ) {
        val uniqueName = nextUniqueAccountName(currentAccount.url ?: "", processedNames)
        currentAccount.accountName = uniqueName
        currentAccount.macAddressList = currentAccount.macAddress
        individualAccounts += currentAccount
        processedNames.add(uniqueName)
        createdAccounts += currentAccount
    }

    private fun nextUniqueAccountName(url: String, processedNames: Set<String>): String {
        val baseName = UiptUtils.getNameFromUrl(url)
        var counter = 1
        var uniqueName = "$baseName($counter)"
        while (accountProvider.apply(uniqueName) != null || processedNames.contains(uniqueName)) {
            counter++
            uniqueName = "$baseName($counter)"
        }
        return uniqueName
    }

    private fun appendMacAddress(account: Account, macAddress: String) {
        val macList = account.macAddressList ?: account.macAddress
        if (macList.isNullOrBlank()) {
            account.macAddressList = macAddress
            return
        }
        if (!macList.contains(macAddress)) {
            account.macAddressList = "$macList,$macAddress"
        }
    }

    private fun findExistingExtraParamAccount(account: Account): Account? {
        val baseName = UiptUtils.getNameFromUrl((account.url ?: "").replace("_", ""))
        val directMatch = accountProvider.apply(baseName)
        if (isSameExtraParamAccount(directMatch, account)) {
            return directMatch
        }
        for (counter in 1 until 1000) {
            val candidate = accountProvider.apply("$baseName($counter)") ?: return null
            if (isSameExtraParamAccount(candidate, account)) {
                return candidate
            }
        }
        return null
    }

    private fun isSameExtraParamAccount(candidate: Account?, account: Account): Boolean =
        candidate != null &&
            hasExtraParams(candidate) &&
            normalizeIdentityValue(UiptUtils.getNameFromUrl((candidate.url ?: "").replace("_", ""))) ==
            normalizeIdentityValue(UiptUtils.getNameFromUrl((account.url ?: "").replace("_", ""))) &&
            normalizeIdentityValue(candidate.serialNumber) == normalizeIdentityValue(account.serialNumber) &&
            normalizeIdentityValue(candidate.deviceId1) == normalizeIdentityValue(account.deviceId1) &&
            normalizeIdentityValue(candidate.deviceId2) == normalizeIdentityValue(account.deviceId2) &&
            normalizeIdentityValue(candidate.signature) == normalizeIdentityValue(account.signature)

    private fun buildExtraParamIdentityKey(account: Account): String =
        listOf(
            normalizeIdentityValue(UiptUtils.getNameFromUrl((account.url ?: "").replace("_", ""))),
            normalizeIdentityValue(account.serialNumber),
            normalizeIdentityValue(account.deviceId1),
            normalizeIdentityValue(account.deviceId2),
            normalizeIdentityValue(account.signature)
        ).joinToString("|")

    private fun normalizeIdentityValue(value: String?): String = value?.trim()?.lowercase(Locale.ROOT) ?: ""

    private fun mergeExtraParams(target: Account, source: Account) {
        if (!StringUtils.isNotBlank(target.serialNumber) && StringUtils.isNotBlank(source.serialNumber)) {
            target.serialNumber = source.serialNumber
        }
        if (!StringUtils.isNotBlank(target.deviceId1) && StringUtils.isNotBlank(source.deviceId1)) {
            target.deviceId1 = source.deviceId1
        }
        if (!StringUtils.isNotBlank(target.deviceId2) && StringUtils.isNotBlank(source.deviceId2)) {
            target.deviceId2 = source.deviceId2
        }
        if (!StringUtils.isNotBlank(target.signature) && StringUtils.isNotBlank(source.signature)) {
            target.signature = source.signature
        }
        if (!StringUtils.isNotBlank(target.timezone) && StringUtils.isNotBlank(source.timezone)) {
            target.timezone = source.timezone
        }
        if (!StringUtils.isNotBlank(target.httpMethod) && StringUtils.isNotBlank(source.httpMethod)) {
            target.httpMethod = source.httpMethod
        }
    }

    private fun detectTimezone(line: String?): String? {
        if (line.isNullOrBlank()) {
            return null
        }
        val lowerLine = line.lowercase()
        for (zoneId in ZoneId.getAvailableZoneIds()) {
            if (lowerLine.contains(zoneId.lowercase())) {
                return zoneId
            }
        }
        return null
    }

    private data class ParsedLine(val trimmedLine: String, val url: String?, val mac: String?)

    private companion object {
        val ATTRIBUTE_PARSERS: List<StalkerAttributeParser> = listOf(
            UrlAttributeParser(),
            MacAttributeParser(),
            SerialCutAttributeParser(),
            SerialAttributeParser(),
            SignatureAttributeParser(),
            DeviceId1AttributeParser(),
            DeviceId2AttributeParser()
        )
    }
}
