package com.uiptv.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    private val DEFAULT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 30)
    private val CONNECT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connect.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)
    private val CONNECTION_REQUEST_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connection.request.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)
    private val RESPONSE_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.response.timeout.seconds", DEFAULT_TIMEOUT_SECONDS)

    private val redirectingClient: HttpClient by lazy {
        buildClient(true)
    }

    private val nonRedirectingClient: HttpClient by lazy {
        buildClient(false)
    }

    @JvmStatic
    fun shared(followRedirects: Boolean = true): HttpClient =
        if (followRedirects) redirectingClient else nonRedirectingClient

    private fun buildClient(followRedirects: Boolean): HttpClient =
        HttpClient(CIO) {
            expectSuccess = false
            this.followRedirects = followRedirects
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_SECONDS.toLong() * 1000L
                requestTimeoutMillis = CONNECTION_REQUEST_TIMEOUT_SECONDS.toLong() * 1000L
                socketTimeoutMillis = RESPONSE_TIMEOUT_SECONDS.toLong() * 1000L
            }
            defaultRequest {
                if (!headers.contains(HttpHeaders.Accept)) {
                    headers.append(HttpHeaders.Accept, "*/*")
                }
            }
        }
}
