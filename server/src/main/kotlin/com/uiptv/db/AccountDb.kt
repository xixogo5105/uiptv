package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.util.AccountType
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import java.sql.ResultSet
import java.sql.SQLException

class AccountDb : BaseDb<Account>(DatabaseUtils.DbTable.ACCOUNT_TABLE) {
    companion object {
        private var instance: AccountDb? = null

        @JvmStatic
        @Synchronized
        fun get(): AccountDb {
            if (instance == null) {
                instance = AccountDb()
            }
            return instance!!
        }
    }

    override fun populate(resultSet: ResultSet): Account {
        val type = nullSafeString(resultSet, "type")
        val account = Account(
            nullSafeString(resultSet, "accountName"),
            nullSafeString(resultSet, "username"),
            nullSafeString(resultSet, "password"),
            nullSafeString(resultSet, "url"),
            nullSafeString(resultSet, "macAddress"),
            nullSafeString(resultSet, "macAddressList"),
            nullSafeString(resultSet, "serialNumber"),
            nullSafeString(resultSet, "deviceId1"),
            nullSafeString(resultSet, "deviceId2"),
            nullSafeString(resultSet, "signature"),
            if (isNotBlank(type)) AccountType.valueOf(type!!) else AccountType.STALKER_PORTAL,
            nullSafeString(resultSet, "epg"),
            nullSafeString(resultSet, "m3u8Path"),
            safeBoolean(resultSet, "pinToTop")
        )
        account.dbId = nullSafeString(resultSet, "id")
        account.serverPortalUrl = nullSafeString(resultSet, "serverPortalUrl")
        account.resolveChainAndDeepRedirects = safeBoolean(resultSet, "resolveChainAndDeepRedirects")
        account.httpMethod = if (isNotBlank(nullSafeString(resultSet, "httpMethod"))) nullSafeString(resultSet, "httpMethod")!! else "GET"
        account.timezone = if (isNotBlank(nullSafeString(resultSet, "timezone"))) nullSafeString(resultSet, "timezone")!! else "Europe/London"
        account.xtremeCredentialsJson = nullSafeString(resultSet, "xtremeCredentialsJson")
        return account
    }

    fun getAccounts(): List<Account> = getAll("order by pinToTop desc, id", emptyArray())

    fun getAccountById(id: String): Account? = getById(id)

    fun getAccountByName(accountName: String): Account? = getAll(" WHERE accountName=?", arrayOf(accountName)).firstOrNull()

    fun save(account: Account) {
        val dbAccount = getAccountByName(account.accountName ?: "")
        val accountExist = dbAccount != null
        val saveQuery = if (accountExist) {
            DatabaseUtils.updateTableSql(DatabaseUtils.DbTable.ACCOUNT_TABLE)
        } else {
            DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.ACCOUNT_TABLE)
        }
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(saveQuery).use { statement ->
                    statement.setString(1, account.accountName)
                    statement.setString(2, account.username)
                    statement.setString(3, account.password)
                    statement.setString(4, account.xtremeCredentialsJson)
                    statement.setString(5, account.url)
                    statement.setString(6, account.macAddress)
                    statement.setString(7, account.macAddressList)
                    statement.setString(8, account.serialNumber)
                    statement.setString(9, account.deviceId1)
                    statement.setString(10, account.deviceId2)
                    statement.setString(11, account.signature)
                    statement.setString(12, account.epg)
                    statement.setString(13, account.m3u8Path)
                    statement.setString(14, account.type.name)
                    statement.setString(15, account.serverPortalUrl)
                    statement.setString(16, if (account.pinToTop) "1" else "0")
                    statement.setString(17, if (account.resolveChainAndDeepRedirects) "1" else "0")
                    statement.setString(18, account.httpMethod)
                    statement.setString(19, account.timezone)
                    if (accountExist) {
                        statement.setInt(20, dbAccount!!.dbId!!.toInt())
                    }
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query", e)
        }
    }

    fun saveServerPortalUrl(account: Account) {
        if (isBlank(account.dbId)) {
            return
        }
        save(account)
    }
}
