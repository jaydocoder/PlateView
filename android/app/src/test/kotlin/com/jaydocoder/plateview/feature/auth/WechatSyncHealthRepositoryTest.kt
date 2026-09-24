package com.jaydocoder.plateview.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WechatSyncHealthRepositoryTest {
    @Test
    fun `远端状态会写入并完整保留时间`() {
        val repository = WechatSyncHealthRepository()

        repository.apply(WechatSyncHealthDto("ONLINE_HEALTHY", "2026-09-24T14:20:00Z", "2026-09-24T14:20:05Z"))

        assertEquals("ONLINE_HEALTHY", repository.health.value?.state)
        assertEquals("2026-09-24T14:20:00Z", repository.health.value?.lastSuccessfulSyncAt)
        assertEquals("2026-09-24T14:20:05Z", repository.health.value?.lastHeartbeatAt)
    }

    @Test
    fun `心跳失败切换未知但保留最近成功时间`() {
        val repository = WechatSyncHealthRepository()
        repository.apply(WechatSyncHealthDto("ONLINE_HEALTHY", "2026-09-24T14:20:00Z", "2026-09-24T14:20:05Z"))

        repository.markUnknown()

        assertEquals("UNKNOWN", repository.health.value?.state)
        assertEquals("2026-09-24T14:20:00Z", repository.health.value?.lastSuccessfulSyncAt)
    }

    @Test
    fun `退出登录清除当前账号状态`() {
        val repository = WechatSyncHealthRepository()
        repository.apply(WechatSyncHealthDto("OFFLINE", null, "2026-09-24T14:20:05Z"))

        repository.clear()

        assertNull(repository.health.value)
    }
}
