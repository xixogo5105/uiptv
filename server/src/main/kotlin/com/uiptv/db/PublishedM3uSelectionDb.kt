package com.uiptv.db

import com.uiptv.model.PublishedM3uSelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class PublishedM3uSelectionDb private constructor() : ExposedCrudRepository<String, PublishedM3uSelection>() {
    companion object {
        private val instance = PublishedM3uSelectionDb()

        @JvmStatic
        fun get(): PublishedM3uSelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uSelection> = query {
        PublishedM3uSelectionTable.selectAll()
            .orderBy(PublishedM3uSelectionTable.id to SortOrder.ASC)
            .map(ResultRow::toPublishedM3uSelection)
    }

    override fun findById(id: String): PublishedM3uSelection? = query {
        id.toIntOrNull()
            ?.let { dbId -> PublishedM3uSelectionTable.selectAll().where { PublishedM3uSelectionTable.id eq dbId }.firstOrNull() }
            ?.toPublishedM3uSelection()
    }

    override fun save(entity: PublishedM3uSelection): PublishedM3uSelection {
        query {
            val insertedId = PublishedM3uSelectionTable.insert { row ->
                row[accountId] = entity.accountId.orEmpty()
            }[PublishedM3uSelectionTable.id]
            entity.dbId = insertedId.toString()
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            PublishedM3uSelectionTable.deleteWhere { PublishedM3uSelectionTable.id eq dbId }
        }
    }

    fun getAllSelections(): List<PublishedM3uSelection> = findAll()

    fun replaceSelections(accountIds: Set<String>?) {
        query {
            replaceSelectionsInTransaction(accountIds)
        }
    }

    internal fun replaceSelectionsInTransaction(accountIds: Set<String>?) {
        PublishedM3uSelectionTable.deleteAll()
        accountIds.orEmpty()
            .filter(String::isNotBlank)
            .forEach { accountId ->
                PublishedM3uSelectionTable.insert { row ->
                    row[PublishedM3uSelectionTable.accountId] = accountId
                }
            }
    }

    fun deleteByAccountId(accountId: String?) {
        if (accountId.isNullOrBlank()) {
            return
        }
        query {
            PublishedM3uSelectionTable.deleteWhere { PublishedM3uSelectionTable.accountId eq accountId }
        }
    }
}

internal object PublishedM3uSelectionTable : Table(DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId")

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toPublishedM3uSelection(): PublishedM3uSelection =
    PublishedM3uSelection().apply {
        dbId = this@toPublishedM3uSelection[PublishedM3uSelectionTable.id].toString()
        accountId = this@toPublishedM3uSelection[PublishedM3uSelectionTable.accountId]
    }
