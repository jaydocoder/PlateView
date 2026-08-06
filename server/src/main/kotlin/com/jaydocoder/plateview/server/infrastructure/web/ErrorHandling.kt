package com.jaydocoder.plateview.server.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

internal fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "INVALID_REQUEST",
                    message = cause.message ?: "请求参数无效",
                    requestId = call.callId,
                ),
            )
        }

        exception<Throwable> { call, _ ->
            call.application.environment.log.error("请求处理失败，请求标识=${call.callId}")
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ApiErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = "服务暂时不可用，请稍后重试",
                    requestId = call.callId,
                ),
            )
        }
    }
}

@Serializable
internal data class ApiErrorResponse(
    val code: String,
    val message: String,
    val requestId: String?,
)
