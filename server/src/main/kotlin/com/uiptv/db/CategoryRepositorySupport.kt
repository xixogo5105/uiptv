package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Category
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

abstract class CategoryCacheTable(
    tableName: String,
    includeExtraJson: Boolean,
    includeCachedAt: Boolean
) : Table(tableName) {
    val id = integer("id").autoIncrement()
    val categoryId = text("categoryId").nullable()
    val accountId = text("accountId").nullable()
    val accountType = text("accountType").nullable()
    val title = text("title").nullable()
    val alias = text("alias").nullable()
    val url = text("url").nullable()
    val activeSub = integer("activeSub").nullable()
    val censored = integer("censored").nullable()
    val extraJson = if (includeExtraJson) text("extraJson").nullable() else null
    val cachedAt = if (includeCachedAt) long("cachedAt").nullable() else null

    override val primaryKey = PrimaryKey(id)
}

abstract class CategoryCacheRepository(
    private val table: CategoryCacheTable
) : ExposedCrudRepository<String, Category>() {
    fun getCategories(account: Account): List<Category> = query {
        table.selectAll()
            .where {
                (table.accountType eq account.action.name) and
                    (table.accountId eq account.dbId)
            }
            .orderBy(table.id to SortOrder.ASC)
            .map(table::toCategory)
    }

    fun getAllAccountCategories(accountId: String): List<Category> = query {
        table.selectAll()
            .where { table.accountId eq accountId }
            .orderBy(table.id to SortOrder.ASC)
            .map(table::toCategory)
    }

    fun getCategoryByDbId(dbId: String, account: Account): Category? = query {
        val parsedDbId = dbId.toIntOrNull() ?: return@query null
        table.selectAll()
            .where {
                (table.id eq parsedDbId) and
                    (table.accountType eq account.action.name) and
                    (table.accountId eq account.dbId)
            }
            .limit(1)
            .firstOrNull()
            ?.let(table::toCategory)
    }

    fun getById(categoryId: String): Category? = query {
        val parsedDbId = categoryId.toIntOrNull() ?: return@query null
        table.selectAll()
            .where { table.id eq parsedDbId }
            .limit(1)
            .firstOrNull()
            ?.let(table::toCategory)
    }

    fun saveAll(categories: List<Category>, account: Account) = query {
        clearAccount(account)
        val cachedAt = if (table.cachedAt != null) System.currentTimeMillis() else null
        categories.forEach { insertRow(it, account, cachedAt) }
    }

    fun deleteByAccount(account: Account) = query {
        clearAccount(account)
    }

    fun deleteByAccountId(accountId: String) = query {
        table.deleteWhere { table.accountId eq accountId }
    }

    fun insert(category: Category, account: Account) = query {
        insertRow(category, account, null)
    }

    fun isFresh(account: Account, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0 || table.cachedAt == null) {
            return false
        }
        return try {
            val latestCachedAt = query {
                table.selectAll()
                    .where {
                        (table.accountId eq account.dbId) and
                            (table.accountType eq account.action.name)
                    }
                    .orderBy(table.cachedAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(table.cachedAt)
                    ?: 0L
            }
            latestCachedAt > 0 && (System.currentTimeMillis() - latestCachedAt) <= maxAgeMs
        } catch (_: Exception) {
            false
        }
    }

    fun getAllByAccountId(accountId: String): List<Category> = query {
        table.selectAll()
            .where { table.accountId eq accountId }
            .orderBy(table.id to SortOrder.ASC)
            .map(table::toCategory)
    }

    override fun findAll(): List<Category> = query {
        table.selectAll()
            .orderBy(table.id to SortOrder.ASC)
            .map(table::toCategory)
    }

    override fun findById(id: String): Category? = getById(id)

    override fun save(entity: Category): Category {
        throw UnsupportedOperationException("Use saveAll(categoryList, account) or insert(category, account)")
    }

    override fun deleteById(id: String) {
        val parsedDbId = id.toIntOrNull() ?: return
        query {
            table.deleteWhere { table.id eq parsedDbId }
        }
    }

    private fun clearAccount(account: Account) {
        table.deleteWhere {
            (table.accountId eq account.dbId) and
                (table.accountType eq account.action.name)
        }
    }

    private fun insertRow(category: Category, account: Account, cachedAt: Long?) {
        table.insert { row ->
            row[table.categoryId] = category.categoryId
            row[table.accountId] = account.dbId
            row[table.accountType] = account.action.name
            row[table.title] = category.title
            row[table.alias] = category.alias
            row[table.activeSub] = if (category.activeSub) 1 else 0
            row[table.censored] = category.censored
            row[table.url] = null
            table.extraJson?.let { row[it] = category.extraJson }
            if (cachedAt != null) {
                row[table.cachedAt!!] = cachedAt
            }
        }
    }
}

internal fun CategoryCacheTable.toCategory(row: ResultRow): Category =
    Category(
        dbId = row[id].toString(),
        accountId = row[accountId],
        accountType = row[accountType],
        categoryId = row[categoryId],
        title = row[title],
        alias = row[alias],
        extraJson = extraJson?.let(row::get),
        activeSub = (row[activeSub] ?: 0) == 1,
        censored = row[censored] ?: 0
    )
