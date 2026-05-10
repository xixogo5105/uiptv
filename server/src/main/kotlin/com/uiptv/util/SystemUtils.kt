package com.uiptv.util

import java.io.IOException
import java.util.Locale

object SystemUtils {
    @JvmField
    val IS_OS_WINDOWS: Boolean

    @JvmField
    val IS_OS_LINUX: Boolean

    @JvmField
    val IS_OS_MAC_OSX: Boolean

    init {
        var isWindows = false
        var isLinux = false
        var isMacOsx = false
        try {
            var osName = System.getProperty("os.name") ?: throw IOException("os.name not found")
            osName = osName.lowercase(Locale.ENGLISH)
            when {
                osName.contains("windows") -> isWindows = true
                osName.contains("linux") ||
                    osName.contains("mpe/ix") ||
                    osName.contains("freebsd") ||
                    osName.contains("openbsd") ||
                    osName.contains("irix") ||
                    osName.contains("digital unix") ||
                    osName.contains("unix") -> isLinux = true
                osName.contains("mac os x") -> isMacOsx = true
            }
        } catch (_: Exception) {
        }
        IS_OS_WINDOWS = isWindows
        IS_OS_LINUX = isLinux
        IS_OS_MAC_OSX = isMacOsx
    }
}
