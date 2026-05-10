package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.VodWatchStateService
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

class HttpWatchingNowVodActionServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        when {
            ex.requestMethod.equals("POST", ignoreCase = true) -> upsert(ex)
            ex.requestMethod.equals("DELETE", ignoreCase = true) -> remove(ex)
            else -> {
                ex.responseHeaders.set("Allow", "POST,DELETE")
                ex.sendResponseHeaders(405, -1)
            }
        }
    }

    private fun upsert(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val accountId = opt(body, "accountId")
        val categoryId = opt(body, "categoryId")
        val vodId = opt(body, "vodId")
        val vodName = opt(body, "vodName")
        val vodCmd = opt(body, "vodCmd")
        val vodLogo = opt(body, "vodLogo")
        if (isBlank(accountId) || isBlank(vodId)) {
            writeJson(ex, 400, """{"status":"error","message":"accountId and vodId are required"}""")
            return
        }
        val account = AccountService.getInstance().getById(accountId)
        if (account == null) {
            writeJson(ex, 404, """{"status":"error","message":"account not found"}""")
            return
        }
        val channel = Channel().apply {
            channelId = vodId
            this.categoryId = categoryId
            name = vodName
            cmd = vodCmd
            logo = vodLogo
        }
        VodWatchStateService.getInstance().save(account, categoryId, channel)
        writeJson(ex, 200, """{"status":"ok"}""")
    }

    private fun remove(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val accountId = opt(body, "accountId")
        val categoryId = opt(body, "categoryId")
        val vodId = opt(body, "vodId")
        if (isBlank(accountId) || isBlank(vodId)) {
            writeJson(ex, 400, """{"status":"error","message":"accountId and vodId are required"}""")
            return
        }
        VodWatchStateService.getInstance().remove(accountId, categoryId, vodId)
        writeJson(ex, 200, """{"status":"ok"}""")
    }

    private fun readBodyJson(ex: HttpExchange): JSONObject =
        ex.requestBody.use { input ->
            val body = String(input.readAllBytes(), StandardCharsets.UTF_8)
            if (body.isBlank()) JSONObject() else JSONObject(body)
        }

    private fun opt(body: JSONObject?, key: String): String =
        if (body == null || !body.has(key) || body.isNull(key)) "" else body.opt(key).toString().trim()

    private fun writeJson(ex: HttpExchange, status: Int, body: String) {
        val responseBytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Access-Control-Allow-Methods", "POST,DELETE,OPTIONS")
        ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type,*")
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, responseBytes.size.toLong())
        ex.responseBody.use { it.write(responseBytes) }
    }
}
