package com.uiptv.db

class CategoryDb private constructor() : CategoryCacheRepository(CategoryTable) {
    companion object {
        private val instance = CategoryDb()

        @JvmStatic
        fun get(): CategoryDb = instance
    }
}

internal object CategoryTable : CategoryCacheTable(
    tableName = DatabaseUtils.DbTable.CATEGORY_TABLE.tableName,
    includeExtraJson = false,
    includeCachedAt = false
)
