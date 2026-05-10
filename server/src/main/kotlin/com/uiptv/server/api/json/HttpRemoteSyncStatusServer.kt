package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.remotesync.RemoteSyncJson
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.ServerUtils.writeJsonResponse
import org.json.JSONObject
import java.io.IOException

class HttpRemoteSyncStatusServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        if (!"GET".equals(exchange.requestMethod, true)) {
            exchange.responseHeaders.set("Allow", "GET")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        try {
            writeJsonResponse(
                exchange,
                200,
                RemoteSyncJson.toJson(RemoteSyncSessionService.getInstance().getSessionState(getParam(exchange, "sessionId"))).toString()
            )
        } catch (ex: IllegalArgumentException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        } catch (ex: IllegalStateException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        }
    }
}
