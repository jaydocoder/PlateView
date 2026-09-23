package com.jaydocoder.plateview.data.workorder

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header

class AttachmentVariantDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var api: AttachmentTestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = Files.createTempDirectory("plateview-attachment-test").toFile()
        api = Retrofit.Builder().baseUrl(server.url("/")).build().create(AttachmentTestApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun `完整附件下载后临时文件会转为正式缓存`() = runTest {
        val payload = "微信原始图片".toByteArray()
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(payload.toString(Charsets.UTF_8)))

        val result = downloadAttachmentVariant(
            request = api::download,
            directory = directory,
            cacheKey = "478-original-original-${payload.sha256()}",
            variant = "original",
            expectedSha256 = payload.sha256(),
        )

        assertEquals(payload.toList(), result.file.readBytes().toList())
        assertEquals("jpg", result.file.extension)
        assertFalse(File(directory, "478-original-original-${payload.sha256()}.download").exists())
    }

    @Test
    fun `详情会复用摘要一致的管理员原件缓存`() {
        val payload = "已经缓存的微信原图".toByteArray()
        val administratorCache = File(directory, "478-original-admin.jpg").apply { writeBytes(payload) }

        val cached = findCachedAttachmentFile(
            directory = directory,
            cacheKey = "478-original-original-${payload.sha256()}",
            id = 478L,
            variant = "original",
            expectedSha256 = payload.sha256(),
        )

        assertEquals(administratorCache, cached)
    }

    @Test
    fun `详情不会复用摘要不一致的旧附件`() {
        File(directory, "478-original-admin.jpg").writeText("旧图片")

        val cached = findCachedAttachmentFile(
            directory = directory,
            cacheKey = "478-original-original-new",
            id = 478L,
            variant = "original",
            expectedSha256 = "new",
        )

        assertNull(cached)
    }

    @Test
    fun `目录同步成功后清理编号相同但摘要不同的旧附件`() {
        val oldPdf = File(directory, "459-original-original-old.pdf").apply { writeText("旧PDF") }
        val currentImage = File(directory, "459-original-original-new.jpg").apply { writeText("新图片") }
        val unrelated = File(directory, "458-original-original-old.jpg").apply { writeText("旧图片") }

        pruneAttachmentCache(directory, setOf("459-original-original-new"))

        assertFalse(oldPdf.exists())
        assertFalse(unrelated.exists())
        assertEquals(true, currentImage.exists())
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private interface AttachmentTestApi {
    @GET("attachment")
    suspend fun download(@Header("Range") range: String?): Response<ResponseBody>
}
