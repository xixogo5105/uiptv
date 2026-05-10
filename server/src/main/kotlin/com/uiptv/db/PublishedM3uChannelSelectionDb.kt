package com.uiptv.db

import com.uiptv.model.PublishedM3uChannelSelection
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class PublishedM3uChannelSelectionDb private constructor() : ExposedCrudRepository<String, PublishedM3uChannelSelection>() {
    companion object {
        private val instance = PublishedM3uChannelSelectionDb()

        @JvmStatic
        fun get(): PublishedM3uChannelSelectionDb = instance
    }

    override fun findAll(): List<PublishedM3uChannelSelection> = query {
        PublishedM3uChannelSelectionTable.selectAll()
            .orderBy(PublishedM3uChannelSelectionTable.id to SortOrder.ASC)
            .map(ResultRow::toPublishedM3uChannelSelection)
    }

    override fun findById(id: String): PublishedM3uChannelSelection? = query {
        id.toIntOrNull()
            ?.let { dbId -> PublishedM3uChannelSelectionTable.selectAll().where { PublishedM3uChannelSelectionTable.id eq dbId }.firstOrNull() }
            ?.toPublishedM3uChannelSelection()
    }

    override fun save(entity: PublishedM3uChannelSelection): PublishedM3uChannelSelection {
        query {
            val insertedId = PublishedM3uChannelSelectionTable.insert { row ->
                row[accountId] = entity.accountId.orEmpty()
                row[categoryName] = entity.categoryName.orEmpty()
                row[channelId] = entity.channelId.orEmpty()
                row[selected] = entity.selected.asDbBoolean()
            }[PublishedM3uChannelSelectionTable.id]
            entity.dbId = insertedId.toString()
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            PublishedM3uChannelSelectionTable.deleteWhere { PublishedM3uChannelSelectionTable.id eq dbId }
        }
    }

    fun getAllSelections(): List<PublishedM3uChannelSelection> = findAll()

    fun replaceSelections(selections: List<PublishedM3uChannelSelection>?) {
        query {
            replaceSelectionsInTransaction(selections)
        }
    }

    internal fun replaceSelectionsInTransaction(selections: List<PublishedM3uChannelSelection>?) {
        PublishedM3uChannelSelectionTable.deleteAll()
        selections.orEmpty()
            .filter { !it.accountId.isNullOrBlank() && !it.categoryName.isNullOrBlank() && !it.channelId.isNullOrBlank() }
            .forEach { selection ->
                PublishedM3uChannelSelectionTable.insert { row ->
                    row[accountId] = selection.accountId.orEmpty()
                    row[categoryName] = selection.categoryName.orEmpty()
                    row[channelId] = selection.channelId.orEmpty()
                    row[selected] = selection.selected.asDbBoolean()
                }
            }
    }

    fun deleteByAccountId(accountId: String?) {
        if (accountId.isNullOrBlank()) {
            return
        }
        query {
            PublishedM3uChannelSelectionTable.deleteWhere { PublishedM3uChannelSelectionTable.accountId eq accountId }
        }
    }
}

internal object PublishedM3uChannelSelectionTable : Table(DatabaseUtils.DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId")
    val categoryName = text("categoryName")
    val channelId = text("channelId")
    val selected = text("selected").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toPublishedM3uChannelSelection(): PublishedM3uChannelSelection =
    PublishedM3uChannelSelection(
        dbId = this[PublishedM3uChannelSelectionTable.id].toString(),
        accountId = this[PublishedM3uChannelSelectionTable.accountId],
        categoryName = this[PublishedM3uChannelSelectionTable.categoryName],
        channelId = this[PublishedM3uChannelSelectionTable.channelId],
        selected = this[PublishedM3uChannelSelectionTable.selected] == "1"
    )
