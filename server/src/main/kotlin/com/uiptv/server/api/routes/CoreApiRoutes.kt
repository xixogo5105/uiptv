package com.uiptv.server.api.routes

import com.uiptv.server.api.ApiBadRequestException
import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.Category
import com.uiptv.model.BookmarkCategory
import com.uiptv.model.Channel
import com.uiptv.db.CategoryDb
import com.uiptv.server.api.ApiNotFoundException
import com.uiptv.server.api.dto.AccountRowDto
import com.uiptv.server.api.dto.BookmarkDeleteRequest
import com.uiptv.server.api.dto.BookmarkCategoryDto
import com.uiptv.server.api.dto.BookmarkDto
import com.uiptv.server.api.dto.BookmarkOrderRequest
import com.uiptv.server.api.dto.BookmarkUpsertRequest
import com.uiptv.server.api.dto.CategoryDto
import com.uiptv.server.api.dto.ConfigResponse
import com.uiptv.server.api.dto.StatusResponse
import com.uiptv.service.AccountService
import com.uiptv.service.AccountResolver
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.uiptv.util.json.parseJsonObject
import com.uiptv.util.json.optString

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
        call.respond(AccountResolver().resolveAccounts().map(::toAccountRowDto))
    }

    get("/categories") {
        val account = accountService.getById(call.request.queryParameters["accountId"])
        if (account == null) {
            call.respond(emptyList<CategoryDto>())
            return@get
        }
        applyMode(account, call.request.queryParameters["mode"])
        call.respond(CategoryResolver().resolveCategories(account, categoryService.get(account)).map(::toCategoryDto))
    }

    options("/bookmarks") {
        call.bookmarkHeaders()
        call.respondText("", status = HttpStatusCode.NoContent)
    }

    get("/bookmarks") {
        call.bookmarkHeaders()
        if ("categories".equals(call.request.queryParameters["view"], true)) {
            call.respond(bookmarkService.getAllCategories().map(::toBookmarkCategoryDto))
            return@get
        }
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 0
        val bookmarks = if (limit > 0) bookmarkService.read(offset, limit) else bookmarkService.read()
        val resolved = com.uiptv.service.BookmarkResolver().resolveBookmarks(bookmarks)
        call.respond(resolved.mapNotNull { it.bookmark }.map(::toBookmarkDto))
    }

    post("/bookmarks") {
        call.bookmarkHeaders()
        val rawBody = call.receiveTextSafely()
        val payload = parseJsonObject(rawBody)
        val accountId = payload.optStringAny("accountId") ?: call.request.queryParameters["accountId"]
        val categoryId = payload.optStringAny("categoryId") ?: call.request.queryParameters["categoryId"]
        val mode = payload.optStringAny("mode") ?: call.request.queryParameters["mode"]
        var channelId = payload.optStringAny("channelId") ?: call.request.queryParameters["channelId"].orEmpty()
        val channelName = payload.optStringAny("name")
            ?: payload.optStringAny("channelName")
            ?: call.request.queryParameters["name"].orEmpty()
        val cmd = payload.optStringAny("cmd") ?: call.request.queryParameters["cmd"].orEmpty()
        if (channelId.isBlank()) {
            channelId = payload.optStringAny("id").orEmpty()
        }

        if (accountId.isNullOrBlank() || channelId.isBlank() || channelName.isBlank()) {
            throw ApiBadRequestException("accountId, channelId and name are required")
        }
        val account = accountService.getById(accountId)
            ?: throw ApiNotFoundException("account not found")
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
            logo = payload.optStringAny("logo").orEmpty()
            drmType = payload.optStringAny("drmType").orEmpty()
            drmLicenseUrl = payload.optStringAny("drmLicenseUrl").orEmpty()
            clearKeysJson = payload.optStringAny("clearKeysJson").orEmpty()
            inputstreamaddon = payload.optStringAny("inputstreamaddon").orEmpty()
            manifestType = payload.optStringAny("manifestType").orEmpty()
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
            throw ApiBadRequestException("bookmarkOrders is required")
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
            throw ApiBadRequestException("bookmarkId is required")
        }
        bookmarkService.remove(bookmarkId)
        call.respond(StatusResponse(status = "ok", action = "removed"))
    }
}

private fun applyMode(account: Account, mode: String?) {
    if (mode.isNullOrBlank()) {
        return
    }
    account.action = Account.AccountAction.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
        ?: Account.AccountAction.itv
}

private fun ApplicationCall.bookmarkHeaders() {
    response.headers.append("Access-Control-Allow-Origin", "*")
    response.headers.append("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
    response.headers.append("Access-Control-Allow-Headers", "Content-Type,*")
    response.headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}

private suspend inline fun <reified T> ApplicationCall.receivePayloadOrDefault(): T {
    val text = receiveTextSafely()
    return routeJson.decodeFromString(if (text.isBlank()) emptyJsonObject else text)
}

private suspend fun ApplicationCall.receiveTextSafely(): String =
    try {
        receiveText()
    } catch (_: Exception) {
        ""
    }

private fun JsonObject?.optStringAny(key: String): String? =
    this?.optString(key)?.takeIf { it.isNotBlank() }

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

private fun toAccountRowDto(row: AccountResolver.AccountRow): AccountRowDto =
    AccountRowDto(
        accountName = row.accountName,
        dbId = row.dbId,
        type = row.type,
        pinToTop = row.pinToTop,
        pinSvgStemPath = row.pinSvgStemPath,
        pinSvgHeadPath = row.pinSvgHeadPath,
        pinSvgStemFill = row.pinSvgStemFill,
        pinSvgHeadFill = row.pinSvgHeadFill,
        pinSvgViewBox = row.pinSvgViewBox,
        pinSvgScale = row.pinSvgScale
    )

private fun toCategoryDto(category: Category): CategoryDto =
    CategoryDto(
        dbId = category.dbId,
        accountId = category.accountId,
        accountType = category.accountType,
        categoryId = category.categoryId,
        title = category.title,
        alias = category.alias,
        extraJson = category.extraJson,
        activeSub = category.activeSub,
        censored = category.censored
    )

private fun toBookmarkCategoryDto(category: BookmarkCategory): BookmarkCategoryDto =
    BookmarkCategoryDto(
        id = category.id,
        name = category.name
    )

private fun toBookmarkDto(bookmark: Bookmark): BookmarkDto =
    BookmarkDto(
        dbId = bookmark.dbId,
        accountName = bookmark.accountName,
        categoryTitle = bookmark.categoryTitle,
        channelId = bookmark.channelId,
        channelName = bookmark.channelName,
        logo = bookmark.logo,
        cmd = bookmark.cmd,
        serverPortalUrl = bookmark.serverPortalUrl,
        categoryId = bookmark.categoryId,
        accountAction = bookmark.accountAction?.name,
        drmType = bookmark.drmType,
        drmLicenseUrl = bookmark.drmLicenseUrl,
        clearKeysJson = bookmark.clearKeysJson,
        inputstreamaddon = bookmark.inputstreamaddon,
        manifestType = bookmark.manifestType,
        categoryJson = bookmark.categoryJson,
        channelJson = bookmark.channelJson,
        vodJson = bookmark.vodJson,
        seriesJson = bookmark.seriesJson
    )
