package com.uiptv.mobile.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeyedJobsTest {
    @Test fun concurrentRequestsShareOneDownload() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val jobs = KeyedJobs(scope)
            val release = CompletableDeferred<Unit>()
            val downloads = AtomicInteger()
            val requested = (1..40).map {
                async(Dispatchers.Default) {
                    jobs.start("same-logo") { downloads.incrementAndGet(); release.await() }
                }
            }.awaitAll()
            assertTrue(requested.all { it === requested.first() })
            release.complete(Unit)
            requested.first().join()
            assertEquals(1, downloads.get())
        } finally { scope.cancel() }
    }

    @Test fun immediateCompletionDoesNotLeaveAStaleRegisteredJob() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = KeyedJobs(scope)
            var count = 0
            repeat(3) { jobs.start("logo") { count++ }.join() }
            assertEquals(3, count)
        } finally { scope.cancel() }
    }

    @Test fun cancellingOldRequestCannotRemoveItsReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val jobs = KeyedJobs(scope)
            val started = CompletableDeferred<Unit>()
            val finishOld = CompletableDeferred<Unit>()
            val old = jobs.start("logo") {
                try { started.complete(Unit); awaitCancellation() }
                finally { withContext(NonCancellable) { finishOld.await() } }
            }
            started.await()
            jobs.cancel("logo")
            val releaseNew = CompletableDeferred<Unit>()
            val replacement = jobs.start("logo") { releaseNew.await() }
            finishOld.complete(Unit)
            old.join()
            assertSame(replacement, jobs.start("logo") { error("duplicate download") })
            releaseNew.complete(Unit)
            replacement.join()
        } finally { scope.cancel() }
    }
}
