package com.uiptv.db

import com.uiptv.model.PublishedM3uChannelSelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert

class PublishedM3uChannelSelectionDb private constructor() : PublishedM3uRepository<String, PublishedM3uChannelSelection>(PublishedM3uChannelSelectionTable) {
    companion object {
        private val instance = PublishedM3uChannelSelectionDb()

        @JvmStatic
        fun get(): PublishedM3uChannelSelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uChannelSelection> = readAll(ResultRow::toPublishedM3uChannelSelection)

    override fun findById(id: String): PublishedM3uChannelSelection? = readByNumericId(id, ResultRow::toPublishedM3uChannelSelection)

    override fun save(entity: PublishedM3uChannelSelection): PublishedM3uChannelSelection {
        query {
            entity.dbId = PublishedM3uChannelSelectionTable.insert { row ->
                row[PublishedM3uChannelSelectionTable.accountId] = entity.accountId.orEmpty()
                row[PublishedM3uChannelSelectionTable.categoryName!!] = entity.categoryName.orEmpty()
                row[PublishedM3uChannelSelectionTable.channelId!!] = entity.channelId.orEmpty()
                row[PublishedM3uChannelSelectionTable.selected!!] = entity.selected.asDbBoolean()
            }[PublishedM3uChannelSelectionTable.id].toString()
        }
        return entity
    }

    override fun deleteById(id: String) = deleteNumericId(id)

    fun getAllSelections(): List<PublishedM3uChannelSelection> = findAll()

    fun replaceSelections(selections: List<PublishedM3uChannelSelection>?) = replaceSelectionsInTransaction(selections)

    internal fun replaceSelectionsInTransaction(selections: List<PublishedM3uChannelSelection>?) = replaceSelections {
        selections.orEmpty()
            .filter { !it.accountId.isNullOrBlank() && !it.categoryName.isNullOrBlank() && !it.channelId.isNullOrBlank() }
            .forEach { selection ->
                PublishedM3uChannelSelectionTable.insert { row ->
                    row[PublishedM3uChannelSelectionTable.accountId] = selection.accountId.orEmpty()
                    row[PublishedM3uChannelSelectionTable.categoryName!!] = selection.categoryName.orEmpty()
                    row[PublishedM3uChannelSelectionTable.channelId!!] = selection.channelId.orEmpty()
                    row[PublishedM3uChannelSelectionTable.selected!!] = selection.selected.asDbBoolean()
                }
            }
    }

    fun deleteByAccountId(accountId: String?) = super.deleteByAccountIdInternal(accountId)
}

internal object PublishedM3uChannelSelectionTable : PublishedM3uSelectionTableBase(
    tableName = DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName,
    includeCategoryName = true,
    includeChannelId = true
)

private fun ResultRow.toPublishedM3uChannelSelection(): PublishedM3uChannelSelection =
    PublishedM3uChannelSelection(
        dbId = this[PublishedM3uChannelSelectionTable.id].toString(),
        accountId = this[PublishedM3uChannelSelectionTable.accountId],
        categoryName = this[PublishedM3uChannelSelectionTable.categoryName!!],
        channelId = this[PublishedM3uChannelSelectionTable.channelId!!],
        selected = this[PublishedM3uChannelSelectionTable.selected!!] == "1"
    )
