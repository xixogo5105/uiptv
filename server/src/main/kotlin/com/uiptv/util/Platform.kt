package com.uiptv.util

import java.io.File

object Platform {
    @JvmStatic
    fun executeCommand(command: String, args: String) {
        try {
            CmdLineRunner().exec("$command $args")
        } catch (e: CmdLineRunner.CmdLineException) {
            AppLog.addErrorLog(Platform::class.java, "Error occurred while executing commands. Error Description: ${e.message}")
        }
    }

    @JvmStatic
    fun getUserHomeDirPath(): String {
        return if (SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC_OSX) {
            System.getProperty("user.home") + File.separator + "uiptv"
        } else {
            System.getProperty("user.home") + File.separator + ".config" + File.separator + "uiptv"
        }
    }

    @JvmStatic
    fun getWebServerRootPath(): String {
        val baseDir = System.getProperty("user.dir")
        val webDir = File(baseDir, "web")
        if (webDir.exists()) {
            return webDir.absolutePath
        }
        val resourcesWebDir = File(baseDir, "src${File.separator}main${File.separator}resources${File.separator}web")
        if (resourcesWebDir.exists()) {
            return resourcesWebDir.absolutePath
        }
        val serverResourcesWebDir = File(baseDir, "server${File.separator}src${File.separator}main${File.separator}resources${File.separator}web")
        if (serverResourcesWebDir.exists()) {
            return serverResourcesWebDir.absolutePath
        }
        val serverWebDir = File(baseDir, "server${File.separator}web")
        if (serverWebDir.exists()) {
            return serverWebDir.absolutePath
        }
        return resourcesWebDir.absolutePath
    }
}
