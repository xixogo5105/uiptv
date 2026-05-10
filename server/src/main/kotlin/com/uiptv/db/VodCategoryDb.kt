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

class VodCategoryDb {
    companion object {
        private val instance = VodCategoryDb()

        @JvmStatic
        fun get(): VodCategoryDb = instance
    }

    fun getCategories(account: Account): List<Category> =
        transaction(SqlConnectionRuntime.database()) {
            VodCategoryTable.selectAll()
                .where {
                    (VodCategoryTable.accountType eq account.action.name) and
                        (VodCategoryTable.accountId eq account.dbId)
                }
                .orderBy(VodCategoryTable.id to SortOrder.ASC)
                .map(ResultRow::toCachedCategory)
        }

    fun isFresh(account: Account, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) {
            return false
        }
        return try {
            val latestCachedAt = transaction(SqlConnectionRuntime.database()) {
                VodCategoryTable.selectAll()
                    .where {
                        (VodCategoryTable.accountId eq account.dbId) and
                            (VodCategoryTable.accountType eq account.action.name)
                    }
                    .orderBy(VodCategoryTable.cachedAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(VodCategoryTable.cachedAt)
                    ?: 0L
            }
            latestCachedAt > 0 && (System.currentTimeMillis() - latestCachedAt) <= maxAgeMs
        } catch (_: Exception) {
            false
        }
    }

    fun saveAll(categories: List<Category>, account: Account) {
        transaction(SqlConnectionRuntime.database()) {
            VodCategoryTable.deleteWhere { accountId eq account.dbId.orEmpty() }
            val cachedAt = System.currentTimeMillis()
            categories.forEach { category ->
                VodCategoryTable.insert { row ->
                    row[categoryId] = category.categoryId
                    row[accountId] = account.dbId
                    row[accountType] = account.action.name
                    row[title] = category.title
                    row[alias] = category.alias
                    row[url] = null
                    row[activeSub] = if (category.activeSub) 1 else 0
                    row[censored] = category.censored
                    row[extraJson] = category.extraJson
                    row[VodCategoryTable.cachedAt] = cachedAt
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            VodCategoryTable.deleteWhere { VodCategoryTable.accountId eq accountId }
        }
    }

    fun getById(categoryId: String): Category? =
        transaction(SqlConnectionRuntime.database()) {
            val dbId = categoryId.toIntOrNull() ?: return@transaction null
            VodCategoryTable.selectAll()
                .where { VodCategoryTable.id eq dbId }
                .limit(1)
                .firstOrNull()
                ?.toCachedCategory()
        }
}

private object VodCategoryTable : Table(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE.tableName) {
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

private fun ResultRow.toCachedCategory(): Category =
    Category(
        dbId = this[VodCategoryTable.id].toString(),
        accountId = this[VodCategoryTable.accountId],
        accountType = this[VodCategoryTable.accountType],
        categoryId = this[VodCategoryTable.categoryId],
        title = this[VodCategoryTable.title],
        alias = this[VodCategoryTable.alias],
        extraJson = this[VodCategoryTable.extraJson],
        activeSub = (this[VodCategoryTable.activeSub] ?: 0) == 1,
        censored = this[VodCategoryTable.censored] ?: 0
    )
