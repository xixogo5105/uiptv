package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.CategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.ServerUtils.objectToJson
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class HttpBookmarksJsonServer : HttpHandler {
    companion object {
        private const val ALLOWED_METHODS = "GET,POST,PUT,DELETE,OPTIONS"
        private const val PARAM_BOOKMARK_IDS = "bookmarkIds"
        private const val PARAM_BOOKMARK_ORDERS = "bookmarkOrders"
        private const val PARAM_CATEGORY_ID = "categoryId"
        private const val PARAM_ORDERED_BOOKMARK_DB_IDS = "orderedBookmarkDbIds"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val method = ex.requestMethod
        when {
            "OPTIONS".equals(method, true) -> {
                ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
                ex.responseHeaders.add("Access-Control-Allow-Methods", ALLOWED_METHODS)
                ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type,*")
                ex.sendResponseHeaders(204, -1)
            }
            "GET".equals(method, true) -> {
                if ("categories".equals(queryParam(ex, "view"), true)) {
                    generateJsonResponse(ex, objectToJson(BookmarkService.getInstance().getAllCategories()))
                    return
                }
                val offset = parseIntParam(queryParam(ex, "offset"), 0)
                val limit = parseIntParam(queryParam(ex, "limit"), 0)
                if (limit > 0) {
                    generateJsonResponse(ex, BookmarkService.getInstance().readToJson(offset, limit))
                } else {
                    generateJsonResponse(ex, BookmarkService.getInstance().readToJson())
                }
            }
            "POST".equals(method, true) -> upsertBookmark(ex)
            "PUT".equals(method, true) -> updateBookmarkOrder(ex)
            "DELETE".equals(method, true) -> deleteBookmark(ex)
            else -> {
                ex.responseHeaders["Allow"] = ALLOWED_METHODS
                ex.sendResponseHeaders(405, -1)
            }
        }
    }

    private fun updateBookmarkOrder(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val bookmarkOrders = extractBookmarkOrders(body)
        if (bookmarkOrders.isEmpty()) {
            writeJson(ex, 400, """{"status":"error","message":"bookmarkOrders is required"}""")
            return
        }
        BookmarkService.getInstance().saveBookmarkOrders(bookmarkOrders)
        writeJson(ex, 200, """{"status":"ok","action":"reordered"}""")
    }

    private fun extractBookmarkOrders(body: JSONObject?): MutableMap<String, Int> {
        val bookmarkOrders = extractExplicitBookmarkOrders(body)
        if (bookmarkOrders.isNotEmpty()) {
            return bookmarkOrders
        }
        val orderedDbIds = extractOrderedBookmarkIds(body)
        orderedDbIds.forEachIndexed { index, id -> bookmarkOrders[id] = index + 1 }
        return bookmarkOrders
    }

    private fun extractExplicitBookmarkOrders(body: JSONObject?): MutableMap<String, Int> {
        val bookmarkOrders = linkedMapOf<String, Int>()
        if (body == null || !body.has(PARAM_BOOKMARK_ORDERS) || body.isNull(PARAM_BOOKMARK_ORDERS)) {
            return bookmarkOrders
        }
        val ordersObject = body.optJSONObject(PARAM_BOOKMARK_ORDERS) ?: return bookmarkOrders
        ordersObject.keySet().forEach { bookmarkId ->
            if (!isBlank(bookmarkId)) {
                val orderNumber = ordersObject.optInt(bookmarkId, -1)
                if (orderNumber > 0) {
                    bookmarkOrders[bookmarkId] = orderNumber
                }
            }
        }
        return bookmarkOrders
    }

    private fun extractOrderedBookmarkIds(body: JSONObject?): List<String> {
        val orderedDbIds = ArrayList<String>()
        val idsArray = extractOrderedBookmarkIdArray(body) ?: return orderedDbIds
        for (i in 0 until idsArray.length()) {
            val id = idsArray.opt(i).toString()
            if (!isBlank(id) && !"null".equals(id, true)) {
                orderedDbIds += id
            }
        }
        return orderedDbIds
    }

    private fun extractOrderedBookmarkIdArray(body: JSONObject?): JSONArray? {
        if (body == null) return null
        if (body.has(PARAM_ORDERED_BOOKMARK_DB_IDS) && !body.isNull(PARAM_ORDERED_BOOKMARK_DB_IDS)) {
            return body.optJSONArray(PARAM_ORDERED_BOOKMARK_DB_IDS)
        }
        if (body.has(PARAM_BOOKMARK_IDS) && !body.isNull(PARAM_BOOKMARK_IDS)) {
            return body.optJSONArray(PARAM_BOOKMARK_IDS)
        }
        return null
    }

    private fun upsertBookmark(ex: HttpExchange) {
        val body = readBodyJson(ex)
        val accountId = opt(body, "accountId", queryParam(ex, "accountId"))
        val categoryId = opt(body, PARAM_CATEGORY_ID, queryParam(ex, PARAM_CATEGORY_ID))
        val mode = opt(body, "mode", queryParam(ex, "mode"))
        var channelId = opt(body, "channelId", queryParam(ex, "channelId"))
        val channelName = opt(body, "name", queryParam(ex, "name"))
        val cmd = opt(body, "cmd", queryParam(ex, "cmd"))
        if (isBlank(channelId) && !isBlank(opt(body, "id", ""))) {
            channelId = opt(body, "id", "")
        }

        val account = AccountService.getInstance().getById(accountId)
        if (account == null || isBlank(channelId) || isBlank(channelName)) {
            writeJson(ex, 400, """{"status":"error","message":"Missing account/channel details"}""")
            return
        }
        applyMode(account, mode)

        var categoryTitle = ""
        if (!isBlank(categoryId)) {
            val category: Category? = CategoryDb.get().getCategoryByDbId(categoryId, account)
            if (category != null) {
                categoryTitle = category.title ?: ""
            }
        }

        val channel = Channel().apply {
            this.channelId = channelId
            name = channelName
            this.cmd = cmd
            logo = opt(body, "logo", "")
            drmType = opt(body, "drmType", "")
            drmLicenseUrl = opt(body, "drmLicenseUrl", "")
            clearKeysJson = opt(body, "clearKeysJson", "")
            inputstreamaddon = opt(body, "inputstreamaddon", "")
            manifestType = opt(body, "manifestType", "")
        }

        val portal = if (isBlank(account.serverPortalUrl)) account.url else account.serverPortalUrl
        val bookmark = Bookmark(account.accountName, categoryTitle, channelId, channelName, cmd, portal, categoryId)
        bookmark.accountAction = account.action
        bookmark.setFromChannel(channel)
        bookmark.channelJson = channel.toJson()

        val existing = BookmarkService.getInstance().getBookmark(bookmark)
        if (existing != null) {
            writeJson(ex, 200, """{"status":"ok","action":"exists","bookmarkId":"${escape(existing.dbId)}"}""")
            return
        }

        BookmarkService.getInstance().save(bookmark)
        val saved = BookmarkService.getInstance().getBookmark(bookmark)
        writeJson(ex, 200, """{"status":"ok","action":"saved","bookmarkId":"${escape(saved?.dbId ?: "")}"}""")
    }

    private fun deleteBookmark(ex: HttpExchange) {
        var bookmarkId = queryParam(ex, "bookmarkId")
        if (isBlank(bookmarkId)) {
            bookmarkId = opt(readBodyJson(ex), "bookmarkId", "")
        }
        if (isBlank(bookmarkId)) {
            writeJson(ex, 400, """{"status":"error","message":"bookmarkId is required"}""")
            return
        }
        BookmarkService.getInstance().remove(bookmarkId)
        writeJson(ex, 200, """{"status":"ok","action":"removed"}""")
    }

    private fun readBodyJson(ex: HttpExchange): JSONObject =
        try {
            ex.requestBody.use { input: InputStream ->
                val data = input.readAllBytes()
                if (data.isEmpty()) JSONObject() else JSONObject(String(data, StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
            JSONObject()
        }

    private fun opt(json: JSONObject?, key: String, fallback: String?): String {
        if (json != null && json.has(key) && !json.isNull(key)) {
            val value = json.optString(key, fallback)
            if (!isBlank(value)) {
                return value
            }
        }
        return fallback ?: ""
    }

    private fun applyMode(account: Account?, mode: String?) {
        if (account == null || isBlank(mode)) return
        try {
            account.action = Account.AccountAction.valueOf(mode!!.lowercase())
        } catch (_: Exception) {
            account.action = Account.AccountAction.itv
        }
    }

    private fun writeJson(ex: HttpExchange, status: Int, body: String) {
        val responseBytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Access-Control-Allow-Methods", ALLOWED_METHODS)
        ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type,*")
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, responseBytes.size.toLong())
        ex.responseBody.use { it.write(responseBytes) }
    }

    private fun escape(value: String?): String =
        value?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""

    private fun queryParam(ex: HttpExchange, key: String): String =
        try {
            getParam(ex, key) ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun parseIntParam(value: String, fallback: Int): Int =
        try {
            value.toInt()
        } catch (_: Exception) {
            fallback
        }
}
