package com.jaydocoder.plateview.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val update: AppUpdate? = null,
    val isChecking: Boolean = false,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: UpdateDownloadProgress) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()
    private var lastCheckAtEpochMillis = 0L

    fun checkForUpdate() {
        if (_uiState.value.isChecking || _uiState.value.update != null) return
        val now = System.currentTimeMillis()
        if (now - lastCheckAtEpochMillis < CHECK_INTERVAL_MILLIS) return
        lastCheckAtEpochMillis = now
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            val update = runCatching { repository.findAvailableUpdate() }.getOrNull()
            _uiState.update { it.copy(update = update, isChecking = false) }
        }
    }

    fun downloadUpdate() {
        val update = _uiState.value.update ?: return
        if (_uiState.value.downloadState is UpdateDownloadState.Downloading) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadState = UpdateDownloadState.Downloading(UpdateDownloadProgress(0, null))) }
            runCatching {
                repository.download(update) { progress ->
                    _uiState.update { it.copy(downloadState = UpdateDownloadState.Downloading(progress)) }
                }
            }.onSuccess { apkFile ->
                _uiState.update { it.copy(downloadState = UpdateDownloadState.ReadyToInstall(apkFile)) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(downloadState = UpdateDownloadState.Failed(throwable.message ?: "下载更新失败，请稍后重试"))
                }
            }
        }
    }

    fun dismissUpdate() {
        if (_uiState.value.downloadState is UpdateDownloadState.Downloading) return
        _uiState.value = AppUpdateUiState()
    }

    fun reportInstallationFailure(message: String) {
        _uiState.update { it.copy(downloadState = UpdateDownloadState.Failed(message)) }
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}
