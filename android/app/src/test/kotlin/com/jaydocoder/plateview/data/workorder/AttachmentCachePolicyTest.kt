package com.jaydocoder.plateview.data.workorder

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachmentCachePolicyTest {
    @Test
    fun `附件质量或内容升级后缓存键必须变化`() {
        val thumbnail = attachmentCacheKey(41L, "thumbnail", "low", "THUMBNAIL")
        val highDefinition = attachmentCacheKey(41L, "original", "high", "HIGH_DEFINITION")
        val original = attachmentCacheKey(41L, "original", "original", "ORIGINAL")

        assertNotEquals(thumbnail, highDefinition)
        assertNotEquals(highDefinition, original)
    }
}
