package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.util.AccountType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere

class AccountDb private constructor() : ExposedCrudRepository<String, Account>() {
    companion object {
        private val instance = AccountDb()

        @JvmStatic
        fun get(): AccountDb = instance
    }

    override fun findAll(): List<Account> = query {
        AccountTable.selectAll()
            .orderBy(AccountTable.pinToTop to SortOrder.DESC, AccountTable.id to SortOrder.ASC)
            .map(ResultRow::toAccount)
    }

    override fun findById(id: String): Account? = query {
        id.toIntOrNull()
            ?.let { dbId -> AccountTable.selectAll().where { AccountTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toAccount()
    }

    override fun save(entity: Account): Account {
        query {
            val existingId = findExistingId(entity)
            if (existingId == null) {
                val insertedId = AccountTable.insert { row -> row.write(entity) }[AccountTable.id]
                entity.dbId = insertedId.toString()
            } else {
                AccountTable.update({ AccountTable.id eq existingId }) { row -> row.write(entity) }
                entity.dbId = existingId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            AccountTable.deleteWhere { AccountTable.id eq dbId }
        }
    }

    fun getAccounts(): List<Account> = findAll()

    fun getAccountById(id: String): Account? = findById(id)

    fun getAccountByName(accountName: String): Account? = query {
        if (accountName.isBlank()) {
            null
        } else {
            AccountTable.selectAll()
                .where { AccountTable.accountName eq accountName }
                .limit(1)
                .firstOrNull()
                ?.toAccount()
        }
    }

    fun delete(id: String) {
        deleteById(id)
    }

    fun saveServerPortalUrl(account: Account) {
        val dbId = account.dbId?.toIntOrNull() ?: return
        query {
            AccountTable.update({ AccountTable.id eq dbId }) { row ->
                row[serverPortalUrl] = account.serverPortalUrl
            }
        }
    }

    private fun findExistingId(account: Account): Int? {
        val dbId = account.dbId?.toIntOrNull()
        if (dbId != null) {
            return dbId
        }
        val accountName = account.accountName.orEmpty().trim()
        if (accountName.isEmpty()) {
            return null
        }
        return AccountTable.selectAll()
            .where { AccountTable.accountName eq accountName }
            .limit(1)
            .firstOrNull()
            ?.get(AccountTable.id)
    }
}

private object AccountTable : Table(DatabaseUtils.DbTable.ACCOUNT_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountName = text("accountName")
    val username = text("username").nullable()
    val password = text("password").nullable()
    val xtremeCredentialsJson = text("xtremeCredentialsJson").nullable()
    val url = text("url").nullable()
    val macAddress = text("macAddress").nullable()
    val macAddressList = text("macAddressList").nullable()
    val serialNumber = text("serialNumber").nullable()
    val deviceId1 = text("deviceId1").nullable()
    val deviceId2 = text("deviceId2").nullable()
    val signature = text("signature").nullable()
    val epg = text("epg").nullable()
    val m3u8Path = text("m3u8Path").nullable()
    val type = text("type").nullable()
    val serverPortalUrl = text("serverPortalUrl").nullable()
    val pinToTop = text("pinToTop").nullable()
    val resolveChainAndDeepRedirects = text("resolveChainAndDeepRedirects").nullable()
    val httpMethod = text("httpMethod").nullable()
    val timezone = text("timezone").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toAccount(): Account = Account().apply {
    dbId = this@toAccount[AccountTable.id].toString()
    accountName = this@toAccount[AccountTable.accountName]
    username = this@toAccount[AccountTable.username]
    password = this@toAccount[AccountTable.password]
    xtremeCredentialsJson = this@toAccount[AccountTable.xtremeCredentialsJson]
    url = this@toAccount[AccountTable.url]
    macAddress = this@toAccount[AccountTable.macAddress]
    macAddressList = this@toAccount[AccountTable.macAddressList]
    serialNumber = this@toAccount[AccountTable.serialNumber]
    deviceId1 = this@toAccount[AccountTable.deviceId1]
    deviceId2 = this@toAccount[AccountTable.deviceId2]
    signature = this@toAccount[AccountTable.signature]
    epg = this@toAccount[AccountTable.epg]
    m3u8Path = this@toAccount[AccountTable.m3u8Path]
    type = this@toAccount[AccountTable.type].toAccountType()
    serverPortalUrl = this@toAccount[AccountTable.serverPortalUrl]
    pinToTop = this@toAccount[AccountTable.pinToTop].asStoredBoolean()
    resolveChainAndDeepRedirects = this@toAccount[AccountTable.resolveChainAndDeepRedirects].asStoredBoolean()
    httpMethod = this@toAccount[AccountTable.httpMethod].orIfBlank("GET")
    timezone = this@toAccount[AccountTable.timezone].orIfBlank("Europe/London")
}

private fun <T : UpdateBuilder<*>> T.write(account: Account) {
    this[AccountTable.accountName] = account.accountName.orEmpty()
    this[AccountTable.username] = account.username
    this[AccountTable.password] = account.password
    this[AccountTable.xtremeCredentialsJson] = account.xtremeCredentialsJson
    this[AccountTable.url] = account.url
    this[AccountTable.macAddress] = account.macAddress
    this[AccountTable.macAddressList] = account.macAddressList
    this[AccountTable.serialNumber] = account.serialNumber
    this[AccountTable.deviceId1] = account.deviceId1
    this[AccountTable.deviceId2] = account.deviceId2
    this[AccountTable.signature] = account.signature
    this[AccountTable.epg] = account.epg
    this[AccountTable.m3u8Path] = account.m3u8Path
    this[AccountTable.type] = account.type.name
    this[AccountTable.serverPortalUrl] = account.serverPortalUrl
    this[AccountTable.pinToTop] = account.pinToTop.asStoredBoolean()
    this[AccountTable.resolveChainAndDeepRedirects] = account.resolveChainAndDeepRedirects.asStoredBoolean()
    this[AccountTable.httpMethod] = account.httpMethod
    this[AccountTable.timezone] = account.timezone
}

private fun String?.toAccountType(): AccountType =
    this?.takeIf(String::isNotBlank)?.let(AccountType::valueOf) ?: AccountType.STALKER_PORTAL

private fun String?.asStoredBoolean(): Boolean = this?.trim() == "1"

private fun Boolean.asStoredBoolean(): String = if (this) "1" else "0"

private fun String?.orIfBlank(defaultValue: String): String =
    this?.takeIf(String::isNotBlank) ?: defaultValue
