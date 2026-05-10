package com.uiptv.server.api.routes

import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.db.CategoryDb
import com.uiptv.server.api.dto.BookmarkDeleteRequest
import com.uiptv.server.api.dto.BookmarkOrderRequest
import com.uiptv.server.api.dto.BookmarkUpsertRequest
import com.uiptv.server.api.dto.ConfigResponse
import com.uiptv.server.api.dto.StatusResponse
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.CategoryResolver
import com.uiptv.service.CategoryService
import com.uiptv.service.ConfigurationService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun Route.registerCoreApiRoutes(
    configurationService: ConfigurationService,
    accountService: AccountService,
    categoryService: CategoryService,
    bookmarkService: BookmarkService
) {
    get("/config") {
        val configuration = configurationService.read()
        call.respond(ConfigResponse(enableThumbnails = configuration.enableThumbnails))
    }

    get("/accounts") {
        call.respondJsonString(accountService.readToJson())
    }

    get("/categories") {
        val account = accountService.getById(call.request.queryParameters["accountId"])
        if (account == null) {
            call.respond(emptyList<JsonElement>())
            return@get
        }
        applyMode(account, call.request.queryParameters["mode"])
        call.respondJsonString(
            com.uiptv.util.ServerUtils.objectToJson(
                CategoryResolver().resolveCategories(account, categoryService.get(account))
            )
        )
    }

    options("/bookmarks") {
        call.bookmarkHeaders()
        call.respondText("", status = HttpStatusCode.NoContent)
    }

    get("/bookmarks") {
        call.bookmarkHeaders()
        if ("categories".equals(call.request.queryParameters["view"], true)) {
            call.respondJsonString(com.uiptv.util.ServerUtils.objectToJson(bookmarkService.getAllCategories()))
            return@get
        }
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 0
        val body = if (limit > 0) bookmarkService.readToJson(offset, limit) else bookmarkService.readToJson()
        call.respondJsonString(body)
    }

    post("/bookmarks") {
        call.bookmarkHeaders()
        val body = call.receivePayloadOrDefault<BookmarkUpsertRequest>()
        val accountId = body.accountId ?: call.request.queryParameters["accountId"]
        val categoryId = body.categoryId ?: call.request.queryParameters["categoryId"]
        val mode = body.mode ?: call.request.queryParameters["mode"]
        var channelId = body.channelId ?: call.request.queryParameters["channelId"].orEmpty()
        val channelName = body.name ?: call.request.queryParameters["name"].orEmpty()
        val cmd = body.cmd ?: call.request.queryParameters["cmd"].orEmpty()
        if (channelId.isBlank()) {
            channelId = body.id.orEmpty()
        }

        val account = accountService.getById(accountId)
        if (account == null || channelId.isBlank() || channelName.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                StatusResponse(status = "error", message = "Missing account/channel details")
            )
            return@post
        }
        applyMode(account, mode)

        var categoryTitle = ""
        if (!categoryId.isNullOrBlank()) {
            val category: Category? = CategoryDb.get().getCategoryByDbId(categoryId, account)
            if (category != null) {
                categoryTitle = category.title ?: ""
            }
        }

        val channel = Channel().apply {
            this.channelId = channelId
            name = channelName
            this.cmd = cmd
            logo = body.logo.orEmpty()
            drmType = body.drmType.orEmpty()
            drmLicenseUrl = body.drmLicenseUrl.orEmpty()
            clearKeysJson = body.clearKeysJson.orEmpty()
            inputstreamaddon = body.inputstreamaddon.orEmpty()
            manifestType = body.manifestType.orEmpty()
        }

        val portal = if (account.serverPortalUrl.isNullOrBlank()) account.url else account.serverPortalUrl
        val bookmark = Bookmark(account.accountName, categoryTitle, channelId, channelName, cmd, portal, categoryId)
        bookmark.accountAction = account.action
        bookmark.setFromChannel(channel)
        bookmark.channelJson = channel.toJson()

        val existing = bookmarkService.getBookmark(bookmark)
        if (existing != null) {
            call.respond(
                StatusResponse(
                    status = "ok",
                    action = "exists",
                    bookmarkId = existing.dbId
                )
            )
            return@post
        }

        bookmarkService.save(bookmark)
        val saved = bookmarkService.getBookmark(bookmark)
        call.respond(
            StatusResponse(
                status = "ok",
                action = "saved",
                bookmarkId = saved?.dbId.orEmpty()
            )
        )
    }

    put("/bookmarks") {
        call.bookmarkHeaders()
        val body = call.receivePayloadOrDefault<BookmarkOrderRequest>()
        val bookmarkOrders = extractBookmarkOrders(body)
        if (bookmarkOrders.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                StatusResponse(status = "error", message = "bookmarkOrders is required")
            )
            return@put
        }
        bookmarkService.saveBookmarkOrders(bookmarkOrders)
        call.respond(StatusResponse(status = "ok", action = "reordered"))
    }

    delete("/bookmarks") {
        call.bookmarkHeaders()
        var bookmarkId = call.request.queryParameters["bookmarkId"].orEmpty()
        if (bookmarkId.isBlank()) {
            bookmarkId = call.receivePayloadOrDefault<BookmarkDeleteRequest>().bookmarkId.orEmpty()
        }
        if (bookmarkId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                StatusResponse(status = "error", message = "bookmarkId is required")
            )
            return@delete
        }
        bookmarkService.remove(bookmarkId)
        call.respond(StatusResponse(status = "ok", action = "removed"))
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

private fun ApplicationCall.bookmarkHeaders() {
    response.headers.append("Access-Control-Allow-Origin", "*")
    response.headers.append("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
    response.headers.append("Access-Control-Allow-Headers", "Content-Type,*")
    response.headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}

private suspend inline fun <reified T> ApplicationCall.receivePayloadOrDefault(): T {
    val text = try {
        receiveText()
    } catch (_: Exception) {
        ""
    }
    return routeJson.decodeFromString(if (text.isBlank()) emptyJsonObject else text)
}

private suspend fun ApplicationCall.respondJsonString(body: String) {
    respond(routeJson.parseToJsonElement(body))
}

private fun extractBookmarkOrders(body: BookmarkOrderRequest): MutableMap<String, Int> {
    val bookmarkOrders = linkedMapOf<String, Int>()
    body.bookmarkOrders?.forEach { (bookmarkId, orderNumber) ->
        if (bookmarkId.isNotBlank() && orderNumber > 0) {
            bookmarkOrders[bookmarkId] = orderNumber
        }
    }
    if (bookmarkOrders.isNotEmpty()) {
        return bookmarkOrders
    }
    val orderedIds = body.orderedBookmarkDbIds ?: body.bookmarkIds ?: return bookmarkOrders
    orderedIds.forEachIndexed { index, id ->
        if (id.isNotBlank() && !id.equals("null", true)) {
            bookmarkOrders[id] = index + 1
        }
    }
    return bookmarkOrders
}

private val routeJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val emptyJsonObject = "{}"
