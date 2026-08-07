package com.jaydocoder.plateview.server.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals

class PlateNormalizerTest {
    @Test
    fun `车牌归一化忽略大小写和无语义分隔符`() {
        assertEquals("新A12345", normalizePlate(" 新a·1 2-3_4.5　"))
    }

    @Test
    fun `车牌归一化移除非车牌字符`() {
        assertEquals("新A12345", normalizePlate("新A(12345)"))
    }

    @Test
    fun `查询最小有效字符数固定为一位`() {
        assertEquals(1, MINIMUM_SEARCH_KEYWORD_LENGTH)
        assertEquals(20, MAXIMUM_SEARCH_RESULT_COUNT)
    }
}
