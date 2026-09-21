package com.jaydocoder.plateview.server.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(8, MAXIMUM_SEARCH_RESULT_COUNT)
    }

    @Test
    fun `完整车牌识别支持普通与新能源车牌`() {
        assertTrue(isCompletePlateNumber("新A12345"))
        assertTrue(isCompletePlateNumber("新AD12345"))
        assertFalse(isCompletePlateNumber("新A123"))
        assertFalse(isCompletePlateNumber("孙主任"))
    }
}
