package com.uiptv.db

import com.uiptv.model.Category

class SeriesCategoryDb private constructor() : CategoryCacheRepository(SeriesCategoryTable) {
    companion object {
        private val instance = SeriesCategoryDb()

        @JvmStatic
        fun get(): SeriesCategoryDb = instance
    }

    fun deleteByAccount(accountId: String) = super.deleteByAccountId(accountId)

    fun getAll(extendedSql: String, parameters: Array<String>): List<Category> {
        if (extendedSql.trim() == "WHERE accountId=?" && parameters.size == 1) {
            return getAllByAccountId(parameters[0])
        }
        throw UnsupportedOperationException("Unsupported SeriesCategoryDb.getAll query: $extendedSql")
    }
}

private object SeriesCategoryTable : CategoryCacheTable(
    tableName = DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE.tableName,
    includeExtraJson = true,
    includeCachedAt = true
)
