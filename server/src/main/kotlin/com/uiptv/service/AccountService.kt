package com.uiptv.service

import com.uiptv.db.AccountDb
import com.uiptv.db.AccountInfoDb
import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.db.PublishedM3uCategorySelectionDb
import com.uiptv.db.PublishedM3uChannelSelectionDb
import com.uiptv.db.PublishedM3uSelectionDb
import com.uiptv.db.SeriesWatchStateDb
import com.uiptv.db.SeriesWatchingNowSnapshotDb
import com.uiptv.db.VodWatchStateDb
import com.uiptv.model.Account
import com.uiptv.util.PingStalkerPortal
import com.uiptv.util.ServerUtils
import com.uiptv.util.XtremeCredentialsJson
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

object AccountService {
    private val changeRevision = AtomicLong(1)
    private val changeListeners = CopyOnWriteArraySet<AccountChangeListener>()
    private val sessionTokenByAccountKey = ConcurrentHashMap<String, String>()

    fun save(account: Account?) {
        if (account == null) {
            return
        }
        sanitizeAccountFields(account)
        val url = account.url.orEmpty()
        if (account.type == com.uiptv.util.AccountType.STALKER_PORTAL && !url.endsWith("/")) {
            account.url = "$url/"
        }
        sessionTokenByAccountKey.remove(getSessionAccountKey(account))
        AccountDb.get().save(account)
        touchChange()
    }
    fun delete(accountId: String?) {
        val normalizedAccountId = accountId.orEmpty()
        val account = AccountDb.get().getAccountById(normalizedAccountId)
        deleteAccountData(normalizedAccountId, account)
    }
    fun deleteAll() {
        sessionTokenByAccountKey.clear()
        AccountDb.get().getAccounts().forEach { deleteAccountData(it.dbId, it) }
        touchChange()
    }
    fun refreshFromDatabase() {
        sessionTokenByAccountKey.clear()
        touchChange()
    }
    fun addChangeListener(listener: AccountChangeListener?) {
        if (listener != null) {
            changeListeners.add(listener)
        }
    }
    fun removeChangeListener(listener: AccountChangeListener?) {
        if (listener != null) {
            changeListeners.remove(listener)
        }
    }

    fun getAll(): Map<String, Account> {
        val accounts = LinkedHashMap<String, Account>()
        AccountDb.get().getAccounts().forEach { account ->
            applySessionToken(account)
            accounts[account.accountName.orEmpty()] = account
        }
        return accounts
    }

    fun getById(dbId: String?): Account? {
        val account = AccountDb.get().getAccountById(dbId.orEmpty())
        applySessionToken(account)
        return account
    }

    fun getByName(accountName: String?): Account? {
        val account = AccountDb.get().getAccountByName(accountName.orEmpty())
        applySessionToken(account)
        return account
    }
    fun readToJson(): String = ServerUtils.objectToJson(AccountResolver().resolveAccounts())
    fun ensureServerPortalUrl(account: Account?): String {
        if (account == null) {
            return ""
        }
        if (com.uiptv.util.StringUtils.isNotBlank(account.serverPortalUrl)) {
            return account.serverPortalUrl.orEmpty()
        }
        val resolved = PingStalkerPortal.ping(account)
        if (com.uiptv.util.StringUtils.isNotBlank(resolved)) {
            account.serverPortalUrl = resolved
            AccountDb.get().saveServerPortalUrl(account)
        }
        return account.serverPortalUrl.orEmpty()
    }
    fun syncSessionToken(account: Account?) {
        if (account == null) {
            return
        }
        val key = getSessionAccountKey(account)
        if (com.uiptv.util.StringUtils.isBlank(key)) {
            return
        }
        if (com.uiptv.util.StringUtils.isNotBlank(account.token)) {
            sessionTokenByAccountKey[key] = account.token.orEmpty()
        } else {
            sessionTokenByAccountKey.remove(key)
        }
    }

    private fun applySessionToken(account: Account?) {
        if (account == null || com.uiptv.util.StringUtils.isNotBlank(account.token)) {
            return
        }
        val key = getSessionAccountKey(account)
        if (com.uiptv.util.StringUtils.isBlank(key)) {
            return
        }
        val cachedToken = sessionTokenByAccountKey[key]
        if (com.uiptv.util.StringUtils.isNotBlank(cachedToken)) {
            account.token = cachedToken
        }
    }

    private fun getSessionAccountKey(account: Account?): String {
        if (account == null) {
            return ""
        }
        if (com.uiptv.util.StringUtils.isNotBlank(account.dbId)) {
            return account.dbId.orEmpty().trim()
        }
        if (com.uiptv.util.StringUtils.isNotBlank(account.accountName)) {
            return account.accountName.orEmpty().trim().lowercase()
        }
        return ""
    }

    private fun deleteAccountData(accountId: String?, account: Account?) {
        if (account != null) {
            BookmarkService.removeByAccountName(account.accountName.orEmpty())
            sessionTokenByAccountKey.remove(getSessionAccountKey(account))
            val dbId = account.dbId.orEmpty()
            AccountInfoService.deleteByAccountId(dbId)
            PublishedM3uSelectionDb.get().deleteByAccountId(dbId)
            PublishedM3uCategorySelectionDb.get().deleteByAccountId(dbId)
            PublishedM3uChannelSelectionDb.get().deleteByAccountId(dbId)
        }
        SeriesWatchStateDb.get().deleteByAccount(accountId.orEmpty())
        SeriesWatchingNowSnapshotDb.get().deleteByAccount(accountId.orEmpty())
        VodWatchStateDb.get().deleteByAccount(accountId.orEmpty())
        ChannelDb.get().deleteByAccount(accountId.orEmpty())
        CategoryDb.get().deleteByAccount(account ?: Account())
        AccountDb.get().delete(accountId.orEmpty())
        touchChange()
    }

    private fun touchChange() {
        val revision = changeRevision.incrementAndGet()
        changeListeners.forEach { listener ->
            try {
                listener.onAccountsChanged(revision)
            } catch (_: Exception) {
                // Listener failures must never break account updates.
            }
        }
    }

    private fun sanitizeAccountFields(account: Account) {
        sanitizeStalkerMacAddresses(account)
        sanitizeXtremeCredentials(account)
    }

    private fun sanitizeStalkerMacAddresses(account: Account) {
        if (account.type != com.uiptv.util.AccountType.STALKER_PORTAL) {
            return
        }
        val ordered = LinkedHashSet<String>()
        val primaryMac = account.macAddress
        if (com.uiptv.util.StringUtils.isNotBlank(primaryMac)) {
            ordered.add(primaryMac.orEmpty().replace(" ", ""))
        }
        val macList = account.macAddressList
        if (com.uiptv.util.StringUtils.isNotBlank(macList)) {
            macList.orEmpty().split(",").forEach { mac ->
                val trimmed = mac.replace(" ", "")
                if (com.uiptv.util.StringUtils.isNotBlank(trimmed)) {
                    ordered.add(trimmed)
                }
            }
        }
        if (ordered.isEmpty()) {
            return
        }
        account.macAddressList = ordered.joinToString(",")
        if (com.uiptv.util.StringUtils.isBlank(primaryMac) || !ordered.contains(primaryMac.orEmpty().replace(" ", ""))) {
            account.macAddress = ordered.first()
        }
    }

    private fun sanitizeXtremeCredentials(account: Account) {
        if (account.type != com.uiptv.util.AccountType.XTREME_API) {
            return
        }
        val username = account.username
        val password = account.password
        var entries = XtremeCredentialsJson.parse(account.xtremeCredentialsJson)
        if (entries.isEmpty() && com.uiptv.util.StringUtils.isNotBlank(username) && com.uiptv.util.StringUtils.isNotBlank(password)) {
            entries = listOf(XtremeCredentialsJson.Entry(username.orEmpty(), password.orEmpty(), true))
        }
        if (entries.isEmpty()) {
            return
        }
        val normalized = XtremeCredentialsJson.normalize(entries, username)
        val defaultEntry = XtremeCredentialsJson.resolveDefault(normalized)
        if (defaultEntry != null) {
            account.username = defaultEntry.username
            account.password = defaultEntry.password
        }
        account.xtremeCredentialsJson = XtremeCredentialsJson.toJson(normalized)
    }
}
