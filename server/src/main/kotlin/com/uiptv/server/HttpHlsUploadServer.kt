package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.InMemoryHlsService
import org.apache.commons.io.IOUtils
import java.io.IOException

class HttpHlsUploadServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val method = ex.requestMethod
        val path = ex.requestURI.path
        val fileName = path.substring(path.lastIndexOf('/') + 1)
        when {
            "PUT".equals(method, true) -> {
                ex.requestBody.use { input ->
                    InMemoryHlsService.getInstance().put(fileName, IOUtils.toByteArray(input))
                }
                ex.sendResponseHeaders(200, -1)
            }
            "DELETE".equals(method, true) -> {
                InMemoryHlsService.getInstance().remove(fileName)
                ex.sendResponseHeaders(200, -1)
            }
            else -> ex.sendResponseHeaders(405, -1)
        }
    }
}
