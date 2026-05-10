package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.IOException

class HttpPlayerGatewayServer : HttpHandler {
    private val delegate = HttpPlayerJsonServer()

    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        delegate.handle(exchange)
    }
}
