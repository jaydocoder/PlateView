package com.jaydocoder.plateview.data.workorder

import com.jaydocoder.plateview.domain.workorder.AttachmentDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.io.path.createTempFile

class AttachmentCachePolicyTest {
    @Test
    fun `附件质量或内容升级后缓存键必须变化`() {
        val thumbnail = attachmentCacheKey(41L, "thumbnail", "low", "THUMBNAIL")
        val highDefinition = attachmentCacheKey(41L, "original", "high", "HIGH_DEFINITION")
        val original = attachmentCacheKey(41L, "original", "original", "ORIGINAL")

        assertNotEquals(thumbnail, highDefinition)
        assertNotEquals(highDefinition, original)
    }

    @Test
    fun `已完成下载任务可以恢复为详情页本地附件`() {
        val file = createTempFile(prefix = "plateview-attachment-", suffix = ".pdf").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val state = AttachmentDownloadState(
            attachmentId = 41,
            kind = "PDF",
            fileName = "测试附件.pdf",
            variant = "original",
            status = "COMPLETED",
            downloadedBytes = file.length(),
            expectedSize = file.length(),
            localPath = file.absolutePath,
            attemptCount = 0,
            lastErrorCode = null,
        )

        val cached = state.completedFile()

        assertEquals(file, cached?.file)
        assertEquals("original", cached?.variant)
    }

    @Test
    fun `已完成状态指向缺失文件时不能伪装为可用缓存`() {
        val state = AttachmentDownloadState(
            attachmentId = 42,
            kind = "IMAGE",
            fileName = "缺失图片.jpg",
            variant = "original",
            status = "COMPLETED",
            downloadedBytes = 1024,
            expectedSize = 1024,
            localPath = "/tmp/plateview-not-found-${System.nanoTime()}.jpg",
            attemptCount = 0,
            lastErrorCode = null,
        )

        assertNull(state.completedFile())
    }
}
