package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Category
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SeriesCategoryDb {
    companion object {
        private val instance = SeriesCategoryDb()

        @JvmStatic
        fun get(): SeriesCategoryDb = instance
    }

    fun getCategories(account: Account): List<Category> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesCategoryTable.selectAll()
                .where {
                    (SeriesCategoryTable.accountType eq account.action.name) and
                        (SeriesCategoryTable.accountId eq account.dbId)
                }
                .orderBy(SeriesCategoryTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesCachedCategory)
        }

    fun isFresh(account: Account, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) {
            return false
        }
        return try {
            val latestCachedAt = transaction(SqlConnectionRuntime.database()) {
                SeriesCategoryTable.selectAll()
                    .where {
                        (SeriesCategoryTable.accountId eq account.dbId) and
                            (SeriesCategoryTable.accountType eq account.action.name)
                    }
                    .orderBy(SeriesCategoryTable.cachedAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(SeriesCategoryTable.cachedAt)
                    ?: 0L
            }
            latestCachedAt > 0 && (System.currentTimeMillis() - latestCachedAt) <= maxAgeMs
        } catch (_: Exception) {
            false
        }
    }

    fun saveAll(categories: List<Category>, account: Account) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesCategoryTable.deleteWhere { accountId eq account.dbId.orEmpty() }
            val cachedAt = System.currentTimeMillis()
            categories.forEach { category ->
                SeriesCategoryTable.insert { row ->
                    row[categoryId] = category.categoryId
                    row[accountId] = account.dbId
                    row[accountType] = account.action.name
                    row[title] = category.title
                    row[alias] = category.alias
                    row[url] = null
                    row[activeSub] = if (category.activeSub) 1 else 0
                    row[censored] = category.censored
                    row[extraJson] = category.extraJson
                    row[SeriesCategoryTable.cachedAt] = cachedAt
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesCategoryTable.deleteWhere { SeriesCategoryTable.accountId eq accountId }
        }
    }

    fun getById(categoryId: String): Category? =
        transaction(SqlConnectionRuntime.database()) {
            val dbId = categoryId.toIntOrNull() ?: return@transaction null
            SeriesCategoryTable.selectAll()
                .where { SeriesCategoryTable.id eq dbId }
                .limit(1)
                .firstOrNull()
                ?.toSeriesCachedCategory()
        }

    fun getAll(extendedSql: String, parameters: Array<String>): List<Category> {
        if (extendedSql.trim() == "WHERE accountId=?" && parameters.size == 1) {
            return transaction(SqlConnectionRuntime.database()) {
                SeriesCategoryTable.selectAll()
                    .where { SeriesCategoryTable.accountId eq parameters[0] }
                    .orderBy(SeriesCategoryTable.id to SortOrder.ASC)
                    .map(ResultRow::toSeriesCachedCategory)
            }
        }
        throw UnsupportedOperationException("Unsupported SeriesCategoryDb.getAll query: $extendedSql")
    }
}

private object SeriesCategoryTable : Table(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val categoryId = text("categoryId").nullable()
    val accountId = text("accountId").nullable()
    val accountType = text("accountType").nullable()
    val title = text("title").nullable()
    val alias = text("alias").nullable()
    val url = text("url").nullable()
    val activeSub = integer("activeSub").nullable()
    val censored = integer("censored").nullable()
    val extraJson = text("extraJson").nullable()
    val cachedAt = long("cachedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toSeriesCachedCategory(): Category =
    Category(
        dbId = this[SeriesCategoryTable.id].toString(),
        accountId = this[SeriesCategoryTable.accountId],
        accountType = this[SeriesCategoryTable.accountType],
        categoryId = this[SeriesCategoryTable.categoryId],
        title = this[SeriesCategoryTable.title],
        alias = this[SeriesCategoryTable.alias],
        extraJson = this[SeriesCategoryTable.extraJson],
        activeSub = (this[SeriesCategoryTable.activeSub] ?: 0) == 1,
        censored = this[SeriesCategoryTable.censored] ?: 0
    )
