package com.jaydocoder.plateview.server.infrastructure.web

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions

internal fun Application.configureSentry() {
    val dsn = System.getenv("SENTRY_DSN").orEmpty().trim()
    if (dsn.isBlank()) return
    Sentry.init { options ->
        options.dsn = dsn
        options.environment = System.getenv("SENTRY_ENVIRONMENT").orEmpty().ifBlank { "production" }
        options.release = System.getenv("SENTRY_RELEASE").orEmpty().ifBlank { "plateview-server@unknown" }
        options.isSendDefaultPii = false
        options.setBeforeSend(object : SentryOptions.BeforeSendCallback {
            override fun execute(event: SentryEvent, hint: Hint): SentryEvent {
                event.request = null
                event.user = null
                return event
            }
        })
        options.setBeforeBreadcrumb(object : SentryOptions.BeforeBreadcrumbCallback {
            override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? = null
        })
    }
}

internal fun ApplicationCall.reportUnexpectedFailure(cause: Throwable) {
    Sentry.withScope { scope ->
        scope.setTag("plateview.request_id", callId ?: "missing")
        scope.setTag("http.method", request.httpMethod.value)
        scope.setTag("http.route", request.path())
        scope.setLevel(SentryLevel.ERROR)
        Sentry.captureException(cause)
    }
}
