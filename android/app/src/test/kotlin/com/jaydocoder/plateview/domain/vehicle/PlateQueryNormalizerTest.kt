package com.jaydocoder.plateview.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateQueryNormalizerTest {
    @Test
    fun `归一化移除分隔符并统一字母大小写`() {
        assertEquals("新A1234", PlateQueryNormalizer.normalize(" 新a·12-34 "))
    }

    @Test
    fun `归一化移除无效字符但保留车牌有效字符`() {
        assertEquals("新A1234", PlateQueryNormalizer.normalize("新A#12@34"))
    }
}
