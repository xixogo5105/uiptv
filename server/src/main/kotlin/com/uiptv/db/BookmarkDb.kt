package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.BookmarkCategory
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object BookmarkDb {
    @JvmStatic
    fun get(): BookmarkDb = this

    fun getById(id: String): Bookmark? = findById(id)

    fun getBookmarks(): List<Bookmark> = getBookmarksOrdered(null)

    fun getBookmarksPage(offset: Int, limit: Int): List<Bookmark> =
        if (limit <= 0) {
            getBookmarksOrdered(null)
        } else {
            getBookmarksOrdered(null, offset.coerceAtLeast(0), limit)
        }

    fun getBookmarksByCategory(categoryId: String?): List<Bookmark> = getBookmarksOrdered(categoryId)

    fun getBookmarkById(bookmark: Bookmark): Bookmark? = query {
        BookmarkTable.selectAll()
            .where {
                (BookmarkTable.accountName eq bookmark.accountName.orEmpty()) and
                    (BookmarkTable.categoryTitle eq bookmark.categoryTitle.orEmpty()) and
                    (BookmarkTable.channelId eq bookmark.channelId.orEmpty()) and
                    (BookmarkTable.channelName eq bookmark.channelName.orEmpty())
            }
            .limit(1)
            .firstOrNull()
            ?.toBookmark()
    }

    fun save(bookmark: Bookmark): Boolean = query {
        val existing = findExisting(bookmark)
        if (existing != null) {
            bookmark.dbId = existing.dbId
            updateBookmark(bookmark)
            false
        } else {
            val insertedId = BookmarkTable.insertReturning(listOf(BookmarkTable.id)) { row -> row.write(bookmark) }
                .single()[BookmarkTable.id]
            bookmark.dbId = insertedId.toString()
            true
        }
    }

    fun delete(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            BookmarkTable.deleteWhere { BookmarkTable.id eq dbId }
            BookmarkOrderTable.deleteWhere { BookmarkOrderTable.bookmarkDbId eq id }
        }
    }

    fun delete(bookmark: Bookmark) {
        if (!bookmark.dbId.isNullOrBlank()) {
            delete(bookmark.dbId!!)
            return
        }
        deleteBookmarkByComposite(bookmark)
    }

    fun deleteByAccountName(accountName: String) {
        if (accountName.isBlank()) {
            return
        }
        query {
            val bookmarkIds = BookmarkTable.selectAll()
                .where { BookmarkTable.accountName eq accountName }
                .map { it[BookmarkTable.id].toString() }
            if (bookmarkIds.isNotEmpty()) {
                BookmarkOrderTable.deleteWhere { BookmarkOrderTable.bookmarkDbId inList bookmarkIds }
            }
            BookmarkTable.deleteWhere { BookmarkTable.accountName eq accountName }
        }
    }

    fun saveBookmarkOrder(bookmarkDbId: String, displayOrder: Int) {
        if (bookmarkDbId.isBlank()) {
            return
        }
        query {
            BookmarkOrderTable.deleteWhere { BookmarkOrderTable.bookmarkDbId eq bookmarkDbId }
            BookmarkOrderTable.insert { row ->
                row[BookmarkOrderTable.bookmarkDbId] = bookmarkDbId
                row[BookmarkOrderTable.categoryId] = null
                row[BookmarkOrderTable.displayOrder] = displayOrder
            }
        }
    }

    fun updateBookmarkOrders(bookmarkOrders: Map<String, Int>) {
        query {
            BookmarkOrderTable.deleteWhere { Op.TRUE }
            bookmarkOrders.entries
                .filter { it.key.isNotBlank() }
                .sortedBy { it.value }
                .forEach { entry ->
                    BookmarkOrderTable.insert { row ->
                        row[bookmarkDbId] = entry.key
                        row[categoryId] = null
                        row[displayOrder] = entry.value
                    }
                }
        }
    }

    fun deleteBookmarkOrder(bookmarkDbId: String, categoryId: String?) {
        if (bookmarkDbId.isBlank()) {
            return
        }
        query {
            BookmarkOrderTable.deleteWhere {
                (BookmarkOrderTable.bookmarkDbId eq bookmarkDbId) and nullableCategoryMatch(BookmarkOrderTable.categoryId, categoryId)
            }
        }
    }

    fun deleteBookmarkOrders(bookmarkDbId: String) {
        if (bookmarkDbId.isBlank()) {
            return
        }
        query {
            BookmarkOrderTable.deleteWhere { BookmarkOrderTable.bookmarkDbId eq bookmarkDbId }
        }
    }

    fun getNextDisplayOrder(): Int = query {
        val maxDisplayOrder = BookmarkOrderTable.selectAll()
            .mapNotNull { it[BookmarkOrderTable.displayOrder] }
            .maxOrNull()
            ?: 0
        maxDisplayOrder + 1
    }

    fun deleteBookmarkOrdersByCategory(categoryId: String?) {
        query {
            BookmarkOrderTable.deleteWhere { nullableCategoryMatch(BookmarkOrderTable.categoryId, categoryId) }
        }
    }

    fun saveCategory(category: BookmarkCategory) {
        query {
            val insertedId = BookmarkCategoryTable.insertReturning(listOf(BookmarkCategoryTable.id)) { row ->
                row[BookmarkCategoryTable.name] = category.name.orEmpty()
            }.single()[BookmarkCategoryTable.id]
            category.id = insertedId.toString()
        }
    }

    fun deleteCategory(category: BookmarkCategory) {
        val dbId = category.id?.toIntOrNull() ?: return
        query {
            BookmarkCategoryTable.deleteWhere { BookmarkCategoryTable.id eq dbId }
        }
    }

    fun getAllCategories(): List<BookmarkCategory> = query {
        BookmarkCategoryTable.selectAll().map {
            BookmarkCategory(
                id = it[BookmarkCategoryTable.id].toString(),
                name = it[BookmarkCategoryTable.name]
            )
        }
    }

    private fun findById(id: String): Bookmark? = query {
        id.toIntOrNull()
            ?.let { dbId -> BookmarkTable.selectAll().where { BookmarkTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toBookmark()
    }

    private fun getBookmarksOrdered(categoryId: String?, offset: Int = -1, limit: Int = -1): List<Bookmark> = query {
        val bookmarkQuery = BookmarkTable.selectAll().let { query ->
            if (categoryId == null) {
                query
            } else {
                query.where { BookmarkTable.categoryId eq categoryId }
            }
        }
        val orderByBookmarkId = BookmarkOrderTable.selectAll()
            .mapNotNull { row ->
                val bookmarkDbId = row[BookmarkOrderTable.bookmarkDbId]
                val displayOrder = row[BookmarkOrderTable.displayOrder] ?: return@mapNotNull null
                bookmarkDbId to displayOrder
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, displayOrders) -> displayOrders.minOrNull() ?: Int.MAX_VALUE }

        val ordered = bookmarkQuery
            .map(ResultRow::toBookmark)
            .sortedWith(
                compareBy<Bookmark> { orderByBookmarkId[it.dbId] == null }
                    .thenBy { orderByBookmarkId[it.dbId] ?: Int.MAX_VALUE }
                    .thenBy { it.dbId?.toIntOrNull() ?: Int.MAX_VALUE }
            )

        if (limit <= 0) {
            ordered
        } else {
            ordered.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
        }
    }

    private fun findExisting(bookmark: Bookmark): Bookmark? =
        BookmarkTable.selectAll()
            .where {
                (BookmarkTable.accountName eq bookmark.accountName.orEmpty()) and
                    (BookmarkTable.categoryTitle eq bookmark.categoryTitle.orEmpty()) and
                    (BookmarkTable.channelId eq bookmark.channelId.orEmpty()) and
                    (BookmarkTable.channelName eq bookmark.channelName.orEmpty())
            }
            .limit(1)
            .firstOrNull()
            ?.toBookmark()

    private fun updateBookmark(bookmark: Bookmark) {
        val dbId = bookmark.dbId?.toIntOrNull() ?: return
        BookmarkTable.update({ BookmarkTable.id eq dbId }) { row -> row.write(bookmark) }
    }

    private fun deleteBookmarkByComposite(bookmark: Bookmark) = query {
        val bookmarkIds = BookmarkTable.selectAll()
            .where {
                (BookmarkTable.accountName eq bookmark.accountName.orEmpty()) and
                    (BookmarkTable.categoryTitle eq bookmark.categoryTitle.orEmpty()) and
                    (BookmarkTable.channelId eq bookmark.channelId.orEmpty()) and
                    (BookmarkTable.channelName eq bookmark.channelName.orEmpty())
            }
            .map { it[BookmarkTable.id].toString() }
        if (bookmarkIds.isNotEmpty()) {
            BookmarkOrderTable.deleteWhere { BookmarkOrderTable.bookmarkDbId inList bookmarkIds }
        }
        BookmarkTable.deleteWhere {
            (BookmarkTable.accountName eq bookmark.accountName.orEmpty()) and
                (BookmarkTable.categoryTitle eq bookmark.categoryTitle.orEmpty()) and
                (BookmarkTable.channelId eq bookmark.channelId.orEmpty()) and
                (BookmarkTable.channelName eq bookmark.channelName.orEmpty())
        }
    }

    private fun <R> query(block: () -> R): R = transaction(SqlConnectionRuntime.database()) { block() }
}

private object BookmarkTable : Table(DatabaseUtils.DbTable.BOOKMARK_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountName = text("accountName").nullable()
    val categoryTitle = text("categoryTitle").nullable()
    val channelId = text("channelId").nullable()
    val channelName = text("channelName").nullable()
    val cmd = text("cmd").nullable()
    val serverPortalUrl = text("serverPortalUrl").nullable()
    val categoryId = text("categoryId").nullable()
    val accountAction = text("accountAction").nullable()
    val drmType = text("drmType").nullable()
    val drmLicenseUrl = text("drmLicenseUrl").nullable()
    val clearKeysJson = text("clearKeysJson").nullable()
    val inputstreamaddon = text("inputstreamaddon").nullable()
    val manifestType = text("manifestType").nullable()
    val categoryJson = text("categoryJson").nullable()
    val channelJson = text("channelJson").nullable()
    val vodJson = text("vodJson").nullable()
    val seriesJson = text("seriesJson").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object BookmarkOrderTable : Table(DatabaseUtils.DbTable.BOOKMARK_ORDER_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val bookmarkDbId = text("bookmark_db_id")
    val categoryId = text("category_id").nullable()
    val displayOrder = integer("display_order").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object BookmarkCategoryTable : Table(DatabaseUtils.DbTable.BOOKMARK_CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val name = text("name")

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toBookmark(): Bookmark =
    Bookmark(
        accountName = this[BookmarkTable.accountName],
        categoryTitle = this[BookmarkTable.categoryTitle],
        channelId = this[BookmarkTable.channelId],
        channelName = this[BookmarkTable.channelName],
        cmd = this[BookmarkTable.cmd],
        serverPortalUrl = this[BookmarkTable.serverPortalUrl],
        categoryId = this[BookmarkTable.categoryId]
    ).apply {
        dbId = this@toBookmark[BookmarkTable.id].toString()
        accountAction = this@toBookmark[BookmarkTable.accountAction]?.takeIf(String::isNotBlank)?.let(Account.AccountAction::valueOf)
        drmType = this@toBookmark[BookmarkTable.drmType]
        drmLicenseUrl = this@toBookmark[BookmarkTable.drmLicenseUrl]
        clearKeysJson = this@toBookmark[BookmarkTable.clearKeysJson]
        inputstreamaddon = this@toBookmark[BookmarkTable.inputstreamaddon]
        manifestType = this@toBookmark[BookmarkTable.manifestType]
        categoryJson = this@toBookmark[BookmarkTable.categoryJson]
        channelJson = this@toBookmark[BookmarkTable.channelJson]
        vodJson = this@toBookmark[BookmarkTable.vodJson]
        seriesJson = this@toBookmark[BookmarkTable.seriesJson]
    }

private fun <T : UpdateBuilder<*>> T.write(bookmark: Bookmark) {
    this[BookmarkTable.accountName] = bookmark.accountName
    this[BookmarkTable.categoryTitle] = bookmark.categoryTitle
    this[BookmarkTable.channelId] = bookmark.channelId
    this[BookmarkTable.channelName] = bookmark.channelName
    this[BookmarkTable.cmd] = bookmark.cmd
    this[BookmarkTable.serverPortalUrl] = bookmark.serverPortalUrl
    this[BookmarkTable.categoryId] = bookmark.categoryId
    this[BookmarkTable.accountAction] = bookmark.accountAction?.name
    this[BookmarkTable.drmType] = bookmark.drmType
    this[BookmarkTable.drmLicenseUrl] = bookmark.drmLicenseUrl
    this[BookmarkTable.clearKeysJson] = bookmark.clearKeysJson
    this[BookmarkTable.inputstreamaddon] = bookmark.inputstreamaddon
    this[BookmarkTable.manifestType] = bookmark.manifestType
    this[BookmarkTable.categoryJson] = bookmark.categoryJson
    this[BookmarkTable.channelJson] = bookmark.channelJson
    this[BookmarkTable.vodJson] = bookmark.vodJson
    this[BookmarkTable.seriesJson] = bookmark.seriesJson
}

private fun nullableCategoryMatch(column: org.jetbrains.exposed.sql.Column<String?>, categoryId: String?) =
    if (categoryId == null) {
        column.isNull()
    } else {
        column eq categoryId
    }
