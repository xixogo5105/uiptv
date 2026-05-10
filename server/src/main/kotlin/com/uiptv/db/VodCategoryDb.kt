package com.uiptv.db

class VodCategoryDb private constructor() : CategoryCacheRepository(VodCategoryTable) {
    companion object {
        private val instance = VodCategoryDb()

        @JvmStatic
        fun get(): VodCategoryDb = instance
    }

    fun deleteByAccount(accountId: String) = super.deleteByAccountId(accountId)
}

private object VodCategoryTable : CategoryCacheTable(
    tableName = DatabaseUtils.DbTable.VOD_CATEGORY_TABLE.tableName,
    includeExtraJson = true,
    includeCachedAt = true
)
