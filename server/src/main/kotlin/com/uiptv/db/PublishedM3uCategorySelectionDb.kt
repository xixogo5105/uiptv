package com.uiptv.db

import com.uiptv.model.PublishedM3uCategorySelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class PublishedM3uCategorySelectionDb private constructor() : ExposedCrudRepository<String, PublishedM3uCategorySelection>() {
    companion object {
        private val instance = PublishedM3uCategorySelectionDb()

        @JvmStatic
        fun get(): PublishedM3uCategorySelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uCategorySelection> = query {
        PublishedM3uCategorySelectionTable.selectAll()
            .orderBy(PublishedM3uCategorySelectionTable.id to SortOrder.ASC)
            .map(ResultRow::toPublishedM3uCategorySelection)
    }

    override fun findById(id: String): PublishedM3uCategorySelection? = query {
        id.toIntOrNull()
            ?.let { dbId -> PublishedM3uCategorySelectionTable.selectAll().where { PublishedM3uCategorySelectionTable.id eq dbId }.firstOrNull() }
            ?.toPublishedM3uCategorySelection()
    }

    override fun save(entity: PublishedM3uCategorySelection): PublishedM3uCategorySelection {
        query {
            val insertedId = PublishedM3uCategorySelectionTable.insert { row ->
                row[accountId] = entity.accountId.orEmpty()
                row[categoryName] = entity.categoryName.orEmpty()
                row[selected] = entity.selected.asDbBoolean()
            }[PublishedM3uCategorySelectionTable.id]
            entity.dbId = insertedId.toString()
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            PublishedM3uCategorySelectionTable.deleteWhere { PublishedM3uCategorySelectionTable.id eq dbId }
        }
    }

    fun getAllSelections(): List<PublishedM3uCategorySelection> = findAll()

    fun replaceSelections(selections: List<PublishedM3uCategorySelection>?) {
        query {
            replaceSelectionsInTransaction(selections)
        }
    }

    internal fun replaceSelectionsInTransaction(selections: List<PublishedM3uCategorySelection>?) {
        PublishedM3uCategorySelectionTable.deleteAll()
        selections.orEmpty()
            .filter { !it.accountId.isNullOrBlank() && !it.categoryName.isNullOrBlank() }
            .forEach { selection ->
                PublishedM3uCategorySelectionTable.insert { row ->
                    row[accountId] = selection.accountId.orEmpty()
                    row[categoryName] = selection.categoryName.orEmpty()
                    row[selected] = selection.selected.asDbBoolean()
                }
            }
    }

    fun deleteByAccountId(accountId: String?) {
        if (accountId.isNullOrBlank()) {
            return
        }
        query {
            PublishedM3uCategorySelectionTable.deleteWhere { PublishedM3uCategorySelectionTable.accountId eq accountId }
        }
    }
}

internal object PublishedM3uCategorySelectionTable : Table(DatabaseUtils.DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId")
    val categoryName = text("categoryName")
    val selected = text("selected").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toPublishedM3uCategorySelection(): PublishedM3uCategorySelection =
    PublishedM3uCategorySelection(
        dbId = this[PublishedM3uCategorySelectionTable.id].toString(),
        accountId = this[PublishedM3uCategorySelectionTable.accountId],
        categoryName = this[PublishedM3uCategorySelectionTable.categoryName],
        selected = this[PublishedM3uCategorySelectionTable.selected] == "1"
    )
