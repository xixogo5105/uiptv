package com.uiptv.util

import com.uiptv.api.AccountParser
import com.uiptv.model.Account
import com.uiptv.service.AccountService
import java.net.URI
import java.util.LinkedHashMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.regex.Pattern

class XtremeParser(
    private val accountProvider: Function<String, Account?> = Function { AccountService.getByName(it) },
    private val accountSaver: Consumer<Account> = Consumer { AccountService.save(it) }
) : AccountParser {
    override fun parseAndSave(text: String, groupAccountsByMac: Boolean, convertM3uToXtreme: Boolean): List<Account> {
        val lines = text.split(Regex("\\R"))
        val currentBlock = ArrayList<String>()
        val parsedAccounts = ArrayList<ParsedAccount>()

        for (line in lines) {
            val trimmed = UiptUtils.replaceAllNonPrintableChars(line).orEmpty().trim()
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    processBlock(currentBlock)?.let(parsedAccounts::add)
                    currentBlock.clear()
                }
                continue
            }
            currentBlock += trimmed
        }
        if (currentBlock.isNotEmpty()) {
            processBlock(currentBlock)?.let(parsedAccounts::add)
        }
        return saveAccounts(parsedAccounts, groupAccountsByMac)
    }

    private fun processBlock(block: List<String>): ParsedAccount? {
        val joinedBlock = block.joinToString(" ")
        val url = extractFirstMatch(URL_PATTERN, joinedBlock, 1) ?: return null
        val credentials = extractCredentials(joinedBlock, url)
        return if (credentials.isComplete()) ParsedAccount(url, credentials) else null
    }

    private fun extractCredentials(joinedBlock: String, url: String): Credentials {
        var username = extractFirstMatch(LABELED_USER, joinedBlock, 2)
        var password = extractFirstMatch(LABELED_PASS, joinedBlock, 3)
        if (username != null && password != null) {
            return Credentials(username, password)
        }
        val unlabeled = extractUnlabeledTokens(joinedBlock, url, username, password).toMutableList()
        if (username == null && unlabeled.isNotEmpty()) {
            username = unlabeled.removeAt(0)
        }
        if (password == null && unlabeled.isNotEmpty()) {
            password = unlabeled.removeAt(0)
        }
        return Credentials(username, password)
    }

    private fun extractFirstMatch(pattern: Pattern, text: String, group: Int): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(group) else null
    }

    private fun extractUnlabeledTokens(joinedBlock: String, url: String, username: String?, password: String?): List<String> {
        var remaining = joinedBlock.replace(url, "")
        remaining = stripKnownCredential(remaining, username, "(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*")
        remaining = stripKnownCredential(remaining, password, "(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*")
        return remaining.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.length > 1 }
            .toMutableList()
    }

    private fun stripKnownCredential(remaining: String, value: String?, prefixPattern: String): String {
        if (value == null) {
            return remaining
        }
        return remaining.replace(Regex(prefixPattern + Pattern.quote(value)), "")
    }

    private fun saveAccounts(parsedAccounts: List<ParsedAccount>, groupAccounts: Boolean): List<Account> {
        if (parsedAccounts.isEmpty()) {
            return emptyList()
        }
        return if (groupAccounts) saveGroupedAccounts(parsedAccounts) else saveIndividualAccounts(parsedAccounts)
    }

    private fun saveIndividualAccounts(parsedAccounts: List<ParsedAccount>): List<Account> {
        val createdAccounts = ArrayList<Account>()
        val processedNames = HashSet<String>()
        for (parsed in parsedAccounts) {
            val name = nextUniqueAccountName(parsed.url, processedNames)
            val account = buildAccount(name, parsed.url, parsed.credentials)
            accountSaver.accept(account)
            createdAccounts += account
            processedNames += name
        }
        return createdAccounts
    }

    private fun saveGroupedAccounts(parsedAccounts: List<ParsedAccount>): List<Account> {
        val groupedAccounts = LinkedHashMap<String, Account>()
        val createdAccounts = ArrayList<Account>()
        for (parsed in parsedAccounts) {
            val name = accountNameFromUrl(parsed.url)
            val existing = groupedAccounts[name]
            if (existing != null) {
                mergeCredentials(existing, parsed.credentials)
                continue
            }
            val existingInDb = accountProvider.apply(name)
            if (existingInDb != null) {
                mergeCredentials(existingInDb, parsed.credentials)
                groupedAccounts[name] = existingInDb
            } else {
                val account = buildAccount(name, parsed.url, parsed.credentials)
                groupedAccounts[name] = account
                createdAccounts += account
            }
        }
        groupedAccounts.values.forEach(accountSaver::accept)
        return createdAccounts
    }

    private fun buildAccount(name: String, url: String, credentials: Credentials): Account {
        val account = Account(
            name,
            credentials.username,
            credentials.password,
            url,
            null,
            null,
            null,
            null,
            null,
            null,
            AccountType.XTREME_API,
            null,
            url,
            false
        )
        mergeCredentials(account, credentials)
        return account
    }

    private fun mergeCredentials(account: Account, credentials: Credentials) {
        val entries = XtremeCredentialsJson.parse(account.xtremeCredentialsJson).toMutableList()
        if (entries.isEmpty() && StringUtils.isNotBlank(account.username) && StringUtils.isNotBlank(account.password)) {
            entries += XtremeCredentialsJson.Entry(account.username!!, account.password!!, true)
        }
        val exists = entries.any { it.username == credentials.username && it.password == credentials.password }
        if (!exists) {
            entries += XtremeCredentialsJson.Entry(credentials.username!!, credentials.password!!, entries.isEmpty())
        }
        val normalized = XtremeCredentialsJson.normalize(entries, account.username)
        val defaultEntry = XtremeCredentialsJson.resolveDefault(normalized)
        if (defaultEntry != null) {
            account.username = defaultEntry.username
            account.password = defaultEntry.password
        }
        account.xtremeCredentialsJson = XtremeCredentialsJson.toJson(normalized)
    }

    private fun nextUniqueAccountName(url: String, processedNames: Set<String>): String {
        val baseName = accountNameFromUrl(url)
        if (accountProvider.apply(baseName) == null && !processedNames.contains(baseName)) {
            return baseName
        }
        var counter = 1
        var candidate = "$baseName ($counter)"
        while (accountProvider.apply(candidate) != null || processedNames.contains(candidate)) {
            counter++
            candidate = "$baseName ($counter)"
        }
        return candidate
    }

    private fun accountNameFromUrl(urlString: String?): String {
        if (StringUtils.isBlank(urlString)) {
            return urlString ?: ""
        }
        return try {
            URI.create(urlString).host
        } catch (_: Exception) {
            urlString ?: ""
        }
    }

    private data class Credentials(val username: String?, val password: String?) {
        fun isComplete(): Boolean = username != null && password != null
    }

    private data class ParsedAccount(val url: String, val credentials: Credentials)

    private companion object {
        val URL_PATTERN: Pattern = Pattern.compile("(https?://\\S+)")
        val LABELED_USER: Pattern = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)")
        val LABELED_PASS: Pattern = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)")
    }
}
