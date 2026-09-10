package com.jaydocoder.plateview.server.infrastructure.web

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

internal fun Application.configureRequestObservability() {
    install(CallLogging) {
        logger = this@configureRequestObservability.environment.log
        level = Level.INFO
        disableDefaultColors()
        format { call ->
            "事件=request_completed 请求标识=${call.callId ?: "missing"} 方法=${call.request.httpMethod.value} " +
                "路径=${call.request.path()} 状态=${call.response.status()?.value ?: 0} " +
                "耗时毫秒=${call.processingTimeMillis()} 错误码=${call.errorCodeOrNull() ?: "none"}"
        }
    }
}
