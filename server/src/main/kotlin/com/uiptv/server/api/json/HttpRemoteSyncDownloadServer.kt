package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.ServerUtils.writeBinaryResponse
import com.uiptv.util.ServerUtils.writeJsonResponse
import org.json.JSONObject
import java.io.IOException
import java.nio.file.Files

class HttpRemoteSyncDownloadServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        if (!"GET".equals(exchange.requestMethod, true)) {
            exchange.responseHeaders.set("Allow", "GET")
            exchange.sendResponseHeaders(405, -1)
            return
        }
        try {
            val snapshotPath = RemoteSyncSessionService.getInstance().getDownloadSnapshot(getParam(exchange, "sessionId"))
            writeBinaryResponse(exchange, 200, Files.readAllBytes(snapshotPath), "application/octet-stream")
        } catch (ex: IllegalArgumentException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        } catch (ex: IllegalStateException) {
            writeJsonResponse(exchange, 400, JSONObject().put("message", ex.message).toString())
        }
    }
}
