package com.jaydocoder.plateview.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidationIntervalTest {
    @Test
    fun `前台会话校验随机区间固定为五十五至六十五秒`() {
        val observedBounds = mutableListOf<Pair<Long, Long>>()

        val delay = sessionValidationDelayMillis { from, until ->
            observedBounds += from to until
            until - 1
        }

        assertEquals(listOf(55_000L to 65_001L), observedBounds)
        assertEquals(65_000L, delay)
    }

    @Test
    fun `心跳延迟结果始终位于允许区间`() {
        listOf(55_000L, 60_000L, 65_000L).forEach { expected ->
            val delay = sessionValidationDelayMillis { _, _ -> expected }
            assertTrue(delay in 55_000L..65_000L)
        }
    }
}
