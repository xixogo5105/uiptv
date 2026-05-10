package com.uiptv.db

import com.uiptv.model.AccountInfo
import com.uiptv.model.AccountStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class AccountInfoDb private constructor() : ExposedCrudRepository<String, AccountInfo>() {
    companion object {
        private val instance = AccountInfoDb()

        @JvmStatic
        fun get(): AccountInfoDb = instance
    }

    override fun findAll(): List<AccountInfo> = query {
        AccountInfoTable.selectAll().map(ResultRow::toAccountInfo)
    }

    override fun findById(id: String): AccountInfo? = query {
        id.toIntOrNull()
            ?.let { dbId -> AccountInfoTable.selectAll().where { AccountInfoTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toAccountInfo()
    }

    override fun save(entity: AccountInfo): AccountInfo {
        if (entity.accountId.isNullOrBlank()) {
            return entity
        }
        query {
            val existingId = entity.dbId?.toIntOrNull() ?: findExistingId(entity.accountId.orEmpty())
            if (existingId == null) {
                val insertedId = AccountInfoTable.insert { row -> row.write(entity) }[AccountInfoTable.id]
                entity.dbId = insertedId.toString()
            } else {
                AccountInfoTable.update({ AccountInfoTable.id eq existingId }) { row -> row.write(entity) }
                entity.dbId = existingId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            AccountInfoTable.deleteWhere { AccountInfoTable.id eq dbId }
        }
    }

    fun getByAccountId(accountId: String?): AccountInfo? = query {
        if (accountId.isNullOrBlank()) {
            null
        } else {
            AccountInfoTable.selectAll()
                .where { AccountInfoTable.accountId eq accountId }
                .limit(1)
                .firstOrNull()
                ?.toAccountInfo()
        }
    }

    fun deleteByAccountId(accountId: String?) {
        if (accountId.isNullOrBlank()) {
            return
        }
        query {
            AccountInfoTable.deleteWhere { AccountInfoTable.accountId eq accountId }
        }
    }

    private fun findExistingId(accountId: String): Int? =
        AccountInfoTable.selectAll()
            .where { AccountInfoTable.accountId eq accountId }
            .limit(1)
            .firstOrNull()
            ?.get(AccountInfoTable.id)
}

private object AccountInfoTable : Table(DatabaseUtils.DbTable.ACCOUNT_INFO_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId")
    val expireDate = text("expireDate").nullable()
    val accountStatus = text("accountStatus").nullable()
    val accountBalance = text("accountBalance").nullable()
    val tariffName = text("tariffName").nullable()
    val tariffPlan = text("tariffPlan").nullable()
    val defaultTimezone = text("defaultTimezone").nullable()
    val profileJson = text("profileJson").nullable()
    val passHash = text("passHash").nullable()
    val parentPasswordHash = text("parentPasswordHash").nullable()
    val passwordHash = text("passwordHash").nullable()
    val settingsPasswordHash = text("settingsPasswordHash").nullable()
    val accountPagePasswordHash = text("accountPagePasswordHash").nullable()
    val allowedStbTypesJson = text("allowedStbTypesJson").nullable()
    val allowedStbTypesForLocalRecordingJson = text("allowedStbTypesForLocalRecordingJson").nullable()
    val preferredStbType = text("preferredStbType").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toAccountInfo(): AccountInfo = AccountInfo().apply {
    dbId = this@toAccountInfo[AccountInfoTable.id].toString()
    accountId = this@toAccountInfo[AccountInfoTable.accountId]
    expireDate = this@toAccountInfo[AccountInfoTable.expireDate]
    accountStatus = this@toAccountInfo[AccountInfoTable.accountStatus]?.let(AccountStatus::fromValue)
    accountBalance = this@toAccountInfo[AccountInfoTable.accountBalance]
    tariffName = this@toAccountInfo[AccountInfoTable.tariffName]
    tariffPlan = this@toAccountInfo[AccountInfoTable.tariffPlan]
    defaultTimezone = this@toAccountInfo[AccountInfoTable.defaultTimezone]
    profileJson = this@toAccountInfo[AccountInfoTable.profileJson]
    passHash = this@toAccountInfo[AccountInfoTable.passHash]
    parentPasswordHash = this@toAccountInfo[AccountInfoTable.parentPasswordHash]
    passwordHash = this@toAccountInfo[AccountInfoTable.passwordHash]
    settingsPasswordHash = this@toAccountInfo[AccountInfoTable.settingsPasswordHash]
    accountPagePasswordHash = this@toAccountInfo[AccountInfoTable.accountPagePasswordHash]
    allowedStbTypesJson = this@toAccountInfo[AccountInfoTable.allowedStbTypesJson]
    allowedStbTypesForLocalRecordingJson = this@toAccountInfo[AccountInfoTable.allowedStbTypesForLocalRecordingJson]
    preferredStbType = this@toAccountInfo[AccountInfoTable.preferredStbType]
}

private fun <T : UpdateBuilder<*>> T.write(info: AccountInfo) {
    this[AccountInfoTable.accountId] = info.accountId.orEmpty()
    this[AccountInfoTable.expireDate] = info.expireDate
    this[AccountInfoTable.accountStatus] = info.accountStatus?.name
    this[AccountInfoTable.accountBalance] = info.accountBalance
    this[AccountInfoTable.tariffName] = info.tariffName
    this[AccountInfoTable.tariffPlan] = info.tariffPlan
    this[AccountInfoTable.defaultTimezone] = info.defaultTimezone
    this[AccountInfoTable.profileJson] = info.profileJson
    this[AccountInfoTable.passHash] = info.passHash
    this[AccountInfoTable.parentPasswordHash] = info.parentPasswordHash
    this[AccountInfoTable.passwordHash] = info.passwordHash
    this[AccountInfoTable.settingsPasswordHash] = info.settingsPasswordHash
    this[AccountInfoTable.accountPagePasswordHash] = info.accountPagePasswordHash
    this[AccountInfoTable.allowedStbTypesJson] = info.allowedStbTypesJson
    this[AccountInfoTable.allowedStbTypesForLocalRecordingJson] = info.allowedStbTypesForLocalRecordingJson
    this[AccountInfoTable.preferredStbType] = info.preferredStbType
}
