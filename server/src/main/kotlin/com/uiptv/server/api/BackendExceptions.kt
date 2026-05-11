package com.uiptv.server.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

class ApiNotFoundException(message: String) : RuntimeException(message)

class BackendHttpException(
    val status: HttpStatusCode,
    message: String? = null,
    val responseBody: String? = null,
    val contentType: ContentType? = null
) : RuntimeException(message ?: responseBody ?: status.description)
