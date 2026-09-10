package com.jaydocoder.plateview.data.network

import io.sentry.Sentry

object AppErrorTelemetry {
    fun report(error: AppError) {
        if (!shouldReport(error)) return
        Sentry.withScope { scope ->
            scope.setTag("plateview.error_kind", error.kind.name)
            scope.setTag("plateview.operation", error.operation)
            scope.setTag("plateview.request_id", error.requestId)
            error.httpStatus?.let { scope.setTag("http.status_code", it.toString()) }
            error.serverCode?.let { scope.setTag("plateview.server_code", it) }
            Sentry.captureException(AppErrorReportException(error.kind, error.operation))
        }
    }

    internal fun shouldReport(error: AppError): Boolean = error.kind !in EXPECTED_BUSINESS_FAILURES

    private val EXPECTED_BUSINESS_FAILURES = setOf(
        AppErrorKind.Validation,
        AppErrorKind.SessionExpired,
        AppErrorKind.PermissionDenied,
        AppErrorKind.Conflict,
        AppErrorKind.NotFound,
    )
}

private class AppErrorReportException(kind: AppErrorKind, operation: String) : RuntimeException("$kind:$operation")
