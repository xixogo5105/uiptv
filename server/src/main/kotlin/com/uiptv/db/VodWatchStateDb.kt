package com.uiptv.db

import com.uiptv.model.VodWatchState
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class VodWatchStateDb private constructor() {
    companion object {
        private val instance = VodWatchStateDb()

        @JvmStatic
        fun get(): VodWatchStateDb = instance
    }

    fun getByVod(accountId: String, categoryId: String, vodId: String): VodWatchState? =
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.selectAll()
                .where {
                    (VodWatchStateTable.accountId eq accountId) and
                        (VodWatchStateTable.categoryId eq categoryId) and
                        (VodWatchStateTable.vodId eq vodId)
                }
                .limit(1)
                .firstOrNull()
                ?.toVodWatchState()
        }

    fun getByVod(accountId: String, vodId: String): List<VodWatchState> =
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.selectAll()
                .where {
                    (VodWatchStateTable.accountId eq accountId) and
                        (VodWatchStateTable.vodId eq vodId)
                }
                .orderBy(VodWatchStateTable.id to SortOrder.ASC)
                .map(ResultRow::toVodWatchState)
        }

    fun getByAccount(accountId: String): List<VodWatchState> =
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.selectAll()
                .where { VodWatchStateTable.accountId eq accountId }
                .orderBy(VodWatchStateTable.id to SortOrder.ASC)
                .map(ResultRow::toVodWatchState)
        }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.deleteWhere { this.accountId eq accountId }
        }
    }

    fun upsert(state: VodWatchState) {
        transaction(SqlConnectionRuntime.database()) {
            val updated = VodWatchStateTable.update({
                (VodWatchStateTable.accountId eq state.accountId) and
                    (VodWatchStateTable.categoryId eq state.categoryId) and
                    (VodWatchStateTable.vodId eq state.vodId)
            }) { row ->
                row[vodName] = state.vodName
                row[vodCmd] = state.vodCmd
                row[vodLogo] = state.vodLogo
                row[updatedAt] = state.updatedAt
            }
            if (updated == 0) {
                VodWatchStateTable.insert { row ->
                    row[accountId] = state.accountId
                    row[categoryId] = state.categoryId
                    row[vodId] = state.vodId
                    row[vodName] = state.vodName
                    row[vodCmd] = state.vodCmd
                    row[vodLogo] = state.vodLogo
                    row[updatedAt] = state.updatedAt
                }
            }
        }
    }

    fun clear(accountId: String, categoryId: String, vodId: String) {
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.deleteWhere {
                (this.accountId eq accountId) and
                    (this.categoryId eq categoryId) and
                    (this.vodId eq vodId)
            }
        }
    }

    fun clearAll() {
        transaction(SqlConnectionRuntime.database()) {
            VodWatchStateTable.deleteAll()
        }
    }
}

private object VodWatchStateTable : Table(DatabaseUtils.DbTable.VOD_WATCH_STATE_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()
    val categoryId = text("categoryId").nullable()
    val vodId = text("vodId").nullable()
    val vodName = text("vodName").nullable()
    val vodCmd = text("vodCmd").nullable()
    val vodLogo = text("vodLogo").nullable()
    val updatedAt = long("updatedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toVodWatchState(): VodWatchState =
    VodWatchState(
        dbId = this[VodWatchStateTable.id].toString(),
        accountId = this[VodWatchStateTable.accountId],
        categoryId = this[VodWatchStateTable.categoryId],
        vodId = this[VodWatchStateTable.vodId],
        vodName = this[VodWatchStateTable.vodName],
        vodCmd = this[VodWatchStateTable.vodCmd],
        vodLogo = this[VodWatchStateTable.vodLogo],
        updatedAt = this[VodWatchStateTable.updatedAt] ?: 0L
    )
