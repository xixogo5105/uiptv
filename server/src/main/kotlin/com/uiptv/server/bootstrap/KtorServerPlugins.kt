package com.uiptv.server.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import org.koin.ktor.plugin.Koin

fun Application.configureBackendPlatform() {
    install(Koin) {
        modules(serverInfrastructureModule, serverServiceModule)
    }
    install(CallLogging)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(
                text = errorJson("bad_request", cause.message ?: "Invalid request"),
                status = HttpStatusCode.BadRequest
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled backend exception", cause)
            call.respondText(
                text = errorJson("internal_error", cause.message ?: "Unexpected server error"),
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}

private fun errorJson(error: String, message: String): String =
    """{"error":${quoteJson(error)},"message":${quoteJson(message)}}"""

private fun quoteJson(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
