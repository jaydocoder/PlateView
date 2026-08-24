package com.jaydocoder.plateview.server.statistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StatisticsAccessPolicyTest {
    @Test
    fun `仅主管理员可使用全员统计范围`() {
        val filter = StatisticsFilter.fromRequest(
            range = "TODAY",
            category = null,
            scope = "ALL",
            actorId = 1L,
            isPrimaryAdministrator = true,
        )

        assertNull(filter.actorId)
    }

    @Test
    fun `其他管理员请求全员统计被拒绝`() {
        val error = assertFailsWith<IllegalArgumentException> {
            StatisticsFilter.fromRequest(
                range = "TODAY",
                category = null,
                scope = "ALL",
                actorId = 2L,
                isPrimaryAdministrator = false,
            )
        }

        assertEquals("仅admin账号可以查看全员统计", error.message)
    }
}
