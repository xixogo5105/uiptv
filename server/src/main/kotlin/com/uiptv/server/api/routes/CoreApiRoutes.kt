package com.uiptv.server.api.routes

import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.BookmarkCategory
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.db.CategoryDb
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.CategoryResolver
import com.uiptv.service.CategoryService
import com.uiptv.service.ConfigurationService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.json.JSONObject
import org.json.JSONArray

fun Route.registerCoreApiRoutes(
    configurationService: ConfigurationService,
    accountService: AccountService,
    categoryService: CategoryService,
    bookmarkService: BookmarkService
) {
    get("/config") {
        val configuration = configurationService.read()
        call.respondText(
            JSONObject()
                .put("enableThumbnails", configuration.enableThumbnails)
                .toString(),
            ContentType.Application.Json
        )
    }

    get("/accounts") {
        val body = accountService.readToJson()
        call.respondText(body, ContentType.Application.Json)
    }

    get("/categories") {
        val account = accountService.getById(call.request.queryParameters["accountId"])
        if (account == null) {
            call.respondText("[]", ContentType.Application.Json)
            return@get
        }
        applyMode(account, call.request.queryParameters["mode"])
        val body = com.uiptv.util.ServerUtils.objectToJson(
            CategoryResolver().resolveCategories(account, categoryService.get(account))
        )
        call.respondText(body, ContentType.Application.Json)
    }

    options("/bookmarks") {
        call.bookmarkHeaders()
        call.respondText("", status = HttpStatusCode.NoContent)
    }

    get("/bookmarks") {
        call.bookmarkHeaders()
        if ("categories".equals(call.request.queryParameters["view"], true)) {
            call.respondText(
                com.uiptv.util.ServerUtils.objectToJson(bookmarkService.getAllCategories()),
                ContentType.Application.Json
            )
            return@get
        }
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 0
        val body = if (limit > 0) bookmarkService.readToJson(offset, limit) else bookmarkService.readToJson()
        call.respondText(body, ContentType.Application.Json)
    }

    post("/bookmarks") {
        call.bookmarkHeaders()
        val body = parseJsonBody(call)
        val accountId = opt(body, "accountId", call.request.queryParameters["accountId"])
        val categoryId = opt(body, "categoryId", call.request.queryParameters["categoryId"])
        val mode = opt(body, "mode", call.request.queryParameters["mode"])
        var channelId = opt(body, "channelId", call.request.queryParameters["channelId"])
        val channelName = opt(body, "name", call.request.queryParameters["name"])
        val cmd = opt(body, "cmd", call.request.queryParameters["cmd"])
        if (channelId.isBlank()) {
            channelId = opt(body, "id", "")
        }

        val account = accountService.getById(accountId)
        if (account == null || channelId.isBlank() || channelName.isBlank()) {
            call.respondText("""{"status":"error","message":"Missing account/channel details"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@post
        }
        applyMode(account, mode)

        var categoryTitle = ""
        if (categoryId.isNotBlank()) {
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

        val portal = if (account.serverPortalUrl.isNullOrBlank()) account.url else account.serverPortalUrl
        val bookmark = Bookmark(account.accountName, categoryTitle, channelId, channelName, cmd, portal, categoryId)
        bookmark.accountAction = account.action
        bookmark.setFromChannel(channel)
        bookmark.channelJson = channel.toJson()

        val existing = bookmarkService.getBookmark(bookmark)
        if (existing != null) {
            call.respondText(
                """{"status":"ok","action":"exists","bookmarkId":"${escape(existing.dbId)}"}""",
                ContentType.Application.Json
            )
            return@post
        }

        bookmarkService.save(bookmark)
        val saved = bookmarkService.getBookmark(bookmark)
        call.respondText(
            """{"status":"ok","action":"saved","bookmarkId":"${escape(saved?.dbId ?: "")}"}""",
            ContentType.Application.Json
        )
    }

    put("/bookmarks") {
        call.bookmarkHeaders()
        val body = parseJsonBody(call)
        val bookmarkOrders = extractBookmarkOrders(body)
        if (bookmarkOrders.isEmpty()) {
            call.respondText("""{"status":"error","message":"bookmarkOrders is required"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@put
        }
        bookmarkService.saveBookmarkOrders(bookmarkOrders)
        call.respondText("""{"status":"ok","action":"reordered"}""", ContentType.Application.Json)
    }

    delete("/bookmarks") {
        call.bookmarkHeaders()
        var bookmarkId = call.request.queryParameters["bookmarkId"].orEmpty()
        if (bookmarkId.isBlank()) {
            bookmarkId = opt(parseJsonBody(call), "bookmarkId", "")
        }
        if (bookmarkId.isBlank()) {
            call.respondText("""{"status":"error","message":"bookmarkId is required"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return@delete
        }
        bookmarkService.remove(bookmarkId)
        call.respondText("""{"status":"ok","action":"removed"}""", ContentType.Application.Json)
    }
}

private fun applyMode(account: Account, mode: String?) {
    if (mode.isNullOrBlank()) {
        return
    }
    account.action = try {
        Account.AccountAction.valueOf(mode.lowercase())
    } catch (_: Exception) {
        Account.AccountAction.itv
    }
}

private suspend fun parseJsonBody(call: io.ktor.server.application.ApplicationCall): JSONObject =
    try {
        val text = call.receiveText()
        if (text.isBlank()) JSONObject() else JSONObject(text)
    } catch (_: Exception) {
        JSONObject()
    }

private fun io.ktor.server.application.ApplicationCall.bookmarkHeaders() {
    response.headers.append("Access-Control-Allow-Origin", "*")
    response.headers.append("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
    response.headers.append("Access-Control-Allow-Headers", "Content-Type,*")
    response.headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}

private fun opt(json: JSONObject?, key: String, fallback: String?): String {
    if (json != null && json.has(key) && !json.isNull(key)) {
        val value = json.optString(key, fallback)
        if (!value.isNullOrBlank()) {
            return value
        }
    }
    return fallback ?: ""
}

private fun extractBookmarkOrders(body: JSONObject?): MutableMap<String, Int> {
    val bookmarkOrders = linkedMapOf<String, Int>()
    if (body != null && body.has("bookmarkOrders") && !body.isNull("bookmarkOrders")) {
        val ordersObject = body.optJSONObject("bookmarkOrders")
        if (ordersObject != null) {
            ordersObject.keySet().forEach { bookmarkId ->
                if (bookmarkId.isNotBlank()) {
                    val orderNumber = ordersObject.optInt(bookmarkId, -1)
                    if (orderNumber > 0) {
                        bookmarkOrders[bookmarkId] = orderNumber
                    }
                }
            }
        }
    }
    if (bookmarkOrders.isNotEmpty()) {
        return bookmarkOrders
    }
    val idsArray = when {
        body?.has("orderedBookmarkDbIds") == true && !body.isNull("orderedBookmarkDbIds") -> body.optJSONArray("orderedBookmarkDbIds")
        body?.has("bookmarkIds") == true && !body.isNull("bookmarkIds") -> body.optJSONArray("bookmarkIds")
        else -> null
    } ?: return bookmarkOrders
    for (i in 0 until idsArray.length()) {
        val id = idsArray.opt(i).toString()
        if (id.isNotBlank() && !"null".equals(id, true)) {
            bookmarkOrders[id] = i + 1
        }
    }
    return bookmarkOrders
}

private fun escape(value: String?): String =
    value?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: ""
