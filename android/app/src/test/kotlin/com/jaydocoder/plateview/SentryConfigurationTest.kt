package com.jaydocoder.plateview

import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.User
import org.junit.Assert.assertNull
import org.junit.Test

class SentryConfigurationTest {
    @Test
    fun `异常事件会移除请求与用户资料`() {
        val event = SentryEvent().apply {
            request = Request().apply { url = "https://example.test/vehicles?plate=新A12345" }
            user = User().apply { username = "真实姓名" }
        }

        event.removeSensitiveData()

        assertNull(event.request)
        assertNull(event.user)
    }
}
