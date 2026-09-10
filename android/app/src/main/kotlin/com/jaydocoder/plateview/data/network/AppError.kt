package com.jaydocoder.plateview.data.network

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

data class AppError(
    val operation: String,
    val kind: AppErrorKind,
    val requestId: String,
    val message: String,
    val retryable: Boolean,
    val httpStatus: Int? = null,
    val serverCode: String? = null,
)

fun AppError.displayText(): String = "$message\n诊断编号：$requestId"

fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

enum class AppErrorKind {
    NetworkUnavailable,
    Timeout,
    GatewayUnavailable,
    RateLimited,
    ServerFailure,
    InvalidResponse,
    SessionExpired,
    PermissionDenied,
    Validation,
    Conflict,
    NotFound,
    Unexpected,
}

class NetworkRequestException(
    val requestId: String,
    cause: IOException,
) : IOException(cause.message, cause)

object AppErrorMapper {
    fun map(operation: String, throwable: Throwable): AppError {
        val networkFailure = throwable as? NetworkRequestException
        val source = networkFailure?.cause ?: throwable
        val fallbackRequestId = networkFailure?.requestId ?: UUID.randomUUID().toString()

        if (source is HttpException) {
            return mapHttpFailure(operation, source, fallbackRequestId)
        }
        return when (source) {
            is SocketTimeoutException, is InterruptedIOException -> error(
                operation = operation,
                kind = AppErrorKind.Timeout,
                requestId = fallbackRequestId,
                message = timeoutMessage(operation),
                retryable = true,
            )

            is IOException -> error(
                operation = operation,
                kind = AppErrorKind.NetworkUnavailable,
                requestId = fallbackRequestId,
                message = "无法连接服务器，请检查网络后重试",
                retryable = true,
            )

            is IllegalArgumentException -> error(
                operation = operation,
                kind = AppErrorKind.Validation,
                requestId = fallbackRequestId,
                message = source.message?.takeIf(String::isNotBlank) ?: "请求参数无效，请检查后重试",
                retryable = false,
            )

            is JsonSyntaxException -> error(
                operation = operation,
                kind = AppErrorKind.InvalidResponse,
                requestId = fallbackRequestId,
                message = "服务器返回的数据格式异常",
                retryable = true,
            )

            else -> error(
                operation = operation,
                kind = AppErrorKind.Unexpected,
                requestId = fallbackRequestId,
                message = "操作未完成",
                retryable = true,
            )
        }
    }

    fun isRetryableReadFailure(throwable: Throwable): Boolean {
        val source = (throwable as? NetworkRequestException)?.cause ?: throwable
        return source is IOException || (source is HttpException && source.code() in RETRYABLE_HTTP_STATUSES)
    }

    private fun mapHttpFailure(operation: String, failure: HttpException, fallbackRequestId: String): AppError {
        val response = failure.response()
        val payload = response?.errorBody()?.string()?.let(::parseErrorPayload)
        val requestId = payload?.requestId
            ?: response?.headers()?.get(REQUEST_ID_HEADER)
            ?: fallbackRequestId
        val status = failure.code()
        return when (status) {
            400 -> error(operation, AppErrorKind.Validation, requestId, payload?.message ?: "请求参数无效，请检查后重试", false, status, payload?.code)
            401 -> error(operation, AppErrorKind.SessionExpired, requestId, "登录已失效，请重新登录", false, status, payload?.code)
            403 -> error(operation, AppErrorKind.PermissionDenied, requestId, "当前账号没有执行此操作的权限", false, status, payload?.code)
            404 -> error(operation, AppErrorKind.NotFound, requestId, payload?.message ?: "请求的内容不存在或已被移除", false, status, payload?.code)
            408 -> error(operation, AppErrorKind.Timeout, requestId, timeoutMessage(operation), true, status, payload?.code)
            409 -> error(operation, AppErrorKind.Conflict, requestId, "数据已被其他管理员修改，请刷新后再试", false, status, payload?.code)
            429 -> error(operation, AppErrorKind.RateLimited, requestId, "请求过于频繁，请稍后重试", true, status, payload?.code)
            502, 503, 504 -> error(operation, AppErrorKind.GatewayUnavailable, requestId, "服务正在切换或暂时繁忙，请稍后重试", true, status, payload?.code)
            in 500..599 -> error(operation, AppErrorKind.ServerFailure, requestId, "服务器处理失败", true, status, payload?.code)
            else -> error(operation, AppErrorKind.Unexpected, requestId, "操作未完成", false, status, payload?.code)
        }
    }

    private fun parseErrorPayload(body: String): ApiErrorPayload? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        ApiErrorPayload(
            code = root.get("code")?.asString,
            message = root.get("message")?.asString,
            requestId = root.get("requestId")?.asString,
        )
    }.getOrNull()

    private fun error(
        operation: String,
        kind: AppErrorKind,
        requestId: String,
        message: String,
        retryable: Boolean,
        httpStatus: Int? = null,
        serverCode: String? = null,
    ) = AppError(operation, kind, requestId, message, retryable, httpStatus, serverCode)

    private fun timeoutMessage(operation: String): String = when (operation) {
        "筛选车辆档案" -> "请求超时，车辆档案尚未刷新"
        else -> "请求超时，数据尚未刷新"
    }

    private data class ApiErrorPayload(
        val code: String?,
        val message: String?,
        val requestId: String?,
    )

    private const val REQUEST_ID_HEADER = "X-Request-ID"
    private val RETRYABLE_HTTP_STATUSES = setOf(408, 429, 502, 503, 504)
}
