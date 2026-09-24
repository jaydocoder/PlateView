package com.jaydocoder.plateview.server.client

import java.time.Instant
import com.jaydocoder.plateview.server.workorder.isSuccessfulWechatSyncHeartbeat
import kotlin.test.Test
import kotlin.test.assertEquals

class WechatSyncHealthServiceTest {
    private val now = Instant.parse("2026-09-24T14:30:00Z")

    @Test
    fun `任一来源健康时整体状态优先显示健康`() {
        val result = aggregateWechatSyncHealth(
            sources = listOf(
                source("HEALTHY", "2026-09-24T14:29:30Z", "2026-09-24T14:29:20Z"),
                source("UPLOAD_FAILED", "2026-09-24T14:29:40Z", "2026-09-24T14:20:00Z"),
            ),
            now = now,
        )

        assertEquals("ONLINE_HEALTHY", result.state)
        assertEquals("2026-09-24T14:29:20Z", result.lastSuccessfulSyncAt)
        assertEquals("2026-09-24T14:29:40Z", result.lastHeartbeatAt)
    }

    @Test
    fun `没有健康来源时依次区分追赶异常和离线`() {
        assertEquals(
            "ONLINE_SYNCING",
            aggregateWechatSyncHealth(listOf(source("CATCHING_UP", "2026-09-24T14:29:00Z")), now).state,
        )
        assertEquals(
            "ONLINE_ERROR",
            aggregateWechatSyncHealth(listOf(source("KEY_MISSING", "2026-09-24T14:29:00Z")), now).state,
        )
        assertEquals(
            "OFFLINE",
            aggregateWechatSyncHealth(listOf(source("HEALTHY", "2026-09-24T14:28:29Z")), now).state,
        )
    }

    @Test
    fun `仅健康无积压无错误心跳更新成功同步时间`() {
        assertEquals(true, isSuccessfulWechatSyncHeartbeat("HEALTHY", 0, null))
        assertEquals(false, isSuccessfulWechatSyncHeartbeat("HEALTHY", 1, null))
        assertEquals(false, isSuccessfulWechatSyncHeartbeat("HEALTHY", 0, "UPLOAD_FAILED"))
        assertEquals(false, isSuccessfulWechatSyncHeartbeat("CATCHING_UP", 0, null))
    }

    private fun source(status: String, heartbeat: String, successful: String? = null) = WechatSyncHealthSource(
        status = status,
        lastHeartbeatAt = Instant.parse(heartbeat),
        lastSuccessfulSyncAt = successful?.let(Instant::parse),
    )
}
