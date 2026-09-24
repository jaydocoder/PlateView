package com.jaydocoder.plateview.data.workorder

import android.content.Context
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import retrofit2.Response

@Singleton
class WechatAttachmentCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun getOrDownload(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
        expectedSize: Long? = null,
        onProgress: suspend (Long) -> Unit = {},
        request: suspend (String?) -> Response<ResponseBody>,
    ): CachedWorkOrderImage {
        val cacheKey = attachmentCacheKey(attachmentId, variant, sha256, sourceQuality)
        val lockKey = "$userId/$cacheKey"
        val mutex = downloadMutexes.getOrPut(lockKey) { Mutex() }
        return try {
            mutex.withLock {
                findCached(
                    userId = userId,
                    attachmentId = attachmentId,
                    variant = variant,
                    sha256 = sha256,
                    sourceQuality = sourceQuality,
                    expectedSize = expectedSize,
                )?.let { return@withLock CachedWorkOrderImage(it, variant) }
                downloadAttachmentVariant(
                    request = request,
                    directory = directory(userId),
                    cacheKey = cacheKey,
                    variant = variant,
                    expectedSha256 = sha256.takeIf { variant == "original" },
                    expectedSize = expectedSize,
                    onProgress = onProgress,
                )
            }
        } finally {
            downloadMutexes.remove(lockKey, mutex)
        }
    }

    fun findCached(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
        expectedSize: Long? = null,
    ): File? {
        val cacheKey = attachmentCacheKey(attachmentId, variant, sha256, sourceQuality)
        return findCachedAttachmentFile(
            directory = directory(userId),
            cacheKey = cacheKey,
            id = attachmentId,
            variant = variant,
            expectedSha256 = sha256.takeIf { variant == "original" },
            expectedSize = expectedSize,
        )
    }

    fun directory(userId: Long): File =
        File(context.filesDir, "work-order-images/$userId").also { check(it.exists() || it.mkdirs()) }

    fun clear(userId: Long?) {
        val root = File(context.filesDir, "work-order-images")
        if (userId == null) root.deleteRecursively() else File(root, userId.toString()).deleteRecursively()
    }

    suspend fun clearAttachmentVersion(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
    ) {
        val cacheKey = attachmentCacheKey(attachmentId, variant, sha256, sourceQuality)
        val lockKey = "$userId/$cacheKey"
        val mutex = downloadMutexes.getOrPut(lockKey) { Mutex() }
        try {
            mutex.withLock {
                directory(userId).listFiles().orEmpty()
                    .filter { it.isFile && (it.nameWithoutExtension == cacheKey || it.name == "$cacheKey.download") }
                    .forEach(File::delete)
            }
        } finally {
            downloadMutexes.remove(lockKey, mutex)
        }
    }
}
