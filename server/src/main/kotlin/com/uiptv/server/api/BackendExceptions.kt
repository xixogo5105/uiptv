package com.uiptv.server.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

open class ApiBadRequestException(message: String) : RuntimeException(message)

class ApiRequestBodyRequiredException(message: String = "Request body is required") : ApiBadRequestException(message)

class ApiNotFoundException(message: String) : RuntimeException(message)

class ApiMethodNotAllowedException(
    message: String = "Method not allowed",
    val allowHeader: String
) : RuntimeException(message)

class ApiBadGatewayException(message: String = HttpStatusCode.BadGateway.description) : RuntimeException(message)

class BackendHttpException(
    val status: HttpStatusCode,
    message: String? = null,
    val responseBody: String? = null,
    val contentType: ContentType? = null,
    val responseHeaders: Map<String, String> = emptyMap()
) : RuntimeException(message ?: responseBody ?: status.description)
