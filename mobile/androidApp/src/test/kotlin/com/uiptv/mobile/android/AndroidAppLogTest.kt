package com.uiptv.mobile.android

import com.uiptv.mobile.shared.logging.AndroidAppLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAppLogTest {
    @Test
    fun appLogsRequireBothDebugBuildAndExplicitLaunchOptIn() {
        try {
            AndroidAppLog.configure(debugBuild = false, explicitlyRequested = true)
            assertFalse(AndroidAppLog.isEnabled)

            AndroidAppLog.configure(debugBuild = true, explicitlyRequested = false)
            assertFalse(AndroidAppLog.isEnabled)

            AndroidAppLog.configure(debugBuild = true, explicitlyRequested = true)
            assertTrue(AndroidAppLog.isEnabled)
        } finally {
            AndroidAppLog.configure(debugBuild = false, explicitlyRequested = false)
        }
    }
}
