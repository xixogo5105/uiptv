package com.uiptv.server.html

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.util.Platform.getWebServerRootPath
import com.uiptv.util.ServerUtils.generateHtmlResponse
import com.uiptv.util.StringUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8

class HttpSpaHtmlServer(
    htmlFileName: String = "index.html"
) : HttpHandler {
    companion object {
        @JvmField
        val SPA_HTML_TEMPLATE: String = getWebServerRootPath() + File.separator + "index.html"
    }

    private val templatePath: String = getWebServerRootPath() + File.separator + htmlFileName

    @Throws(IOException::class)
    override fun handle(httpExchange: HttpExchange) {
        FileInputStream(templatePath).use { input ->
            generateHtmlResponse(httpExchange, StringUtils.EMPTY + IOUtils.toString(input, UTF_8))
        }
    }
}
