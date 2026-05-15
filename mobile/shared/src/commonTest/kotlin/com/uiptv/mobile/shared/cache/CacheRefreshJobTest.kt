package com.uiptv.mobile.shared.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CacheRefreshJobTest {
    @Test
    fun accountSpecificRequestsRequireAccountId() {
        assertFailsWith<IllegalArgumentException> {
            CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ACCOUNT)
        }
        assertFailsWith<IllegalArgumentException> {
            CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ACCOUNT_CACHE)
        }
    }

    @Test
    fun accountSpecificRequestsAcceptAccountId() {
        val refresh = CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ACCOUNT, accountId = 42)
        val clear = CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ACCOUNT_CACHE, accountId = 84)

        assertEquals(42, refresh.accountId)
        assertEquals(84, clear.accountId)
    }

    @Test
    fun globalRequestsDoNotRequireAccountId() {
        val refreshAll = CacheRefreshJobRequest(CacheRefreshAction.REFRESH_ALL)
        val clearAll = CacheRefreshJobRequest(CacheRefreshAction.CLEAR_ALL_CACHE)

        assertEquals(CacheRefreshAction.REFRESH_ALL, refreshAll.action)
        assertEquals(CacheRefreshAction.CLEAR_ALL_CACHE, clearAll.action)
        assertNull(refreshAll.accountId)
        assertNull(clearAll.accountId)
    }

    @Test
    fun jobStateCarriesProgressAndAccountContext() {
        val state = CacheRefreshJobState(
            jobId = "job-1",
            action = CacheRefreshAction.REFRESH_ACCOUNT,
            status = CacheRefreshJobStatus.RUNNING,
            progressPercent = 42,
            message = "Refreshing Demo",
            accountId = 7,
            updatedAtEpochSeconds = 123
        )

        assertEquals("job-1", state.jobId)
        assertEquals(CacheRefreshAction.REFRESH_ACCOUNT, state.action)
        assertEquals(CacheRefreshJobStatus.RUNNING, state.status)
        assertEquals(42, state.progressPercent)
        assertEquals("Refreshing Demo", state.message)
        assertEquals(7, state.accountId)
        assertEquals(123, state.updatedAtEpochSeconds)
    }

    @Test
    fun allJobStatusesAreRepresented() {
        assertEquals(
            listOf(
                CacheRefreshJobStatus.QUEUED,
                CacheRefreshJobStatus.RUNNING,
                CacheRefreshJobStatus.SUCCEEDED,
                CacheRefreshJobStatus.FAILED,
                CacheRefreshJobStatus.SKIPPED
            ),
            CacheRefreshJobStatus.entries
        )
    }
}
