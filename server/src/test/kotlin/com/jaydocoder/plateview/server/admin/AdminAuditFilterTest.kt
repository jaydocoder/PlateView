package com.jaydocoder.plateview.server.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAuditFilterTest {
    @Test
    fun `未提供时间范围时默认最近三十天`() {
        assertEquals(AdminAuditRange.THIRTY_DAYS, AdminAuditRange.fromRequest(null))
    }

    @Test
    fun `异常结果筛选可被解析`() {
        assertEquals(AdminAuditResult.ABNORMAL, AdminAuditResult.fromRequest("ABNORMAL"))
    }

    @Test
    fun `无效时间范围被拒绝`() {
        assertFailsWith<AdminValidationException> {
            AdminAuditRange.fromRequest("month")
        }
    }

    @Test
    fun `筛选条件会清理空白关键词`() {
        assertEquals(null, AdminAuditFilter(keyword = "   ").normalized().keyword)
    }
}
