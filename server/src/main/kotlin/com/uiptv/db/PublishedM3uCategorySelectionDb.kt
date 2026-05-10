package com.uiptv.db

import com.uiptv.model.PublishedM3uCategorySelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert

class PublishedM3uCategorySelectionDb private constructor() : PublishedM3uRepository<String, PublishedM3uCategorySelection>(PublishedM3uCategorySelectionTable) {
    companion object {
        private val instance = PublishedM3uCategorySelectionDb()

        @JvmStatic
        fun get(): PublishedM3uCategorySelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uCategorySelection> = readAll(ResultRow::toPublishedM3uCategorySelection)

    override fun findById(id: String): PublishedM3uCategorySelection? = readByNumericId(id, ResultRow::toPublishedM3uCategorySelection)

    override fun save(entity: PublishedM3uCategorySelection): PublishedM3uCategorySelection {
        query {
            entity.dbId = PublishedM3uCategorySelectionTable.insert { row ->
                row[PublishedM3uCategorySelectionTable.accountId] = entity.accountId.orEmpty()
                row[PublishedM3uCategorySelectionTable.categoryName!!] = entity.categoryName.orEmpty()
                row[PublishedM3uCategorySelectionTable.selected!!] = entity.selected.asDbBoolean()
            }[PublishedM3uCategorySelectionTable.id].toString()
        }
        return entity
    }

    override fun deleteById(id: String) = deleteNumericId(id)

    fun getAllSelections(): List<PublishedM3uCategorySelection> = findAll()

    fun replaceSelections(selections: List<PublishedM3uCategorySelection>?) = replaceSelectionsInTransaction(selections)

    internal fun replaceSelectionsInTransaction(selections: List<PublishedM3uCategorySelection>?) = replaceSelections {
        selections.orEmpty()
            .filter { !it.accountId.isNullOrBlank() && !it.categoryName.isNullOrBlank() }
            .forEach { selection ->
                PublishedM3uCategorySelectionTable.insert { row ->
                    row[PublishedM3uCategorySelectionTable.accountId] = selection.accountId.orEmpty()
                    row[PublishedM3uCategorySelectionTable.categoryName!!] = selection.categoryName.orEmpty()
                    row[PublishedM3uCategorySelectionTable.selected!!] = selection.selected.asDbBoolean()
                }
            }
    }

    fun deleteByAccountId(accountId: String?) = super.deleteByAccountIdInternal(accountId)
}

internal object PublishedM3uCategorySelectionTable : PublishedM3uSelectionTableBase(
    tableName = DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName,
    includeCategoryName = true,
    includeChannelId = false
)

private fun ResultRow.toPublishedM3uCategorySelection(): PublishedM3uCategorySelection =
    PublishedM3uCategorySelection(
        dbId = this[PublishedM3uCategorySelectionTable.id].toString(),
        accountId = this[PublishedM3uCategorySelectionTable.accountId],
        categoryName = this[PublishedM3uCategorySelectionTable.categoryName!!],
        selected = this[PublishedM3uCategorySelectionTable.selected!!] == "1"
    )
