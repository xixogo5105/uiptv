package com.uiptv.mobile.shared.cache

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AndroidCacheRefreshScheduler(
    private val context: Context
) : CacheRefreshScheduler {
    private val preferences: SharedPreferences
        get() = context.applicationContext.getSharedPreferences(CACHE_JOB_PREFS, Context.MODE_PRIVATE)

    override suspend fun enqueue(request: CacheRefreshJobRequest): String {
        val workRequest = OneTimeWorkRequestBuilder<AndroidCacheMaintenanceWorker>()
            .setInputData(request.toWorkData())
            .addTag(actionTag(request.action))
            .build()
        val uniqueName = when (request.action) {
            CacheRefreshAction.REFRESH_ACCOUNT,
            CacheRefreshAction.CLEAR_ACCOUNT_CACHE -> "${request.action.name}-${request.accountId}"
            CacheRefreshAction.REFRESH_ALL,
            CacheRefreshAction.CLEAR_ALL_CACHE -> request.action.name
        }
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, workRequest)
        val jobId = workRequest.id.toString()
        rememberState(
            CacheRefreshJobState(
                jobId = jobId,
                action = request.action,
                status = CacheRefreshJobStatus.QUEUED,
                accountId = request.accountId,
                message = "Queued",
                updatedAtEpochSeconds = nowEpochSeconds()
            )
        )
        return jobId
    }

    override suspend fun state(jobId: String): CacheRefreshJobState? = withContext(Dispatchers.IO) {
        val uuid = runCatching { UUID.fromString(jobId) }.getOrNull() ?: return@withContext null
        val info = WorkManager.getInstance(context.applicationContext)
            .getWorkInfoById(uuid)
            .get()
            ?: return@withContext null
        val remembered = rememberedStates().firstOrNull { it.jobId == jobId }
        val action = info.tags
            .firstNotNullOfOrNull { tag -> tag.removePrefix(ACTION_TAG_PREFIX).takeIf { it != tag } }
            ?.let { runCatching { CacheRefreshAction.valueOf(it) }.getOrNull() }
            ?: remembered?.action
            ?: CacheRefreshAction.REFRESH_ALL
        val data = if (info.state == WorkInfo.State.RUNNING) info.progress else info.outputData
        val state = CacheRefreshJobState(
            jobId = jobId,
            action = action,
            status = data.getString(KEY_STATUS)?.let { runCatching { CacheRefreshJobStatus.valueOf(it) }.getOrNull() }
                ?: info.state.toCacheStatus(),
            progressPercent = data.getInt(KEY_PROGRESS, if (info.state.isFinished) 100 else 0),
            message = data.getString(KEY_MESSAGE) ?: remembered?.message ?: info.state.name.lowercase().replaceFirstChar { it.uppercase() },
            accountId = remembered?.accountId,
            updatedAtEpochSeconds = nowEpochSeconds()
        )
        rememberState(state)
        state
    }

    override suspend fun recentStates(limit: Int): List<CacheRefreshJobState> = withContext(Dispatchers.IO) {
        rememberedStates()
            .take(limit.coerceAtLeast(1))
            .map { state(it.jobId) ?: it }
            .take(limit.coerceAtLeast(1))
    }

    override suspend fun cancel(jobId: String) {
        val uuid = runCatching { UUID.fromString(jobId) }.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(context.applicationContext)
                .cancelWorkById(uuid)
                .result
                .get()
            rememberedStates().firstOrNull { it.jobId == jobId }?.let {
                rememberState(
                    it.copy(
                        status = CacheRefreshJobStatus.SKIPPED,
                        progressPercent = it.progressPercent,
                        message = "Stop requested",
                        updatedAtEpochSeconds = nowEpochSeconds()
                    )
                )
            }
        }
    }

    private fun CacheRefreshJobRequest.toWorkData(): Data =
        workDataOf(
            KEY_ACTION to action.name,
            KEY_ACCOUNT_ID to (accountId ?: -1L)
        )

    private fun actionTag(action: CacheRefreshAction): String = "$ACTION_TAG_PREFIX${action.name}"

    private fun WorkInfo.State.toCacheStatus(): CacheRefreshJobStatus =
        when (this) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> CacheRefreshJobStatus.QUEUED
            WorkInfo.State.RUNNING -> CacheRefreshJobStatus.RUNNING
            WorkInfo.State.SUCCEEDED -> CacheRefreshJobStatus.SUCCEEDED
            WorkInfo.State.FAILED -> CacheRefreshJobStatus.FAILED
            WorkInfo.State.CANCELLED -> CacheRefreshJobStatus.SKIPPED
        }

    private fun rememberState(state: CacheRefreshJobState) {
        val updated = listOf(state) + rememberedStates().filterNot { it.jobId == state.jobId }
        preferences.edit()
            .putString(KEY_RECENT_CACHE_JOBS, JSONArray(updated.take(MAX_RECENT_CACHE_JOBS).map { it.toJson() }).toString())
            .apply()
    }

    private fun rememberedStates(): List<CacheRefreshJobState> {
        val raw = preferences.getString(KEY_RECENT_CACHE_JOBS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toCacheRefreshJobState()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun CacheRefreshJobState.toJson(): JSONObject =
        JSONObject()
            .put("jobId", jobId)
            .put("action", action.name)
            .put("status", status.name)
            .put("progressPercent", progressPercent)
            .put("message", message)
            .put("accountId", accountId)
            .put("updatedAtEpochSeconds", updatedAtEpochSeconds)

    private fun JSONObject.toCacheRefreshJobState(): CacheRefreshJobState? {
        val action = runCatching { CacheRefreshAction.valueOf(optString("action")) }.getOrNull() ?: return null
        val status = runCatching { CacheRefreshJobStatus.valueOf(optString("status")) }.getOrNull() ?: return null
        return CacheRefreshJobState(
            jobId = optString("jobId"),
            action = action,
            status = status,
            progressPercent = optInt("progressPercent", 0),
            message = optString("message"),
            accountId = if (isNull("accountId")) null else optLong("accountId"),
            updatedAtEpochSeconds = optLong("updatedAtEpochSeconds", 0)
        ).takeIf { it.jobId.isNotBlank() }
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}

class AndroidCacheMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val action = runCatching {
            CacheRefreshAction.valueOf(inputData.getString(KEY_ACTION).orEmpty())
        }.getOrElse {
            return Result.failure(workDataOf(KEY_MESSAGE to "Unknown cache job action."))
        }
        val accountId = inputData.getLong(KEY_ACCOUNT_ID, -1L).takeIf { it > 0 }
        val databaseHelper = AndroidUiptvDatabaseHelper(applicationContext)
        val repository = AndroidSQLiteAccountRepository(databaseHelper)
        val m3uReloader = AndroidM3uCacheReloader(applicationContext, databaseHelper)
        val xtremeReloader = AndroidXtremeCacheReloader(databaseHelper)
        val stalkerReloader = AndroidStalkerCacheReloader(databaseHelper)
        val notifier = CacheRefreshNotifier(applicationContext, id.toString())

        setProgress(workDataOf(KEY_STATUS to CacheRefreshJobStatus.RUNNING.name, KEY_PROGRESS to 10))
        notifier.show("Cache refresh running", "Starting cache job.", ongoing = true)

        val result = when (action) {
            CacheRefreshAction.CLEAR_ACCOUNT_CACHE -> {
                if (accountId == null) {
                    Result.failure(workDataOf(KEY_MESSAGE to "Missing account id."))
                } else {
                    val summary = repository.clearAccountCache(accountId)
                    Result.success(successData("Cleared ${summary.totalItems} cached rows.", 100))
                }
            }
            CacheRefreshAction.CLEAR_ALL_CACHE -> {
                val summary = repository.clearAllCache()
                Result.success(successData("Cleared ${summary.totalItems} cached rows.", 100))
            }
            CacheRefreshAction.REFRESH_ACCOUNT -> {
                if (accountId == null) {
                    Result.failure(workDataOf(KEY_MESSAGE to "Missing account id."))
                } else {
                    val account = repository.listAccounts().firstOrNull { it.id == accountId }
                        ?: return Result.failure(workDataOf(KEY_MESSAGE to "Account not found."))
                    setProgress(
                        workDataOf(
                            KEY_STATUS to CacheRefreshJobStatus.RUNNING.name,
                            KEY_PROGRESS to 25,
                            KEY_MESSAGE to "Refreshing ${account.accountName}."
                        )
                    )
                    refreshAccount(account, m3uReloader, xtremeReloader, stalkerReloader).toWorkResult()
                }
            }
            CacheRefreshAction.REFRESH_ALL -> {
                val accounts = repository.listAccounts()
                    .filter { it.canRefreshCache }
                var refreshed = 0
                var skipped = 0
                var failed = 0
                val accountLogs = mutableListOf<String>()
                for ((index, account) in accounts.withIndex()) {
                    if (isStopped) {
                        skipped += accounts.size - index
                        accountLogs += "Stopped before ${account.accountName}."
                        break
                    }
                    setProgress(
                        workDataOf(
                            KEY_STATUS to CacheRefreshJobStatus.RUNNING.name,
                            KEY_PROGRESS to (10 + (index * 80 / accounts.size.coerceAtLeast(1))),
                            KEY_MESSAGE to "Refreshing ${account.accountName}."
                        )
                    )
                    val result = refreshAccount(account, m3uReloader, xtremeReloader, stalkerReloader)
                    accountLogs += "${account.accountName}: ${result.status.name.lowercase()} - ${result.message}"
                    when (result.status) {
                        CacheRefreshJobStatus.SUCCEEDED -> refreshed++
                        CacheRefreshJobStatus.SKIPPED -> skipped++
                        CacheRefreshJobStatus.FAILED -> failed++
                        CacheRefreshJobStatus.QUEUED,
                        CacheRefreshJobStatus.RUNNING -> Unit
                    }
                }
                Result.success(
                    workDataOf(
                        KEY_STATUS to CacheRefreshJobStatus.SUCCEEDED.name,
                        KEY_PROGRESS to 100,
                        KEY_MESSAGE to buildString {
                            append("Refresh complete: $refreshed refreshed, $skipped skipped, $failed failed.")
                            if (accountLogs.isNotEmpty()) {
                                append(" | ")
                                append(accountLogs.joinToString(" | "))
                            }
                        }
                    )
                )
            }
        }
        notifier.show(
            title = "Cache refresh ${result.outputData.getString(KEY_STATUS)?.lowercase() ?: "complete"}",
            text = result.outputData.getString(KEY_MESSAGE).orEmpty().ifBlank { "Cache job finished." },
            ongoing = false
        )
        return result
    }

    private suspend fun refreshAccount(
        account: MobileAccount,
        m3uReloader: AndroidM3uCacheReloader,
        xtremeReloader: AndroidXtremeCacheReloader,
        stalkerReloader: AndroidStalkerCacheReloader
    ): M3uRefreshResult {
        val id = account.id ?: return M3uRefreshResult.failed("Missing account id.")
        return when (account.type) {
            MobileAccountType.M3U8_URL,
            MobileAccountType.M3U8_LOCAL -> m3uReloader.refreshAccount(id)
            MobileAccountType.XTREME_API -> xtremeReloader.refreshAccount(id)
            MobileAccountType.STALKER_PORTAL -> stalkerReloader.refreshAccount(id)
            MobileAccountType.RSS_FEED ->
                M3uRefreshResult.skipped("RSS Feed cache refresh is not supported.")
        }
    }

    private fun M3uRefreshResult.toWorkResult(): Result {
        val data = workDataOf(
            KEY_STATUS to status.name,
            KEY_PROGRESS to if (status == CacheRefreshJobStatus.SUCCEEDED) 100 else 0,
            KEY_MESSAGE to message
        )
        return when (status) {
            CacheRefreshJobStatus.SUCCEEDED,
            CacheRefreshJobStatus.SKIPPED -> Result.success(data)
            CacheRefreshJobStatus.FAILED -> Result.failure(data)
            CacheRefreshJobStatus.QUEUED,
            CacheRefreshJobStatus.RUNNING -> Result.success(data)
        }
    }

    private fun successData(message: String, progress: Int): Data =
        workDataOf(
            KEY_STATUS to CacheRefreshJobStatus.SUCCEEDED.name,
            KEY_PROGRESS to progress,
            KEY_MESSAGE to message
        )
}

const val KEY_ACTION = "action"
const val KEY_ACCOUNT_ID = "account_id"
const val KEY_STATUS = "status"
const val KEY_PROGRESS = "progress"
const val KEY_MESSAGE = "message"
private const val ACTION_TAG_PREFIX = "cache-action:"
private const val CACHE_JOB_PREFS = "cache_jobs"
private const val KEY_RECENT_CACHE_JOBS = "recent_cache_jobs"
private const val MAX_RECENT_CACHE_JOBS = 10
private const val CACHE_NOTIFICATION_CHANNEL_ID = "cache_refresh"

private class CacheRefreshNotifier(
    private val context: Context,
    jobId: String
) {
    private val manager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val notificationId: Int = jobId.hashCode()

    fun show(title: String, text: String, ongoing: Boolean) {
        val notificationManager = manager ?: return
        ensureChannel(notificationManager)
        val notification = buildNotification(title, text.take(220), ongoing)
        runCatching { notificationManager.notify(notificationId, notification) }
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CACHE_NOTIFICATION_CHANNEL_ID,
            "Cache refresh",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CACHE_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()
    }
}
