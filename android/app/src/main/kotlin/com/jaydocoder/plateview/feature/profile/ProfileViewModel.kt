package com.jaydocoder.plateview.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.feature.auth.AuthRepository
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AvatarCacheEntry
import com.jaydocoder.plateview.feature.auth.AvatarRepository
import com.jaydocoder.plateview.feature.auth.ProfileUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProfileUiState(
    val username: String = "",
    val roleLabel: String = "",
    val avatar: AvatarCacheEntry = AvatarCacheEntry(null, null, 0L),
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                if (session == null) return@collectLatest
                _uiState.value = _uiState.value.copy(username = session.username, roleLabel = if (session.role == "ADMIN") "管理员" else "普通用户")
                launch { runCatching { avatarRepository.synchronize(session) } }
                avatarRepository.observe(session.userId).collect { entry -> _uiState.value = _uiState.value.copy(avatar = entry) }
            }
        }
    }

    fun uploadAvatar(uri: Uri) = viewModelScope.launch {
        runCatching {
            val session = requireNotNull(authRepository.session.first())
            val upload = prepareAvatar(uri)
            avatarRepository.upload(session, upload.first, upload.second)
        }.onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "上传头像失败") }
    }

    fun deleteAvatar() = viewModelScope.launch {
        runCatching {
            authRepository.session.first()?.let { session -> avatarRepository.delete(session) }
        }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "移除头像失败") }
    }

    fun updateProfile(username: String, currentPassword: String?, password: String?) = viewModelScope.launch {
        runCatching {
            val session = requireNotNull(authRepository.session.first())
            authRepository.updateProfile(session.accessToken, ProfileUpdateRequest(username = username, currentPassword = currentPassword, password = password))
            authRepository.logout()
        }.onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "更新账号资料失败") }
    }

    private fun prepareAvatar(uri: Uri): Pair<File, String> {
        val declaredType = context.contentResolver.getType(uri)?.lowercase()
        require(declaredType in SUPPORTED_TYPES) { "仅支持JPEG、PNG、WebP、GIF或BMP格式的头像" }
        val source = File(context.cacheDir, "avatar-source")
        context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use(input::copyTo) }
            ?: error("无法读取头像文件")
        require(source.length() in 1..MAXIMUM_AVATAR_SIZE_BYTES) { "头像文件不能超过10MiB" }
        if (declaredType == "image/gif") return source to declaredType
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source))
        val edge = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge)
        val output = File(context.cacheDir, "avatar-static.jpg")
        output.outputStream().use { stream -> check(cropped.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "头像转码失败" } }
        source.delete()
        return output to "image/jpeg"
    }

    private companion object {
        const val MAXIMUM_AVATAR_SIZE_BYTES = 10L * 1024 * 1024
        val SUPPORTED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
    }
}
