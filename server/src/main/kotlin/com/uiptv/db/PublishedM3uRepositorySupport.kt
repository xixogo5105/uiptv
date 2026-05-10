package com.uiptv.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

abstract class PublishedM3uSelectionTableBase(
    tableName: String,
    includeCategoryName: Boolean,
    includeChannelId: Boolean
) : Table(tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId")
    val categoryName = if (includeCategoryName) text("categoryName") else null
    val channelId = if (includeChannelId) text("channelId") else null
    val selected = if (includeCategoryName || includeChannelId) text("selected").nullable() else null

    override val primaryKey = PrimaryKey(id)
}

abstract class PublishedM3uRepository<ID, T>(
    private val table: PublishedM3uSelectionTableBase
) : ExposedCrudRepository<ID, T>() {
    protected fun <R> readAll(mapper: (ResultRow) -> R): List<R> = query {
        table.selectAll()
            .orderBy(table.id to SortOrder.ASC)
            .map(mapper)
    }

    protected fun <R> readByNumericId(id: String, mapper: (ResultRow) -> R): R? = query {
        id.toIntOrNull()
            ?.let { dbId -> table.selectAll().where { table.id eq dbId }.firstOrNull() }
            ?.let(mapper)
    }

    protected fun deleteNumericId(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            table.deleteWhere { table.id eq dbId }
        }
    }

    protected fun deleteAllSelections() = query {
        table.deleteAll()
    }

    protected fun deleteByAccountIdInternal(accountId: String?) {
        if (accountId.isNullOrBlank()) return
        query {
            table.deleteWhere { table.accountId eq accountId }
        }
    }

    protected fun replaceSelections(replaceBlock: () -> Unit) = query {
        table.deleteAll()
        replaceBlock()
    }
}
