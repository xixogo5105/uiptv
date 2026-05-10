package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.util.ServerUtils.readRequestBodyText
import com.uiptv.util.ServerUtils.writeJsonResponse
import org.json.JSONObject
import java.io.IOException

class HttpRemoteSyncCompleteServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        if (!"POST".equals(exchange.requestMethod, true)) {
            exchange.responseHeaders.set("Allow", "POST")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        try {
            val payload = JSONObject(readRequestBodyText(exchange))
            RemoteSyncSessionService.getInstance().completeImport(
                payload.optString("sessionId", ""),
                payload.optBoolean("success", false),
                payload.optString("message", "")
            )
            writeJsonResponse(exchange, 200, JSONObject().put("status", "ok").toString())
        } catch (ex: IllegalArgumentException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        } catch (ex: IllegalStateException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        }
    }
}
