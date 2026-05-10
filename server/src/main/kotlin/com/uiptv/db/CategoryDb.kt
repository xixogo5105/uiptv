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

class CategoryDb {
    companion object {
        private val instance = CategoryDb()

        @JvmStatic
        fun get(): CategoryDb = instance
    }

    fun getCategories(account: Account): List<Category> =
        transaction(SqlConnectionRuntime.database()) {
            CategoryTable.selectAll()
                .where {
                    (CategoryTable.accountType eq account.action.name) and
                        (CategoryTable.accountId eq account.dbId)
                }
                .orderBy(CategoryTable.id to SortOrder.ASC)
                .map(ResultRow::toBasicCategory)
        }

    fun getAllAccountCategories(accountId: String): List<Category> =
        transaction(SqlConnectionRuntime.database()) {
            CategoryTable.selectAll()
                .where { CategoryTable.accountId eq accountId }
                .orderBy(CategoryTable.id to SortOrder.ASC)
                .map(ResultRow::toBasicCategory)
        }

    fun getCategoryByDbId(dbId: String, account: Account): Category? =
        transaction(SqlConnectionRuntime.database()) {
            CategoryTable.selectAll()
                .where {
                    (CategoryTable.id eq dbId.toInt()) and
                        (CategoryTable.accountType eq account.action.name) and
                        (CategoryTable.accountId eq account.dbId)
                }
                .limit(1)
                .firstOrNull()
                ?.toBasicCategory()
        }

    fun saveAll(categories: List<Category>, account: Account) {
        transaction(SqlConnectionRuntime.database()) {
            CategoryTable.deleteWhere {
                (accountId eq account.dbId) and
                    (accountType eq account.action.name)
            }
            categories.forEach { insertRow(it, account) }
        }
    }

    fun deleteByAccount(account: Account) {
        try {
            openConnection().use { connection ->
                connection.prepareStatement(
                    "DELETE FROM ${DatabaseUtils.DbTable.CATEGORY_TABLE.tableName} WHERE accountId = ? AND accountType = ?"
                ).use { statement ->
                    statement.setString(1, account.dbId)
                    statement.setString(2, account.action.name)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            throw DatabaseAccessException("Unable to delete categories for account: ${account.accountName}", ex)
        }
    }

    fun insert(category: Category, account: Account) {
        transaction(SqlConnectionRuntime.database()) {
            insertRow(category, account)
        }
    }

    private fun insertRow(category: Category, account: Account) {
        CategoryTable.insert { row ->
            row[categoryId] = category.categoryId
            row[accountId] = account.dbId
            row[accountType] = account.action.name
            row[title] = category.title
            row[alias] = category.alias
            row[activeSub] = if (category.activeSub) 1 else 0
            row[censored] = category.censored
            row[url] = null
        }
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

private object CategoryTable : Table(DatabaseUtils.DbTable.CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val categoryId = text("categoryId").nullable()
    val accountId = text("accountId").nullable()
    val accountType = text("accountType").nullable()
    val title = text("title").nullable()
    val alias = text("alias").nullable()
    val url = text("url").nullable()
    val activeSub = integer("activeSub").nullable()
    val censored = integer("censored").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toBasicCategory(): Category =
    Category(
        dbId = this[CategoryTable.id].toString(),
        categoryId = this[CategoryTable.categoryId],
        title = this[CategoryTable.title],
        alias = this[CategoryTable.alias],
        activeSub = (this[CategoryTable.activeSub] ?: 0) == 1,
        censored = this[CategoryTable.censored] ?: 0
    )
