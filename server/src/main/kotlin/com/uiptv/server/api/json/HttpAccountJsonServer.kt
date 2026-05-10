package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.AccountService
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.StringUtils
import java.io.IOException

class HttpAccountJsonServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(httpExchange: HttpExchange) {
        generateJsonResponse(httpExchange, StringUtils.EMPTY + AccountService.getInstance().readToJson())
    }
}
