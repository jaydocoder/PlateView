package com.jaydocoder.plateview.server.infrastructure.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import java.util.UUID

internal fun Application.configureRequestContext() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate {
            UUID.randomUUID().toString()
        }
        verify { requestId ->
            requestId.length <= 64 && requestId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
        replyToHeader(HttpHeaders.XRequestId)
    }
}
