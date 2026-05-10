package com.uiptv.db

import com.uiptv.model.ThemeCssOverride
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class ThemeCssOverrideDb private constructor() : ExposedCrudRepository<String, ThemeCssOverride>() {
    companion object {
        private val instance = ThemeCssOverrideDb()

        @JvmStatic
        fun get(): ThemeCssOverrideDb = instance
    }

    override fun findAll(): List<ThemeCssOverride> = query {
        ThemeCssOverrideTable.selectAll().map(ResultRow::toThemeCssOverride)
    }

    override fun findById(id: String): ThemeCssOverride? = query {
        id.toIntOrNull()
            ?.let { dbId -> ThemeCssOverrideTable.selectAll().where { ThemeCssOverrideTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toThemeCssOverride()
    }

    override fun save(entity: ThemeCssOverride): ThemeCssOverride {
        if (entity.updatedAt.isNullOrBlank()) {
            entity.updatedAt = System.currentTimeMillis().toString()
        }
        query {
            val existingId = entity.dbId?.toIntOrNull()
                ?: ThemeCssOverrideTable.selectAll().limit(1).firstOrNull()?.get(ThemeCssOverrideTable.id)
            if (existingId == null) {
                val insertedId = ThemeCssOverrideTable.insert { row -> row.write(entity) }[ThemeCssOverrideTable.id]
                entity.dbId = insertedId.toString()
            } else {
                ThemeCssOverrideTable.update({ ThemeCssOverrideTable.id eq existingId }) { row -> row.write(entity) }
                entity.dbId = existingId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        // No current caller needs delete semantics for this single-row table.
    }

    fun read(): ThemeCssOverride = findAll().firstOrNull() ?: ThemeCssOverride()
}

private object ThemeCssOverrideTable : Table(DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val lightThemeCssName = text("lightThemeCssName").nullable()
    val lightThemeCssContent = text("lightThemeCssContent").nullable()
    val darkThemeCssName = text("darkThemeCssName").nullable()
    val darkThemeCssContent = text("darkThemeCssContent").nullable()
    val updatedAt = text("updatedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toThemeCssOverride(): ThemeCssOverride =
    ThemeCssOverride(
        dbId = this[ThemeCssOverrideTable.id].toString(),
        lightThemeCssName = this[ThemeCssOverrideTable.lightThemeCssName],
        lightThemeCssContent = this[ThemeCssOverrideTable.lightThemeCssContent],
        darkThemeCssName = this[ThemeCssOverrideTable.darkThemeCssName],
        darkThemeCssContent = this[ThemeCssOverrideTable.darkThemeCssContent],
        updatedAt = this[ThemeCssOverrideTable.updatedAt]
    )

private fun <T : UpdateBuilder<*>> T.write(override: ThemeCssOverride) {
    this[ThemeCssOverrideTable.lightThemeCssName] = override.lightThemeCssName
    this[ThemeCssOverrideTable.lightThemeCssContent] = override.lightThemeCssContent
    this[ThemeCssOverrideTable.darkThemeCssName] = override.darkThemeCssName
    this[ThemeCssOverrideTable.darkThemeCssContent] = override.darkThemeCssContent
    this[ThemeCssOverrideTable.updatedAt] = override.updatedAt
}
