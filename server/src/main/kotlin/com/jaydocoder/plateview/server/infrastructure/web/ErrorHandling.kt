package com.jaydocoder.plateview.server.infrastructure.web

import com.jaydocoder.plateview.server.imports.ImportBatchNotFoundException
import com.jaydocoder.plateview.server.imports.ImportFileInvalidException
import com.jaydocoder.plateview.server.imports.ImportWorkflowConflictException
import com.jaydocoder.plateview.server.admin.AdminConflictException
import com.jaydocoder.plateview.server.admin.AdminPermissionException
import com.jaydocoder.plateview.server.admin.AdminResourceNotFoundException
import com.jaydocoder.plateview.server.admin.AdminValidationException
import com.jaydocoder.plateview.server.vehicle.VehicleNotFoundException
import com.jaydocoder.plateview.server.vehicle.VehicleSearchKeywordException
import com.jaydocoder.plateview.server.vehicle.VehicleCatalogVersionConflictException
import com.jaydocoder.plateview.server.auth.ProfileConflictException
import com.jaydocoder.plateview.server.schedule.ScheduleNotFoundException
import com.jaydocoder.plateview.server.schedule.SchedulePermissionException
import com.jaydocoder.plateview.server.workorder.WorkOrderNotFoundException
import com.jaydocoder.plateview.server.workorder.WorkOrderPermissionException
import com.jaydocoder.plateview.server.workorder.WorkOrderCatalogVersionConflictException
import com.jaydocoder.plateview.server.workorder.CollectorAuthenticationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable

internal fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<VehicleCatalogVersionConflictException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse(
                    code = "VEHICLE_CATALOG_VERSION_CONFLICT",
                    message = cause.message ?: "车辆目录已更新，请重新同步",
                    requestId = call.callId,
                ),
            )
        }
        exception<WorkOrderCatalogVersionConflictException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse(
                    code = "WORK_ORDER_CATALOG_VERSION_CONFLICT",
                    message = cause.message ?: "微信目录已更新，请重新同步",
                    requestId = call.callId,
                ),
            )
        }
        exception<VehicleSearchKeywordException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "SEARCH_KEYWORD_TOO_SHORT",
                    message = cause.message ?: "请至少输入4个有效车牌字符",
                    requestId = call.callId,
                ),
            )
        }

        exception<VehicleNotFoundException> { call, _ ->
            call.respondApiError(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse(
                    code = "VEHICLE_NOT_FOUND",
                    message = "车辆不存在或已停用",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportFileInvalidException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "IMPORT_FILE_INVALID",
                    message = cause.message ?: "Excel文件格式无效",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportBatchNotFoundException> { call, _ ->
            call.respondApiError(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse(
                    code = "IMPORT_BATCH_NOT_FOUND",
                    message = "导入批次不存在",
                    requestId = call.callId,
                ),
            )
        }

        exception<ImportWorkflowConflictException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse(
                    code = cause.errorCode,
                    message = cause.message ?: "导入批次状态冲突",
                    requestId = call.callId,
                ),
            )
        }

        exception<AdminResourceNotFoundException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.NotFound,
                message = ApiErrorResponse("ADMIN_RESOURCE_NOT_FOUND", cause.message ?: "管理资源不存在", call.callId),
            )
        }

        exception<AdminPermissionException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Forbidden,
                message = ApiErrorResponse("ADMIN_PERMISSION_DENIED", cause.message ?: "没有此管理权限", call.callId),
            )
        }

        exception<AdminValidationException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse("ADMIN_VALIDATION_FAILED", cause.message ?: "管理请求无效", call.callId),
            )
        }

        exception<AdminConflictException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse("ADMIN_CONFLICT", cause.message ?: "数据已被其他管理员修改", call.callId),
            )
        }

        exception<ProfileConflictException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Conflict,
                message = ApiErrorResponse("PROFILE_CONFLICT", cause.message ?: "账号资料冲突", call.callId),
            )
        }

        exception<ScheduleNotFoundException> { call, cause ->
            call.respondApiError(HttpStatusCode.NotFound, ApiErrorResponse("SCHEDULE_NOT_FOUND", cause.message ?: "排班模板不存在", call.callId))
        }

        exception<SchedulePermissionException> { call, cause ->
            call.respondApiError(HttpStatusCode.Forbidden, ApiErrorResponse("SCHEDULE_PERMISSION_DENIED", cause.message ?: "没有排班权限", call.callId))
        }

        exception<WorkOrderPermissionException> { call, cause ->
            call.respondApiError(HttpStatusCode.Forbidden, ApiErrorResponse("WORK_ORDER_PERMISSION_DENIED", cause.message ?: "没有微信车单访问权限", call.callId))
        }

        exception<WorkOrderNotFoundException> { call, cause ->
            call.respondApiError(HttpStatusCode.NotFound, ApiErrorResponse("WORK_ORDER_NOT_FOUND", cause.message ?: "微信车单不存在", call.callId))
        }

        exception<CollectorAuthenticationException> { call, cause ->
            call.respondApiError(HttpStatusCode.Unauthorized, ApiErrorResponse("COLLECTOR_UNAUTHENTICATED", cause.message ?: "微信采集凭据无效", call.callId))
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                message = ApiErrorResponse(
                    code = "INVALID_REQUEST",
                    message = cause.message ?: "请求参数无效",
                    requestId = call.callId,
                ),
            )
        }

        exception<Throwable> { call, cause ->
            call.application.environment.log.error("请求处理失败，请求标识=${call.callId}", cause)
            call.reportUnexpectedFailure(cause)
            call.respondApiError(
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

internal val ErrorCodeAttributeKey = AttributeKey<String>("plateview.error-code")

internal suspend fun io.ktor.server.application.ApplicationCall.respondApiError(
    status: HttpStatusCode,
    message: ApiErrorResponse,
) {
    attributes.put(ErrorCodeAttributeKey, message.code)
    respond(status, message)
}

internal fun io.ktor.server.application.ApplicationCall.errorCodeOrNull(): String? =
    attributes.getOrNull(ErrorCodeAttributeKey)

@Serializable
internal data class ApiErrorResponse(
    val code: String,
    val message: String,
    val requestId: String?,
)
