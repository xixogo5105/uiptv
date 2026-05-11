package com.uiptv.server.api.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApiDtosCoverageTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `bookmark upsert request decodes optional fields`() {
        val request = json.decodeFromString<BookmarkUpsertRequest>(
            """
            {
              "accountId":"acc-1",
              "categoryId":"cat-1",
              "mode":"itv",
              "channelId":"ch-1",
              "name":"Channel One",
              "logo":"http://img/logo.png"
            }
            """.trimIndent()
        )

        assertEquals("acc-1", request.accountId)
        assertEquals("cat-1", request.categoryId)
        assertEquals("itv", request.mode)
        assertEquals("ch-1", request.channelId)
        assertEquals("Channel One", request.name)
        assertEquals("http://img/logo.png", request.logo)
        assertNull(request.drmType)
    }
}
