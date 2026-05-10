package com.uiptv.util

import com.uiptv.db.DatabaseAccessException
import com.uiptv.server.UIptvServer
import com.uiptv.service.ConfigurationService
import java.io.IOException
import java.io.UncheckedIOException

object ServerUrlUtil {
    @JvmStatic
    fun getLocalServerUrl(): String {
        var port = "8888"
        try {
            val service = ConfigurationService.getInstance()
            val config = service.read()
            val configured = config?.serverPort
            if (!configured.isNullOrBlank()) {
                port = configured.trim()
            }
        } catch (_: DatabaseAccessException) {
        } catch (_: IllegalStateException) {
        } catch (_: Exception) {
        }
        return "http://127.0.0.1:$port"
    }

    @JvmStatic
    fun installServerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread(UIptvServer::stop))
    }

    @JvmStatic
    fun startServer() {
        try {
            UIptvServer.start()
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to start local web server", e)
        }
    }

    @JvmStatic
    fun ensureServerForWebPlayback(): Boolean {
        return try {
            UIptvServer.start()
            true
        } catch (e: IOException) {
            AppLog.addErrorLog(ServerUrlUtil::class.java, "Unable to start local web server for playback: ${e.message}")
            false
        }
    }
}
