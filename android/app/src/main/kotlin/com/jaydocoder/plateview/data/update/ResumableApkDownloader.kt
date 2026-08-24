package com.jaydocoder.plateview.data.update

import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class ResumableApkDownloader(
    private val client: OkHttpClient,
    private val downloadDirectory: File,
) {
    suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        check(downloadDirectory.exists() || downloadDirectory.mkdirs()) { "无法创建更新下载目录" }

        val apkFile = File(downloadDirectory, "PlateView-${update.versionName}.apk")
        val temporaryFile = File(downloadDirectory, "PlateView-${update.versionName}.apk.part")
        downloadIntoTemporaryFile(update.downloadUrls, temporaryFile, onProgress)

        check(temporaryFile.length() > 0L) { "下载更新失败，安装包为空" }
        update.sha256?.let { expected ->
            check(temporaryFile.sha256().equals(expected, ignoreCase = true)) {
                temporaryFile.delete()
                "下载更新失败，安装包校验不匹配"
            }
        }
        check(!apkFile.exists() || apkFile.delete()) { "无法替换旧安装包" }
        check(temporaryFile.renameTo(apkFile)) { "下载更新失败，无法保存安装包" }
        apkFile
    }

    private fun downloadIntoTemporaryFile(
        downloadUrls: List<String>,
        temporaryFile: File,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ) {
        var lastFailure: Throwable? = null
        for (downloadUrl in downloadUrls.distinct()) {
            try {
                downloadFromSource(downloadUrl, temporaryFile, onProgress)
                return
            } catch (failure: Throwable) {
                lastFailure = failure
            }
        }
        throw IllegalStateException("所有更新下载源均不可用", lastFailure)
    }

    private fun downloadFromSource(
        downloadUrl: String,
        temporaryFile: File,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ) {
        var downloadedBytes = temporaryFile.length().coerceAtLeast(0L)
        var retriedAfterInvalidRange = false
        while (true) {
            val request = Request.Builder()
                .url(downloadUrl)
                .apply {
                    if (downloadedBytes > 0L) {
                        header(HTTP_RANGE_HEADER, "bytes=$downloadedBytes-")
                    }
                }
                .build()
            val response = client.newCall(request).execute()
            try {
                if (downloadedBytes > 0L && response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                    check(!retriedAfterInvalidRange) { "下载更新失败，服务器拒绝续传范围" }
                    temporaryFile.delete()
                    downloadedBytes = 0L
                    retriedAfterInvalidRange = true
                    continue
                }
                check(response.isSuccessful) { "下载更新失败，服务器返回 ${response.code}" }

                if (downloadedBytes > 0L && response.code == HTTP_PARTIAL_CONTENT) {
                    val rangeStart = response.contentRangeStart()
                    if (rangeStart != downloadedBytes) {
                        check(!retriedAfterInvalidRange) { "下载更新失败，服务器返回了错误的续传范围" }
                        temporaryFile.delete()
                        downloadedBytes = 0L
                        retriedAfterInvalidRange = true
                        continue
                    }
                }

                val append = downloadedBytes > 0L && response.code == HTTP_PARTIAL_CONTENT
                if (!append && downloadedBytes > 0L) {
                    temporaryFile.delete()
                    downloadedBytes = 0L
                }
                val totalBytes = requireNotNull(response.totalBytes(downloadedBytes)) {
                    "下载更新失败，服务器未提供安装包大小"
                }
                val responseBody = checkNotNull(response.body) { "下载更新失败，未收到安装包" }
                FileOutputStream(temporaryFile, append).buffered().use { output ->
                    onProgress(UpdateDownloadProgress(downloadedBytes, totalBytes))
                    responseBody.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            onProgress(UpdateDownloadProgress(downloadedBytes, totalBytes))
                        }
                    }
                }
                check(temporaryFile.length() == totalBytes) { "下载更新失败，安装包不完整" }
                return
            } finally {
                response.close()
            }
        }
    }

    private fun Response.contentRangeStart(): Long? = header(HTTP_CONTENT_RANGE_HEADER)
        ?.substringAfter("bytes ", missingDelimiterValue = "")
        ?.substringBefore('-')
        ?.toLongOrNull()

    private fun Response.totalBytes(downloadedBytes: Long): Long? = header(HTTP_CONTENT_RANGE_HEADER)
        ?.substringAfter('/', missingDelimiterValue = "")
        ?.takeUnless { it == "*" }
        ?.toLongOrNull()
        ?: body?.contentLength()?.takeIf { it >= 0L }?.let(downloadedBytes::plus)

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 8 * 1024
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_RANGE_HEADER = "Range"
        const val HTTP_CONTENT_RANGE_HEADER = "Content-Range"
    }
}
