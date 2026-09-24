package com.jaydocoder.plateview.server.workorder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachmentCachePaginationTest {
    @Test
    fun `空列表固定返回第一页且总页数为零`() {
        assertEquals(AttachmentCachePage(page = 1, totalPages = 0), resolveAttachmentCachePage(0, 8, 10))
    }

    @Test
    fun `超过末页时返回最后一页`() {
        assertEquals(AttachmentCachePage(page = 3, totalPages = 3), resolveAttachmentCachePage(25, 9, 10))
    }

    @Test
    fun `支持三种固定每页数量`() {
        assertEquals(10, resolveAttachmentCachePage(100, 1, 10).totalPages)
        assertEquals(5, resolveAttachmentCachePage(100, 1, 20).totalPages)
        assertEquals(2, resolveAttachmentCachePage(100, 1, 50).totalPages)
    }

    @Test
    fun `拒绝未允许的每页数量`() {
        assertFailsWith<IllegalArgumentException> { resolveAttachmentCachePage(100, 1, 30) }
    }
}
