package com.uiptv.db

import com.uiptv.model.PublishedM3uSelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert

class PublishedM3uSelectionDb private constructor() : PublishedM3uRepository<String, PublishedM3uSelection>(PublishedM3uSelectionTable) {
    companion object {
        private val instance = PublishedM3uSelectionDb()

        @JvmStatic
        fun get(): PublishedM3uSelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uSelection> = readAll(ResultRow::toPublishedM3uSelection)

    override fun findById(id: String): PublishedM3uSelection? = readByNumericId(id, ResultRow::toPublishedM3uSelection)

    override fun save(entity: PublishedM3uSelection): PublishedM3uSelection {
        query {
            entity.dbId = PublishedM3uSelectionTable.insert { row ->
                row[PublishedM3uSelectionTable.accountId] = entity.accountId.orEmpty()
            }[PublishedM3uSelectionTable.id].toString()
        }
        return entity
    }

    override fun deleteById(id: String) = deleteNumericId(id)

    fun getAllSelections(): List<PublishedM3uSelection> = findAll()

    fun replaceSelections(accountIds: Set<String>?) = replaceSelectionsInTransaction(accountIds)

    internal fun replaceSelectionsInTransaction(accountIds: Set<String>?) = replaceSelections {
        accountIds.orEmpty()
            .filter(String::isNotBlank)
            .forEach { accountId ->
                PublishedM3uSelectionTable.insert { row ->
                    row[PublishedM3uSelectionTable.accountId] = accountId
                }
            }
    }

    fun deleteByAccountId(accountId: String?) = super.deleteByAccountIdInternal(accountId)
}

internal object PublishedM3uSelectionTable : PublishedM3uSelectionTableBase(
    tableName = DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName,
    includeCategoryName = false,
    includeChannelId = false
)

private fun ResultRow.toPublishedM3uSelection(): PublishedM3uSelection =
    PublishedM3uSelection().apply {
        dbId = this@toPublishedM3uSelection[PublishedM3uSelectionTable.id].toString()
        accountId = this@toPublishedM3uSelection[PublishedM3uSelectionTable.accountId]
    }
