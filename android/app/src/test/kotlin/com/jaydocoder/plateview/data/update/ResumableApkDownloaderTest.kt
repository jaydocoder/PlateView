package com.jaydocoder.plateview.data.update

import com.jaydocoder.plateview.domain.update.AppUpdate
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResumableApkDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var downloadDirectory: File
    private val downloader by lazy { ResumableApkDownloader(OkHttpClient(), downloadDirectory) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloadDirectory = Files.createTempDirectory("plateview-update-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadDirectory.deleteRecursively()
    }

    @Test
    fun `存在部分文件时使用范围请求续传并保留已下载进度`() = runTest {
        temporaryFile("0.4.0").writeText("hello")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 5-10/11")
                .setBody(" world"),
        )
        val progress = mutableListOf<Pair<Long, Long?>>()

        val downloaded = downloader.download(update("0.4.0")) { state ->
            progress += state.downloadedBytes to state.totalBytes
        }

        assertEquals("bytes=5-", server.takeRequest().getHeader("Range"))
        assertEquals("hello world", downloaded.readText())
        assertTrue(progress.contains(5L to 11L))
        assertEquals(11L to 11L, progress.last())
        assertTrue(!temporaryFile("0.4.0").exists())
    }

    @Test
    fun `服务器忽略范围请求时使用响应内容完整重下`() = runTest {
        temporaryFile("0.4.1").writeText("旧数据")
        server.enqueue(MockResponse().setResponseCode(200).setBody("完整安装包"))

        val downloaded = downloader.download(update("0.4.1")) {}

        assertEquals("bytes=9-", server.takeRequest().getHeader("Range"))
        assertEquals("完整安装包", downloaded.readText())
        assertTrue(!temporaryFile("0.4.1").exists())
    }

    @Test
    fun `无效范围响应时清除断点并自动全量重试一次`() = runTest {
        temporaryFile("0.4.2").writeText("失效断点")
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .addHeader("Content-Range", "bytes */5"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("完整包"))

        val downloaded = downloader.download(update("0.4.2")) {}

        assertEquals("bytes=12-", server.takeRequest().getHeader("Range"))
        assertEquals(null, server.takeRequest().getHeader("Range"))
        assertEquals("完整包", downloaded.readText())
    }

    @Test
    fun `服务器返回错误续传起点时不拼接错误数据`() = runTest {
        temporaryFile("0.4.3").writeText("已有断点")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 0-5/6")
                .setBody("错误内容"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("完整安装包"))

        val downloaded = downloader.download(update("0.4.3")) {}

        assertEquals("bytes=12-", server.takeRequest().getHeader("Range"))
        assertEquals(null, server.takeRequest().getHeader("Range"))
        assertEquals("完整安装包", downloaded.readText())
    }

    @Test
    fun `下载失败后保留部分文件供下次继续`() = runTest {
        val part = temporaryFile("0.4.4").apply { writeText("已下载") }
        server.enqueue(MockResponse().setResponseCode(500))

        runCatching { downloader.download(update("0.4.4")) {} }
            .onSuccess { error("预期下载失败") }

        assertEquals("已下载", part.readText())
        assertTrue(!completedFile("0.4.4").exists())
    }

    private fun update(versionName: String) = AppUpdate(
        versionName = versionName,
        releaseNotes = "测试更新",
        downloadUrl = server.url("/app-release.apk").toString(),
    )

    private fun temporaryFile(versionName: String) = File(downloadDirectory, "PlateView-$versionName.apk.part")

    private fun completedFile(versionName: String) = File(downloadDirectory, "PlateView-$versionName.apk")
}
