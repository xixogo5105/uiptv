package com.uiptv.db

import java.sql.SQLException

internal object DatabaseRetrySupport {
    fun isBusy(exception: SQLException): Boolean {
        var current: SQLException? = exception
        while (current != null) {
            val message = current.message
            if (current.errorCode == 5 || (message != null && message.contains("SQLITE_BUSY"))) {
                return true
            }
            current = current.nextException
        }
        return false
    }

    fun sleepBeforeRetry(delayMs: Long) {
        try {
            Thread.sleep(delayMs)
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DatabaseAccessException(
                "Interrupted while waiting to retry database initialization",
                interruptedException
            )
        }
    }
}
