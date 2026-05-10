package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.util.ServerUtils.generateJavascriptResponse
import com.uiptv.util.StringUtils
import java.io.IOException

class HttpJavascriptServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        try {
            val filePath = StaticWebFileResolver.resolve(ex)
            generateJavascriptResponse(ex, StringUtils.EMPTY + StaticWebFileResolver.readUtf8(filePath), filePath.fileName.toString())
        } catch (_: IOException) {
            ex.sendResponseHeaders(404, -1)
        }
    }
}
