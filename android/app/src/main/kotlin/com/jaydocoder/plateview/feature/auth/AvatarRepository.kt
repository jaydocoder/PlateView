package com.jaydocoder.plateview.feature.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

private val Context.avatarCacheDataStore by preferencesDataStore("avatar_cache")

data class AvatarCacheEntry(
    val file: File?,
    val contentType: String?,
    val version: Long,
)

@Singleton
class AvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AuthApi,
    private val authRepository: AuthRepository,
) {
    fun observe(userId: Long): Flow<AvatarCacheEntry> = context.avatarCacheDataStore.data.map { preferences ->
        val version = preferences[versionKey(userId)] ?: 0L
        val contentType = preferences[contentTypeKey(userId)]
        AvatarCacheEntry(cachedFile(userId, contentType), contentType, version)
    }

    suspend fun synchronize(session: AuthSession): ProfileDto {
        val profile = api.profile(bearer(session.accessToken))
        val current = currentEntry(session.userId)
        if (!profile.hasAvatar) {
            if (current.file != null || current.version != profile.avatarVersion) clear(session.userId, profile.avatarVersion)
            authRepository.updateAvatarVersion(profile.avatarVersion)
            return profile
        }
        if (current.file != null && current.version == profile.avatarVersion) return profile

        val response = api.downloadAvatar(bearer(session.accessToken))
        val contentType = response.contentType()?.toString()?.substringBefore(';')
            ?.takeIf(::isSupportedContentType)
            ?: throw IllegalStateException("服务器返回了不支持的头像格式")
        val temporary = File(avatarDirectory(), "avatar-${session.userId}.download")
        response.byteStream().use { input ->
            temporary.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        check(temporary.length() in 1..MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件大小无效" }
        replaceCache(session.userId, temporary, contentType, profile.avatarVersion)
        authRepository.updateAvatarVersion(profile.avatarVersion)
        return profile
    }

    suspend fun upload(session: AuthSession, file: File, contentType: String): ProfileDto {
        require(file.length() in 1..MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件不能超过10MiB" }
        require(isSupportedContentType(contentType)) { "仅支持JPEG、PNG、WebP、GIF或BMP格式的头像" }
        val part = MultipartBody.Part.createFormData(
            name = "avatar",
            filename = "avatar.${extensionFor(contentType)}",
            body = file.asRequestBody(contentType.toMediaType()),
        )
        val profile = api.uploadAvatar(bearer(session.accessToken), part)
        val temporary = File(avatarDirectory(), "avatar-${session.userId}.upload")
        file.copyTo(temporary, overwrite = true)
        replaceCache(session.userId, temporary, contentType, profile.avatarVersion)
        authRepository.updateAvatarVersion(profile.avatarVersion)
        return profile
    }

    suspend fun delete(session: AuthSession): ProfileDto {
        val profile = api.deleteAvatar(bearer(session.accessToken))
        clear(session.userId, profile.avatarVersion)
        authRepository.updateAvatarVersion(profile.avatarVersion)
        return profile
    }

    private suspend fun currentEntry(userId: Long): AvatarCacheEntry = observe(userId).first()

    private suspend fun replaceCache(userId: Long, temporary: File, contentType: String, version: Long) {
        val target = File(avatarDirectory(), "avatar-$userId.${extensionFor(contentType)}")
        val replacement = File(avatarDirectory(), "avatar-$userId.${extensionFor(contentType)}.tmp")
        temporary.copyTo(replacement, overwrite = true)
        check(!target.exists() || target.delete()) { "无法替换本地头像缓存" }
        check(replacement.renameTo(target)) { "无法写入本地头像缓存" }
        temporary.delete()
        avatarDirectory().listFiles()?.filter { it.name.startsWith("avatar-$userId.") && it != target }?.forEach(File::delete)
        context.avatarCacheDataStore.edit { preferences ->
            preferences[versionKey(userId)] = version
            preferences[contentTypeKey(userId)] = contentType
        }
    }

    private suspend fun clear(userId: Long, version: Long) {
        avatarDirectory().listFiles()?.filter { it.name.startsWith("avatar-$userId.") }?.forEach(File::delete)
        context.avatarCacheDataStore.edit { preferences ->
            preferences[versionKey(userId)] = version
            preferences.remove(contentTypeKey(userId))
        }
    }

    private fun cachedFile(userId: Long, contentType: String?): File? = contentType
        ?.let { File(avatarDirectory(), "avatar-$userId.${extensionFor(it)}") }
        ?.takeIf(File::isFile)

    private fun avatarDirectory(): File = File(context.filesDir, "avatars").also { directory ->
        check(directory.exists() || directory.mkdirs()) { "无法创建头像缓存目录" }
    }

    private fun versionKey(userId: Long) = longPreferencesKey("avatar_${userId}_version")
    private fun contentTypeKey(userId: Long) = stringPreferencesKey("avatar_${userId}_content_type")

    private companion object {
        const val MAXIMUM_AVATAR_SIZE_BYTES = 10L * 1024 * 1024

        fun bearer(token: String): String = "Bearer $token"
        fun isSupportedContentType(value: String): Boolean = value in setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
        )
        fun extensionFor(value: String): String = when (value) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> error("不支持的头像格式")
        }
    }
}
