package com.uiptv.mobile.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Register before starting, so concurrent callers share one job even if it completes immediately. */
internal class KeyedJobs(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()

    fun start(key: String, action: suspend () -> Unit): Job = synchronized(jobs) {
        jobs[key]?.takeUnless { it.isCompleted } ?: scope.launch(start = CoroutineStart.LAZY) {
            try {
                action()
            } finally {
                synchronized(jobs) { jobs.remove(key, coroutineContext[Job]) }
            }
        }.also { job -> jobs[key] = job; job.start() }
    }

    fun cancel(key: String) = synchronized(jobs) { jobs.remove(key)?.cancel() }

    fun cancelAll() = synchronized(jobs) {
        val active = jobs.values.toList()
        jobs.clear()
        active.forEach { it.cancel() }
    }
}
