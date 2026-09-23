package com.jaydocoder.plateview.server.client

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientPolicyTest {
    @Test
    fun `普通账号严格执行零到五十的配置值`() {
        listOf(0, 1, 8, 50).forEach { configured ->
            assertEquals(configured, effectiveLimit(configured, primaryAdmin = false))
        }
    }

    @Test
    fun `主管理员仅在配置为零时回退十条`() {
        assertEquals(8, effectiveLimit(0, primaryAdmin = true))
        assertEquals(1, effectiveLimit(1, primaryAdmin = true))
        assertEquals(8, effectiveLimit(8, primaryAdmin = true))
        assertEquals(50, effectiveLimit(50, primaryAdmin = true))
    }
}
