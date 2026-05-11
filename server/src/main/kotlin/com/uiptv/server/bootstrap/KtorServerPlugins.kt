package com.uiptv.server.bootstrap

import com.uiptv.server.api.ApiNotFoundException
import com.uiptv.server.api.BackendHttpException
import com.uiptv.server.api.dto.ErrorResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin
import java.sql.SQLException

fun Application.configureBackendPlatform(extraModules: List<Module> = emptyList()) {
    install(Koin) {
        allowOverride(true)
        modules(listOf(serverInfrastructureModule, serverServiceModule) + extraModules)
    }
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }
    install(CallLogging)
    install(StatusPages) {
        exception<BackendHttpException> { call, cause ->
            when {
                cause.responseBody != null -> call.respondText(
                    text = cause.responseBody,
                    contentType = cause.contentType ?: ContentType.Text.Plain,
                    status = cause.status
                )
                else -> call.respond(cause.status)
            }
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse("bad_request", cause.message ?: "Invalid request")
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse("invalid_state", cause.message ?: "Invalid request state")
            )
        }
        exception<SerializationException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse("malformed_json", cause.message ?: "Malformed request body")
            )
        }
        exception<ApiNotFoundException> { call, cause ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponse("not_found", cause.message ?: "Resource not found")
            )
        }
        exception<SQLException> { call, cause ->
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse("database_error", cause.message ?: "Database operation failed")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled backend exception", cause)
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse("internal_error", cause.message ?: "Unexpected server error")
            )
        }
    }
}
