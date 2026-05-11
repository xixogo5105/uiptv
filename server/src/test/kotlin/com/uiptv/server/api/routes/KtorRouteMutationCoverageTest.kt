package com.uiptv.server.api.routes

import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.server.configureServerApplication
import com.uiptv.service.AccountService
import com.uiptv.service.DbBackedTest
import com.uiptv.util.AccountType
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class KtorRouteMutationCoverageTest : DbBackedTest() {
    @Test
    fun `bookmarks lifecycle is covered through ktor core routes`() = testApplication {
        val account = saveAccount("route-bookmarks", AccountType.M3U8_URL, Account.AccountAction.itv)
        val category = saveLiveCategory(account, "route-live-cat", "Route Live")

        application { configureServerApplication() }

        val create = client.post("/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "accountId":"${account.dbId}",
                  "categoryId":"${category.dbId}",
                  "mode":"itv",
                  "channelId":"chan-1",
                  "name":"Route Channel",
                  "cmd":"http://example.test/live.m3u8"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, create.status, create.bodyAsText())
        val createJson = JSONObject(create.bodyAsText())
        assertEquals("saved", createJson.getString("action"))
        val bookmarkId = createJson.getString("bookmarkId")

        val list = client.get("/bookmarks")
        assertEquals(HttpStatusCode.OK, list.status)
        val bookmarks = JSONArray(list.bodyAsText())
        assertEquals(1, bookmarks.length())
        assertEquals("Route Channel", bookmarks.getJSONObject(0).getString("channelName"))

        val reorder = client.put("/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody("""{"orderedBookmarkDbIds":["$bookmarkId"]}""")
        }
        assertEquals(HttpStatusCode.OK, reorder.status)
        assertEquals("reordered", JSONObject(reorder.bodyAsText()).getString("action"))

        val delete = client.delete("/bookmarks?bookmarkId=$bookmarkId")
        assertEquals(HttpStatusCode.OK, delete.status)
        assertEquals("removed", JSONObject(delete.bodyAsText()).getString("action"))

        val afterDelete = client.get("/bookmarks")
        assertEquals(0, JSONArray(afterDelete.bodyAsText()).length())
    }

    @Test
    fun `channels route is covered through ktor http endpoint`() = testApplication {
        val account = saveAccount("route-channels", AccountType.M3U8_URL, Account.AccountAction.itv)
        val category = saveLiveCategory(account, "route-news", "Route News")
        val channel = Channel().apply {
            channelId = "route-1"
            name = "Route News 1"
            cmd = "http://example.test/stream.m3u8"
        }
        ChannelDb.get().saveAll(listOf(channel), category.dbId.orEmpty(), account)

        application { configureServerApplication() }

        val response = client.get("/channels?accountId=${account.dbId}&categoryId=${category.dbId}&mode=itv")
        assertEquals(HttpStatusCode.OK, response.status)
        val channels = JSONArray(response.bodyAsText())
        assertEquals(1, channels.length())
        assertEquals("route-1", channels.getJSONObject(0).getString("channelId"))
        assertEquals("Route News 1", channels.getJSONObject(0).getString("name"))
    }

    @Test
    fun `watching now series and vod mutations are covered through ktor routes`() = testApplication {
        val seriesAccount = saveAccount("route-series", AccountType.XTREME_API, Account.AccountAction.series)
        val vodAccount = saveAccount("route-vod", AccountType.XTREME_API, Account.AccountAction.vod)

        application { configureServerApplication() }

        val createSeries = client.post("/watchingNowSeriesAction") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "accountId":"${seriesAccount.dbId}",
                  "categoryId":"series-cat",
                  "seriesId":"series-1",
                  "episodeId":"ep-1",
                  "episodeName":"Episode 1",
                  "season":"1",
                  "episodeNum":"1",
                  "categoryDbId":"series-cat-db",
                  "seriesTitle":"Route Series",
                  "seriesPoster":"poster.png",
                  "episodes":[]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, createSeries.status)

        val seriesRows = client.get("/watchingNow")
        assertEquals(HttpStatusCode.OK, seriesRows.status)
        val seriesJson = JSONArray(seriesRows.bodyAsText())
        assertFalse(seriesJson.isEmpty)
        assertEquals("series-1", seriesJson.getJSONObject(0).getString("seriesId"))
        assertFalse(seriesJson.getJSONObject(0).getString("seriesTitle").isBlank())

        val deleteSeries = client.delete("/watchingNowSeriesAction") {
            contentType(ContentType.Application.Json)
            setBody("""{"accountId":"${seriesAccount.dbId}","categoryId":"series-cat","seriesId":"series-1"}""")
        }
        assertEquals(HttpStatusCode.OK, deleteSeries.status)
        assertTrue(JSONArray(client.get("/watchingNow").bodyAsText()).isEmpty)

        val createVod = client.post("/watchingNowVodAction") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "accountId":"${vodAccount.dbId}",
                  "categoryId":"vod-cat",
                  "vodId":"vod-1",
                  "vodName":"Route Vod",
                  "vodCmd":"http://example.test/vod.m3u8",
                  "vodLogo":"vod.png"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, createVod.status)

        val vodRows = client.get("/watchingNowVod")
        assertEquals(HttpStatusCode.OK, vodRows.status)
        val vodJson = JSONArray(vodRows.bodyAsText())
        assertFalse(vodJson.isEmpty)
        assertEquals("vod-1", vodJson.getJSONObject(0).getString("vodId"))
        assertEquals("Route Vod", vodJson.getJSONObject(0).getString("vodName"))

        val deleteVod = client.delete("/watchingNowVodAction") {
            contentType(ContentType.Application.Json)
            setBody("""{"accountId":"${vodAccount.dbId}","categoryId":"vod-cat","vodId":"vod-1"}""")
        }
        assertEquals(HttpStatusCode.OK, deleteVod.status)
        assertTrue(JSONArray(client.get("/watchingNowVod").bodyAsText()).isEmpty)
    }

    @Test
    fun `mutation validation and not found errors are centralized through ktor status pages`() = testApplication {
        application { configureServerApplication() }

        val bookmarkValidation = client.post("/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody("""{"channelId":"chan-1","name":"Missing Account"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bookmarkValidation.status)
        val bookmarkValidationJson = JSONObject(bookmarkValidation.bodyAsText())
        assertEquals("bad_request", bookmarkValidationJson.getString("error"))
        assertTrue(bookmarkValidationJson.getString("message").contains("accountId"))

        val bookmarkDeleteValidation = client.delete("/bookmarks") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bookmarkDeleteValidation.status)
        assertEquals("bad_request", JSONObject(bookmarkDeleteValidation.bodyAsText()).getString("error"))

        val seriesValidation = client.post("/watchingNowSeriesAction") {
            contentType(ContentType.Application.Json)
            setBody("""{"accountId":"missing"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, seriesValidation.status)
        assertEquals("bad_request", JSONObject(seriesValidation.bodyAsText()).getString("error"))

        val seriesNotFound = client.post("/watchingNowSeriesAction") {
            contentType(ContentType.Application.Json)
            setBody("""{"accountId":"missing","seriesId":"series-1","episodeId":"ep-1"}""")
        }
        assertEquals(HttpStatusCode.NotFound, seriesNotFound.status)
        assertEquals("not_found", JSONObject(seriesNotFound.bodyAsText()).getString("error"))

        val vodValidation = client.post("/watchingNowVodAction") {
            contentType(ContentType.Application.Json)
            setBody("""{"accountId":"missing"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, vodValidation.status)
        assertEquals("bad_request", JSONObject(vodValidation.bodyAsText()).getString("error"))
    }

    private fun saveAccount(name: String, type: AccountType, action: Account.AccountAction): Account {
        val account = Account(
            name,
            "user",
            "pass",
            "http://127.0.0.1/mock",
            null,
            null,
            null,
            null,
            null,
            null,
            type,
            null,
            "http://127.0.0.1/mock",
            false
        )
        account.action = action
        AccountService.getInstance().save(account)
        return AccountService.getInstance().getByName(name) ?: error("Saved account not found: $name")
    }

    private fun saveLiveCategory(account: Account, categoryId: String, title: String): Category {
        CategoryDb.get().saveAll(listOf(Category(categoryId, title, title, false, 0)), account)
        return CategoryDb.get().getCategories(account).first { it.categoryId == categoryId }
    }
}
