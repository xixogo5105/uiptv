package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.util.ServerUtils.writeJsonResponse
import org.json.JSONObject
import java.io.IOException

class HttpRemoteSyncHealthServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        if (!"GET".equals(exchange.requestMethod, true)) {
            exchange.responseHeaders.set("Allow", "GET")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        writeJsonResponse(exchange, 200, JSONObject().put("status", "ok").toString())
    }
}
