package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.remotesync.RemoteSyncJson
import com.uiptv.service.remotesync.RemoteSyncRequest
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.util.ServerUtils.readRequestBodyText
import com.uiptv.util.ServerUtils.writeJsonResponse
import org.json.JSONObject
import java.io.IOException

class HttpRemoteSyncRequestServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        if (!"POST".equals(exchange.requestMethod, true)) {
            exchange.responseHeaders.set("Allow", "POST")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        try {
            val request: RemoteSyncRequest = RemoteSyncJson.toRequest(JSONObject(readRequestBodyText(exchange)))
            val requesterAddress = exchange.remoteAddress?.address?.hostAddress ?: ""
            writeJsonResponse(
                exchange,
                200,
                RemoteSyncJson.toJson(RemoteSyncSessionService.getInstance().createSession(request, requesterAddress)).toString()
            )
        } catch (ex: IllegalArgumentException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        }
    }
}
