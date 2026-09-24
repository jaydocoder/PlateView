package com.jaydocoder.plateview.data.workorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WechatAttachmentSharedCacheTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var repository: WechatAttachmentCacheRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.filesDir, "work-order-images")
        root.deleteRecursively()
        repository = WechatAttachmentCacheRepository(context)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun 已校验原文件可以在授权任务之间跨账号复用且账号清理不删除共享实体() = runBlocking {
        val bytes = "同一台设备上的微信原始附件".toByteArray()
        val sha256 = bytes.sha256()
        val attachmentId = 71L
        val legacy = File(
            repository.directory(1),
            "${attachmentCacheKey(attachmentId, "original", sha256, "ORIGINAL")}.jpg",
        ).apply { writeBytes(bytes) }

        val first = repository.findCached(2, attachmentId, "original", sha256, "ORIGINAL", bytes.size.toLong())
        val second = repository.findCached(3, 999, "original", sha256, "ORIGINAL", bytes.size.toLong())

        assertTrue(legacy.isFile)
        assertTrue(first?.absolutePath?.contains("/shared/") == true)
        assertEquals(first?.absolutePath, second?.absolutePath)
        repository.clear(2)
        assertTrue(first?.isFile == true)
        repository.clearAttachmentVersion(3, 999, "original", sha256, "ORIGINAL")
        assertTrue(first?.isFile == true)
    }

    @Test
    fun 无稳定摘要的附件不会跨账号复用() {
        val attachmentId = 72L
        File(
            repository.directory(1),
            "${attachmentCacheKey(attachmentId, "original", null, "UNKNOWN")}.jpg",
        ).writeText("不能共享")

        val result = repository.findCached(2, attachmentId, "original", null, "UNKNOWN")

        assertNull(result)
    }

    @Test
    fun 摘要不匹配的旧文件不能迁移到共享目录() {
        val expected = "正确内容".toByteArray()
        val sha256 = expected.sha256()
        val attachmentId = 73L
        File(
            repository.directory(1),
            "${attachmentCacheKey(attachmentId, "original", sha256, "ORIGINAL")}.jpg",
        ).writeBytes("损坏内容".toByteArray())

        val result = repository.findCached(2, attachmentId, "original", sha256, "ORIGINAL")

        assertNull(result)
        assertFalse(File(root, "shared").listFiles().orEmpty().any { it.extension != "download" })
    }

    @Test
    fun 显式设备清理会删除共享附件() {
        val bytes = "需要远程清理的附件".toByteArray()
        val sha256 = bytes.sha256()
        val attachmentId = 74L
        File(
            repository.directory(1),
            "${attachmentCacheKey(attachmentId, "original", sha256, "ORIGINAL")}.pdf",
        ).writeBytes(bytes)
        val cached = repository.findCached(1, attachmentId, "original", sha256, "ORIGINAL")

        repository.clearSharedFiles()

        assertTrue(cached != null)
        assertFalse(cached!!.exists())
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
