package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.file.Files

class HttpIconServer : HttpHandler {
    companion object {
        private const val RESOURCE_ICON = "/icon.ico"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        var bytes: ByteArray? = null
        try {
            val filePath = StaticWebFileResolver.resolve(ex)
            bytes = Files.readAllBytes(filePath)
        } catch (_: IOException) {
        }

        if (bytes == null) {
            HttpIconServer::class.java.getResourceAsStream(RESOURCE_ICON)?.use { input ->
                bytes = IOUtils.toByteArray(input)
            }
        }
        if (bytes == null) {
            ex.sendResponseHeaders(404, -1)
            return
        }
        ex.responseHeaders.add("Content-Type", "image/x-icon")
        ex.sendResponseHeaders(200, bytes!!.size.toLong())
        ex.responseBody.use { os -> os.write(bytes) }
    }
}
