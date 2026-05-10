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
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.SQLException

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
            openConnection().use { connection ->
                connection.prepareStatement(
                    "SELECT MAX(cachedAt) FROM ${DatabaseUtils.DbTable.VOD_CATEGORY_TABLE.tableName} WHERE accountId=? AND accountType=?"
                ).use { statement ->
                    statement.setString(1, account.dbId)
                    statement.setString(2, account.action.name)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            val cachedAt = rs.getLong(1)
                            cachedAt > 0 && (System.currentTimeMillis() - cachedAt) <= maxAgeMs
                        } else {
                            false
                        }
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun saveAll(categories: List<Category>, account: Account) {
        try {
            deleteByAccount(account.dbId.orEmpty())
            val cachedAt = System.currentTimeMillis()
            categories.forEach { category ->
                openConnection().use { connection ->
                    connection.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE)).use { statement ->
                        statement.setString(1, category.categoryId)
                        statement.setString(2, account.dbId)
                        statement.setString(3, account.action.name)
                        statement.setString(4, category.title)
                        statement.setString(5, category.alias)
                        statement.setString(6, null)
                        statement.setInt(7, if (category.activeSub) 1 else 0)
                        statement.setInt(8, category.censored)
                        statement.setString(9, category.extraJson)
                        statement.setLong(10, cachedAt)
                        statement.execute()
                    }
                }
            }
        } catch (ex: SQLException) {
            throw DatabaseAccessException("Unable to execute insert query", ex)
        }
    }

    fun deleteByAccount(accountId: String) {
        try {
            openConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM ${DatabaseUtils.DbTable.VOD_CATEGORY_TABLE.tableName} WHERE accountId=?"
                ).use { statement ->
                    statement.setString(1, accountId)
                    statement.execute()
                }
            }
        } catch (ex: SQLException) {
            throw DatabaseAccessException("Unable to execute delete query", ex)
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

    private fun openConnection(): Connection =
        try {
            SQLConnection::class.java.getDeclaredMethod("connect").invoke(null) as Connection
        } catch (ex: InvocationTargetException) {
            val target = ex.targetException
            if (target is SQLException) {
                throw target
            }
            throw IllegalStateException(target)
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
