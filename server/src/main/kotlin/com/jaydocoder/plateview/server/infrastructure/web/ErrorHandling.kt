package com.jaydocoder.plateview.server.infrastructure.web

import com.jaydocoder.plateview.server.imports.ImportBatchNotFoundException
import com.jaydocoder.plateview.server.imports.ImportFileInvalidException
import com.jaydocoder.plateview.server.imports.ImportWorkflowConflictException
import com.jaydocoder.plateview.server.admin.AdminConflictException
import com.jaydocoder.plateview.server.admin.AdminResourceNotFoundException
import com.jaydocoder.plateview.server.admin.AdminValidationException
import com.jaydocoder.plateview.server.vehicle.VehicleNotFoundException
import com.jaydocoder.plateview.server.vehicle.VehicleSearchKeywordException
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
        exception<VehicleSearchKeywordException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "SEARCH_KEYWORD_TOO_SHORT",
                    message = cause.message ?: "请至少输入4个有效车牌字符",
                    requestId = call.callId,
                ),
            )
        }

        exception<VehicleNotFoundException> { call, _ ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse(
                    code = "VEHICLE_NOT_FOUND",
                    message = "车辆不存在或已停用",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportFileInvalidException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "IMPORT_FILE_INVALID",
                    message = cause.message ?: "Excel文件格式无效",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportBatchNotFoundException> { call, _ ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse(
                    code = "IMPORT_BATCH_NOT_FOUND",
                    message = "导入批次不存在",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportWorkflowConflictException> { call, cause ->
            call.respond(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse(
                    code = cause.errorCode,
                    message = cause.message ?: "导入批次状态冲突",
                    requestId = call.callId,
                ),
            )
        }

        exception<AdminResourceNotFoundException> { call, cause ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse("ADMIN_RESOURCE_NOT_FOUND", cause.message ?: "管理资源不存在", call.callId),
            )
        }

        exception<AdminValidationException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse("ADMIN_VALIDATION_FAILED", cause.message ?: "管理请求无效", call.callId),
            )
        }

        exception<AdminConflictException> { call, cause ->
            call.respond(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse("ADMIN_CONFLICT", cause.message ?: "数据已被其他管理员修改", call.callId),
            )
        }

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
