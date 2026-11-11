package com.jaydocoder.plateview.server.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    @Test
    fun `运行时策略序列化包含API和更新服务地址`() {
        val policy = ClientRuntimePolicyResponse(
            policyRevision = 2,
            vehicleResultLimit = 8,
            workOrderResultLimit = 8,
            wechatMessageResultLimit = 8,
            apiBaseUrl = "https://api.plateview.top/",
            updateBaseUrl = "https://api.plateview.top/updates/",
            cacheResetRevision = 0,
        )

        val json = Json.encodeToString(policy)
        assertTrue(json.contains("\"apiBaseUrl\":\"https://api.plateview.top/\""))
        assertTrue(json.contains("\"updateBaseUrl\":\"https://api.plateview.top/updates/\""))
    }
}
