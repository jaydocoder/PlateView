package com.jaydocoder.plateview.data.workorder

import android.content.Context
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
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
    private val legacyMigrationLock = Any()

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
        val location = cacheLocation(userId, attachmentId, variant, sha256, sourceQuality)
        val mutex = downloadMutexes.getOrPut(location.lockKey) { Mutex() }
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
                    directory = location.directory,
                    cacheKey = location.cacheKey,
                    variant = variant,
                    expectedSha256 = sha256.takeIf { variant == "original" },
                    expectedSize = expectedSize,
                    onProgress = onProgress,
                )
            }
        } finally {
            downloadMutexes.remove(location.lockKey, mutex)
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
        val location = cacheLocation(userId, attachmentId, variant, sha256, sourceQuality)
        findCachedAttachmentFile(
            directory = location.directory,
            cacheKey = location.cacheKey,
            id = attachmentId,
            variant = variant,
            expectedSha256 = sha256.takeIf { variant == "original" },
            expectedSize = expectedSize,
        )?.let { return it }
        if (!location.shared) return null
        return adoptLegacyValidatedFile(
            userId = userId,
            attachmentId = attachmentId,
            variant = variant,
            sha256 = checkNotNull(sha256),
            sourceQuality = sourceQuality,
            expectedSize = expectedSize,
            target = location,
        )
    }

    fun directory(userId: Long): File =
        File(rootDirectory(), userId.toString()).ensureDirectory()

    fun clear(userId: Long?) {
        val root = rootDirectory()
        if (userId == null) root.deleteRecursively() else File(root, userId.toString()).deleteRecursively()
    }

    fun clearSharedFiles() {
        File(rootDirectory(), SHARED_DIRECTORY_NAME).deleteRecursively()
    }

    suspend fun clearAttachmentVersion(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
    ) {
        val location = cacheLocation(userId, attachmentId, variant, sha256, sourceQuality)
        if (location.shared) return
        val mutex = downloadMutexes.getOrPut(location.lockKey) { Mutex() }
        try {
            mutex.withLock {
                location.directory.listFiles().orEmpty()
                    .filter {
                        it.isFile && (it.nameWithoutExtension == location.cacheKey ||
                            it.name == "${location.cacheKey}.download" || it.name == "${location.cacheKey}.part")
                    }
                    .forEach(File::delete)
            }
        } finally {
            downloadMutexes.remove(location.lockKey, mutex)
        }
    }

    private fun cacheLocation(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String?,
        sourceQuality: String,
    ): AttachmentCacheLocation {
        val normalizedSha256 = sha256?.trim()?.lowercase()
        val shared = variant == ORIGINAL_VARIANT && normalizedSha256?.matches(SHA256_PATTERN) == true
        return if (shared) {
            val cacheKey = sharedAttachmentCacheKey(variant, checkNotNull(normalizedSha256))
            AttachmentCacheLocation(
                directory = File(rootDirectory(), SHARED_DIRECTORY_NAME).ensureDirectory(),
                cacheKey = cacheKey,
                lockKey = "$SHARED_DIRECTORY_NAME/$cacheKey",
                shared = true,
            )
        } else {
            val cacheKey = attachmentCacheKey(attachmentId, variant, sha256, sourceQuality)
            AttachmentCacheLocation(
                directory = directory(userId),
                cacheKey = cacheKey,
                lockKey = "$userId/$cacheKey",
                shared = false,
            )
        }
    }

    private fun adoptLegacyValidatedFile(
        userId: Long,
        attachmentId: Long,
        variant: String,
        sha256: String,
        sourceQuality: String,
        expectedSize: Long?,
        target: AttachmentCacheLocation,
    ): File? = synchronized(legacyMigrationLock) {
        findCachedAttachmentFile(target.directory, target.cacheKey, attachmentId, variant, sha256, expectedSize)?.let { return@synchronized it }
        val legacyKey = attachmentCacheKey(attachmentId, variant, sha256, sourceQuality)
        val root = rootDirectory()
        val candidates = buildList {
            add(File(root, userId.toString()))
            addAll(root.listFiles().orEmpty().filter { it.isDirectory && it.name != SHARED_DIRECTORY_NAME && it.name != userId.toString() })
        }
        val legacy = candidates.firstNotNullOfOrNull { directory ->
            findCachedAttachmentFile(directory, legacyKey, attachmentId, variant, sha256, expectedSize)
                ?.takeIf { it.matchesVerifiedContent(expectedSize, sha256) }
        } ?: return@synchronized null
        val destination = File(target.directory, "${target.cacheKey}.${legacy.extension.ifBlank { "bin" }}")
        val temporary = File(target.directory, "${target.cacheKey}.migrating")
        temporary.delete()
        legacy.copyTo(temporary, overwrite = true)
        if (!temporary.isFile || temporary.length() != legacy.length()) {
            temporary.delete()
            return@synchronized null
        }
        check(temporary.renameTo(destination) || run { temporary.copyTo(destination, overwrite = true); temporary.delete(); true }) {
            "无法迁移微信附件共享缓存"
        }
        destination
    }

    private fun rootDirectory(): File = File(context.filesDir, ROOT_DIRECTORY_NAME).ensureDirectory()

    private fun File.ensureDirectory(): File = also { check(it.exists() || it.mkdirs()) }

    private fun File.matchesVerifiedContent(expectedSize: Long?, expectedSha256: String): Boolean {
        if (!isFile || length() <= 0L || (expectedSize != null && length() != expectedSize)) return false
        val digest = inputStream().buffered().use { input ->
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                messageDigest.update(buffer, 0, read)
            }
            messageDigest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
        return digest.equals(expectedSha256, ignoreCase = true)
    }

    private data class AttachmentCacheLocation(
        val directory: File,
        val cacheKey: String,
        val lockKey: String,
        val shared: Boolean,
    )

    private companion object {
        const val ROOT_DIRECTORY_NAME = "work-order-images"
        const val SHARED_DIRECTORY_NAME = "shared"
        const val ORIGINAL_VARIANT = "original"
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

internal fun sharedAttachmentCacheKey(variant: String, sha256: String): String =
    "$variant-${sha256.trim().lowercase()}"
