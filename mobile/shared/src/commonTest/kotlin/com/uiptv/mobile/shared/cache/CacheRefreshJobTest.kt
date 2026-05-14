package com.uiptv.mobile.shared.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CacheRefreshJobTest {
    @Test
    fun accountSpecificActionsRequireAccountId() {
        assertFailsWith<IllegalArgumentException> {
            CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ACCOUNT)
        }
        assertFailsWith<IllegalArgumentException> {
            CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ACCOUNT_CACHE)
        }
    }

    @Test
    fun allAccountActionsDoNotRequireAccountId() {
        assertEquals(CacheRefreshAction.REFRESH_ALL, CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL).action)
        assertEquals(CacheRefreshAction.CLEAR_ALL_CACHE, CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ALL_CACHE).action)
    }
}
