package com.uiptv.mobile.android

import android.app.Application
import android.util.Log
import androidx.media3.common.util.Log as Media3Log
import androidx.work.Configuration
import com.uiptv.mobile.shared.logging.AndroidAppLog

class UiptvApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.ASSERT)
            .build()

    override fun onCreate() {
        super.onCreate()
        AndroidAppLog.configure(debugBuild = BuildConfig.DEBUG, explicitlyRequested = false)
        Media3Log.setLogLevel(Media3Log.LOG_LEVEL_OFF)
    }
}
