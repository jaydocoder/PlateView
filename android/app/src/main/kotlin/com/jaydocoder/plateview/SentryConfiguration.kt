package com.jaydocoder.plateview

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

internal fun configureSentry(context: Context) {
    if (BuildConfig.DEBUG || BuildConfig.SENTRY_DSN.isBlank()) return
    SentryAndroid.init(context) { options ->
        options.dsn = BuildConfig.SENTRY_DSN
        options.environment = BuildConfig.SENTRY_ENVIRONMENT
        options.release = "plateview@${BuildConfig.VERSION_NAME}"
        options.isSendDefaultPii = false
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.enableAllAutoBreadcrumbs(false)
        options.setBeforeSend(object : SentryOptions.BeforeSendCallback {
            override fun execute(event: SentryEvent, hint: Hint): SentryEvent = event.removeSensitiveData()
        })
        options.setBeforeBreadcrumb(object : SentryOptions.BeforeBreadcrumbCallback {
            override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? = null
        })
    }
}

internal fun SentryEvent.removeSensitiveData(): SentryEvent {
    request = null
    user = null
    return this
}
