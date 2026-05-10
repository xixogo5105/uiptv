package com.uiptv.db

import com.uiptv.api.JsonCompliant
import com.uiptv.util.StringUtils.SPACE
import com.uiptv.util.StringUtils.isBlank
import java.sql.ResultSet
import java.sql.SQLException

abstract class BaseDb<T : JsonCompliant>(
    private val table: DatabaseUtils.DbTable
) {
    abstract fun populate(resultSet: ResultSet): T

    open fun getAll(extendedSql: String, parameters: Array<String>): List<T> {
        val items = ArrayList<T>()
        val sql = DatabaseUtils.selectAllSql(table) + SPACE + extendedSql
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            items += populate(resultSet)
                        }
                    }
                }
            }
        } catch (sqlException: SQLException) {
            throw IllegalStateException("Unable to execute query", sqlException)
        }
        return items
    }

    open fun getAll(): List<T> = getAll("", emptyArray())

    open fun getById(id: String): T? {
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(DatabaseUtils.selectByIdSql(table)).use { statement ->
                    statement.setString(1, id)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return populate(resultSet)
                        }
                    }
                }
            }
        } catch (sqlException: SQLException) {
            throw IllegalStateException("Unable to execute query", sqlException)
        }
        return null
    }

    open fun delete(id: String) {
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(DatabaseUtils.deleteByIdSql(table)).use { statement ->
                    statement.setString(1, id)
                    statement.executeUpdate()
                }
            }
        } catch (sqlException: SQLException) {
            throw IllegalStateException("Unable to execute delete query", sqlException)
        }
    }

    fun nullSafeString(resultSet: ResultSet, column: String): String? =
        try {
            resultSet.getString(column)
        } catch (_: SQLException) {
            null
        }

    companion object {
        @JvmStatic
        fun safeInteger(resultSet: ResultSet, column: String): Int =
            try {
                val raw = resultSet.getString(column)
                if (isBlank(raw)) 0 else raw.toInt()
            } catch (_: SQLException) {
                0
            }

        @JvmStatic
        fun safeBoolean(resultSet: ResultSet, column: String): Boolean =
            try {
                val raw = resultSet.getString(column)
                !isBlank(raw) && raw.toInt() > 0
            } catch (_: SQLException) {
                false
            }
    }
}
