package com.jaydocoder.plateview.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidationIntervalTest {
    @Test
    fun `前台心跳随机区间固定为十二至十八秒`() {
        val observedBounds = mutableListOf<Pair<Long, Long>>()

        val delay = sessionValidationDelayMillis { from, until ->
            observedBounds += from to until
            until - 1
        }

        assertEquals(listOf(12_000L to 18_001L), observedBounds)
        assertEquals(18_000L, delay)
    }

    @Test
    fun `心跳延迟结果始终位于允许区间`() {
        listOf(12_000L, 15_000L, 18_000L).forEach { expected ->
            val delay = sessionValidationDelayMillis { _, _ -> expected }
            assertTrue(delay in 12_000L..18_000L)
        }
    }
}
