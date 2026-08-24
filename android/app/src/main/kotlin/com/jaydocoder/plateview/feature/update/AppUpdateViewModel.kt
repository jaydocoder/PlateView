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
    val isUpdateDialogVisible: Boolean = false,
    val isManualCheckDialogVisible: Boolean = false,
    val manualCheckState: ManualUpdateCheckState = ManualUpdateCheckState.Idle,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
)

sealed interface ManualUpdateCheckState {
    data object Idle : ManualUpdateCheckState
    data object Checking : ManualUpdateCheckState
    data object Latest : ManualUpdateCheckState
    data class Failed(val message: String) : ManualUpdateCheckState
}

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
        if (_uiState.value.isChecking) return
        val now = System.currentTimeMillis()
        if (now - lastCheckAtEpochMillis < CHECK_INTERVAL_MILLIS) return
        lastCheckAtEpochMillis = now
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            runCatching { repository.findAvailableUpdate() }
                .onSuccess { update ->
                    _uiState.update { current ->
                        val shouldShowManualResult = current.isManualCheckDialogVisible
                        current.copy(
                            update = update,
                            isChecking = false,
                            isUpdateDialogVisible = (current.isUpdateDialogVisible || shouldShowManualResult) && update != null,
                            isManualCheckDialogVisible = shouldShowManualResult && update == null,
                            manualCheckState = if (shouldShowManualResult && update == null) {
                                ManualUpdateCheckState.Latest
                            } else {
                                ManualUpdateCheckState.Idle
                            },
                            downloadState = if (current.update == update) {
                                current.downloadState
                            } else {
                                UpdateDownloadState.Idle
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        val shouldShowManualResult = current.isManualCheckDialogVisible
                        current.copy(
                            isChecking = false,
                            isManualCheckDialogVisible = shouldShowManualResult,
                            manualCheckState = if (shouldShowManualResult) {
                                ManualUpdateCheckState.Failed(throwable.message ?: "检查更新失败，请稍后重试")
                            } else {
                                ManualUpdateCheckState.Idle
                            },
                        )
                    }
                }
        }
    }

    fun checkForUpdateFromUser() {
        _uiState.update {
            it.copy(
                isManualCheckDialogVisible = true,
                manualCheckState = ManualUpdateCheckState.Checking,
            )
        }
        if (!_uiState.value.isChecking) {
            lastCheckAtEpochMillis = 0L
            checkForUpdate()
        }
    }

    fun openUpdateDialog() {
        if (_uiState.value.update == null) return
        _uiState.update { it.copy(isUpdateDialogVisible = true) }
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

    fun dismissUpdateDialog() {
        if (_uiState.value.downloadState is UpdateDownloadState.Downloading) return
        _uiState.update { it.copy(isUpdateDialogVisible = false) }
    }

    fun dismissManualCheckDialog() {
        if (_uiState.value.manualCheckState is ManualUpdateCheckState.Checking) return
        _uiState.update {
            it.copy(
                isManualCheckDialogVisible = false,
                manualCheckState = ManualUpdateCheckState.Idle,
            )
        }
    }

    fun reportInstallationFailure(message: String) {
        _uiState.update { it.copy(downloadState = UpdateDownloadState.Failed(message)) }
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}
