package com.uiptv.db

import com.uiptv.model.AccountInfo
import com.uiptv.model.AccountStatus
import com.uiptv.util.StringUtils.isBlank
import java.sql.ResultSet
import java.sql.SQLException

class AccountInfoDb : BaseDb<AccountInfo>(DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE) {
    companion object {
        private var instance: AccountInfoDb? = null

        @JvmStatic
        @Synchronized
        fun get(): AccountInfoDb {
            if (instance == null) {
                instance = AccountInfoDb()
            }
            return instance!!
        }
    }

    override fun populate(resultSet: ResultSet): AccountInfo {
        val info = AccountInfo()
        info.dbId = nullSafeString(resultSet, "id")
        info.accountId = nullSafeString(resultSet, "accountId")
        info.expireDate = nullSafeString(resultSet, "expireDate")
        info.accountStatus = AccountStatus.fromValue(nullSafeString(resultSet, "accountStatus"))
        info.accountBalance = nullSafeString(resultSet, "accountBalance")
        info.tariffName = nullSafeString(resultSet, "tariffName")
        info.tariffPlan = nullSafeString(resultSet, "tariffPlan")
        info.defaultTimezone = nullSafeString(resultSet, "defaultTimezone")
        info.profileJson = nullSafeString(resultSet, "profileJson")
        info.passHash = nullSafeString(resultSet, "passHash")
        info.parentPasswordHash = nullSafeString(resultSet, "parentPasswordHash")
        info.passwordHash = nullSafeString(resultSet, "passwordHash")
        info.settingsPasswordHash = nullSafeString(resultSet, "settingsPasswordHash")
        info.accountPagePasswordHash = nullSafeString(resultSet, "accountPagePasswordHash")
        info.allowedStbTypesJson = nullSafeString(resultSet, "allowedStbTypesJson")
        info.allowedStbTypesForLocalRecordingJson = nullSafeString(resultSet, "allowedStbTypesForLocalRecordingJson")
        info.preferredStbType = nullSafeString(resultSet, "preferredStbType")
        return info
    }

    fun getByAccountId(accountId: String?): AccountInfo? {
        if (isBlank(accountId)) {
            return null
        }
        return getAll(" WHERE accountId=?", arrayOf(accountId!!)).firstOrNull()
    }

    fun save(info: AccountInfo?) {
        if (info == null || isBlank(info.accountId)) {
            return
        }
        if (getByAccountId(info.accountId) == null) insert(info) else update(info)
    }

    fun deleteByAccountId(accountId: String?) {
        if (isBlank(accountId)) {
            return
        }
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE.tableName} WHERE accountId=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.executeUpdate()
                }
            }
        } catch (sqlException: SQLException) {
            throw IllegalStateException("Unable to execute delete query", sqlException)
        }
    }

    private fun insert(info: AccountInfo) {
        val insertQuery = DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE)
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(insertQuery).use { statement ->
                    statement.setString(1, info.accountId)
                    statement.setString(2, info.expireDate)
                    statement.setString(3, statusToString(info.accountStatus))
                    statement.setString(4, info.accountBalance)
                    statement.setString(5, info.tariffName)
                    statement.setString(6, info.tariffPlan)
                    statement.setString(7, info.defaultTimezone)
                    statement.setString(8, info.profileJson)
                    statement.setString(9, info.passHash)
                    statement.setString(10, info.parentPasswordHash)
                    statement.setString(11, info.passwordHash)
                    statement.setString(12, info.settingsPasswordHash)
                    statement.setString(13, info.accountPagePasswordHash)
                    statement.setString(14, info.allowedStbTypesJson)
                    statement.setString(15, info.allowedStbTypesForLocalRecordingJson)
                    statement.setString(16, info.preferredStbType)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query", e)
        }
    }

    private fun update(info: AccountInfo) {
        val sql = "UPDATE ${DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE.tableName} " +
            "SET expireDate=?, accountStatus=?, accountBalance=?, tariffName=?, tariffPlan=?, defaultTimezone=?, profileJson=?," +
            " passHash=?, parentPasswordHash=?, passwordHash=?, settingsPasswordHash=?, accountPagePasswordHash=?," +
            " allowedStbTypesJson=?, allowedStbTypesForLocalRecordingJson=?, preferredStbType=? WHERE accountId=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, info.expireDate)
                    statement.setString(2, statusToString(info.accountStatus))
                    statement.setString(3, info.accountBalance)
                    statement.setString(4, info.tariffName)
                    statement.setString(5, info.tariffPlan)
                    statement.setString(6, info.defaultTimezone)
                    statement.setString(7, info.profileJson)
                    statement.setString(8, info.passHash)
                    statement.setString(9, info.parentPasswordHash)
                    statement.setString(10, info.passwordHash)
                    statement.setString(11, info.settingsPasswordHash)
                    statement.setString(12, info.accountPagePasswordHash)
                    statement.setString(13, info.allowedStbTypesJson)
                    statement.setString(14, info.allowedStbTypesForLocalRecordingJson)
                    statement.setString(15, info.preferredStbType)
                    statement.setString(16, info.accountId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to update account info", e)
        }
    }

    private fun statusToString(status: AccountStatus?): String? = status?.name
}
