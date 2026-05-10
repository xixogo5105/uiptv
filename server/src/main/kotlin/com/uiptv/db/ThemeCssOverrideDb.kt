package com.uiptv.db

import com.uiptv.model.ThemeCssOverride
import java.sql.ResultSet
import java.sql.SQLException

class ThemeCssOverrideDb : BaseDb<ThemeCssOverride>(DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE) {
    companion object {
        private val instance = ThemeCssOverrideDb()

        @JvmStatic
        fun get(): ThemeCssOverrideDb = instance
    }

    override fun populate(resultSet: ResultSet): ThemeCssOverride {
        val override = ThemeCssOverride()
        override.dbId = nullSafeString(resultSet, "id")
        override.lightThemeCssName = nullSafeString(resultSet, "lightThemeCssName")
        override.lightThemeCssContent = nullSafeString(resultSet, "lightThemeCssContent")
        override.darkThemeCssName = nullSafeString(resultSet, "darkThemeCssName")
        override.darkThemeCssContent = nullSafeString(resultSet, "darkThemeCssContent")
        override.updatedAt = nullSafeString(resultSet, "updatedAt")
        return override
    }

    fun read(): ThemeCssOverride = super.getAll().firstOrNull() ?: ThemeCssOverride()

    fun save(override: ThemeCssOverride?) {
        val sanitized = override ?: ThemeCssOverride()
        if (sanitized.updatedAt.isNullOrBlank()) {
            sanitized.updatedAt = System.currentTimeMillis().toString()
        }
        val existing = super.getAll()
        if (existing.isNotEmpty()) {
            val current = existing.first()
            try {
                SQLConnection.connect().use { conn ->
                    conn.prepareStatement(DatabaseUtils.updateTableSql(DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE)).use { statement ->
                        setParameters(statement, sanitized)
                        statement.setString(6, current.dbId)
                        statement.execute()
                    }
                }
            } catch (e: SQLException) {
                throw DatabaseAccessException("Unable to execute update query", e)
            }
            return
        }
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE)).use { statement ->
                    setParameters(statement, sanitized)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute insert query", e)
        }
    }

    private fun setParameters(statement: java.sql.PreparedStatement, override: ThemeCssOverride) {
        statement.setString(1, override.lightThemeCssName)
        statement.setString(2, override.lightThemeCssContent)
        statement.setString(3, override.darkThemeCssName)
        statement.setString(4, override.darkThemeCssContent)
        statement.setString(5, override.updatedAt)
    }
}
