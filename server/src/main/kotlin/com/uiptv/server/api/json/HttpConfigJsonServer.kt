package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.ConfigurationService
import com.uiptv.util.ServerUtils.generateJsonResponse
import org.json.JSONObject
import java.io.IOException

class HttpConfigJsonServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val response = JSONObject()
        response.put("enableThumbnails", ConfigurationService.getInstance().read().enableThumbnails)
        generateJsonResponse(exchange, response.toString())
    }
}
