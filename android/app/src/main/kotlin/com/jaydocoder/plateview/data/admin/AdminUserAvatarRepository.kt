package com.jaydocoder.plateview.data.admin

import android.content.Context
import android.net.Uri
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

@Singleton
class AdminUserAvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AdminApi,
) {
    suspend fun load(accessToken: String, userId: Long, avatarVersion: Long, hasAvatar: Boolean): AvatarCacheEntry {
        if (!hasAvatar) return AvatarCacheEntry(null, null, avatarVersion)
        val directory = cacheDirectory()
        val cached = directory.listFiles()?.firstOrNull { it.name.startsWith("$userId-$avatarVersion.") }
        if (cached != null) return AvatarCacheEntry(cached, contentTypeFor(cached), avatarVersion)
        val response = api.downloadUserAvatar(bearer(accessToken), userId)
        val contentType = response.contentType()?.toString()?.substringBefore(';') ?: return AvatarCacheEntry(null, null, avatarVersion)
        if (contentType !in SUPPORTED_CONTENT_TYPES) return AvatarCacheEntry(null, null, avatarVersion)
        val target = File(directory, "$userId-$avatarVersion.${extensionFor(contentType)}")
        response.byteStream().use { input -> target.outputStream().buffered().use(input::copyTo) }
        directory.listFiles()?.filter { it.name.startsWith("$userId-") && it != target }?.forEach(File::delete)
        return AvatarCacheEntry(target, contentType, avatarVersion)
    }

    suspend fun upload(accessToken: String, userId: Long, version: Int, uri: Uri): Int {
        val contentType = context.contentResolver.getType(uri)?.takeIf { it in SUPPORTED_CONTENT_TYPES }
            ?: throw IllegalArgumentException("仅支持JPEG、PNG、WebP、GIF或BMP格式的头像")
        val source = File(cacheDirectory(), "$userId-upload.${extensionFor(contentType)}")
        context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().buffered().use(input::copyTo) }
            ?: throw IllegalArgumentException("无法读取头像文件")
        require(source.length() in 1..MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件不能超过10MiB" }
        return api.uploadUserAvatar(
            bearer(accessToken),
            version,
            userId,
            MultipartBody.Part.createFormData("avatar", source.name, source.asRequestBody(contentType.toMediaType())),
        ).version
    }

    suspend fun delete(accessToken: String, userId: Long, version: Int): Int {
        val nextVersion = api.deleteUserAvatar(bearer(accessToken), version, userId).version
        cacheDirectory().listFiles()?.filter { it.name.startsWith("$userId-") }?.forEach(File::delete)
        return nextVersion
    }

    private fun cacheDirectory(): File = File(context.cacheDir, "managed-avatars").also { directory ->
        check(directory.exists() || directory.mkdirs()) { "无法创建账号头像缓存目录" }
    }

    private fun contentTypeFor(file: File): String? = when (file.extension) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> null
    }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private companion object {
        const val MAXIMUM_AVATAR_SIZE_BYTES = 10L * 1024 * 1024
        val SUPPORTED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
        fun extensionFor(contentType: String) = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> error("不支持的头像格式")
        }
    }
}
