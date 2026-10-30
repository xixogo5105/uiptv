package com.uiptv.mobile.shared.logging

import android.util.Log

/** App-owned log output is available only in an opted-in debug build. */
object AndroidAppLog {
    const val ENABLE_LOGS_EXTRA = "com.uiptv.mobile.extra.ENABLE_APP_LOGS"

    @Volatile
    private var enabled = false

    val isEnabled: Boolean
        get() = enabled

    fun configure(debugBuild: Boolean, explicitlyRequested: Boolean) {
        enabled = debugBuild && explicitlyRequested
    }

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (enabled) Log.i(tag, message)
    }

    fun w(tag: String, message: String, failure: Throwable? = null) {
        if (!enabled) return
        if (failure == null) Log.w(tag, message) else Log.w(tag, message, failure)
    }

    fun e(tag: String, message: String, failure: Throwable? = null) {
        if (!enabled) return
        if (failure == null) Log.e(tag, message) else Log.e(tag, message, failure)
    }
}
