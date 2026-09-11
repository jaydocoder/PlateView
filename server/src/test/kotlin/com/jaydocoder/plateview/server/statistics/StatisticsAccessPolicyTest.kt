package com.jaydocoder.plateview.server.statistics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatisticsAccessPolicyTest {
    @Test
    fun `全部时间范围从最早查询事件开始统计`() {
        val filter = StatisticsFilter.fromRequest(
            range = "ALL_TIME",
            category = null,
            scope = "ME",
            actorId = 1L,
            isPrimaryAdministrator = false,
        )

        assertEquals(StatisticsRange.ALL_TIME, filter.range)
        assertEquals(java.time.Instant.EPOCH, filter.range.startAt())
    }

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

    @Test
    fun `查询记录分页限制范围并规范化车牌关键字`() {
        val page = StatisticsHistoryPage.fromRequest(
            query = " 新A·12345 ",
            limit = "20",
            offset = "40",
        )

        assertEquals("新A12345", page.query)
        assertEquals(20, page.limit)
        assertEquals(40, page.offset)
        assertTrue(assertFailsWith<IllegalArgumentException> {
            StatisticsHistoryPage.fromRequest(null, limit = "51", offset = "0")
        }.message!!.contains("分页大小"))
    }
}
