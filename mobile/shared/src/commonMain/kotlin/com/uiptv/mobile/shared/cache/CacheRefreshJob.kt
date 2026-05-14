package com.uiptv.mobile.shared.cache

enum class CacheRefreshAction {
    REFRESH_ACCOUNT,
    REFRESH_ALL,
    CLEAR_ACCOUNT_CACHE,
    CLEAR_ALL_CACHE
}

enum class CacheRefreshJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}

data class CacheRefreshJobRequest(
    val action: CacheRefreshAction,
    val accountId: Long? = null
) {
    init {
        if (action == CacheRefreshAction.REFRESH_ACCOUNT || action == CacheRefreshAction.CLEAR_ACCOUNT_CACHE) {
            require(accountId != null) { "accountId is required for account-specific cache jobs." }
        }
    }
}

data class CacheRefreshJobState(
    val jobId: String,
    val action: CacheRefreshAction,
    val status: CacheRefreshJobStatus,
    val progressPercent: Int = 0,
    val message: String = "",
    val accountId: Long? = null,
    val updatedAtEpochSeconds: Long = 0
)

interface CacheRefreshScheduler {
    suspend fun enqueue(request: CacheRefreshJobRequest): String

    suspend fun state(jobId: String): CacheRefreshJobState?

    suspend fun recentStates(limit: Int = 5): List<CacheRefreshJobState>

    suspend fun cancel(jobId: String)
}
