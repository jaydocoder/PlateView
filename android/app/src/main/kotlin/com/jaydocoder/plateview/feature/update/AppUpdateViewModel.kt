package com.jaydocoder.plateview.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.UpdateCheckResult
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import com.jaydocoder.plateview.feature.auth.AuthSession
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import com.jaydocoder.plateview.data.network.AppErrorMapper
import com.jaydocoder.plateview.data.network.AppErrorTelemetry
import com.jaydocoder.plateview.data.network.displayText
import com.jaydocoder.plateview.data.network.rethrowIfCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val update: AppUpdate? = null,
    val isChecking: Boolean = false,
    val isUpdateDialogVisible: Boolean = false,
    val isForceUpdate: Boolean = false,
    val isForceUpdateUnavailable: Boolean = false,
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
    private val sessionProvider: AuthSessionProvider,
    private val promptStateRepository: UpdatePromptStateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()
    private var lastCheckAtEpochMillis = 0L
    private var queryScreenVisible = false
    private var latestCheckResult: UpdateCheckResult? = null

    fun onQueryScreenVisible() {
        queryScreenVisible = true
        viewModelScope.launch {
            presentLatestCheckResultIfNeeded(sessionProvider.session.first())
            checkForUpdate()
        }
    }

    fun onQueryScreenHidden() {
        queryScreenVisible = false
    }

    fun checkForUpdate() {
        if (_uiState.value.isChecking) return
        val now = System.currentTimeMillis()
        if (now - lastCheckAtEpochMillis < CHECK_INTERVAL_MILLIS) return
        lastCheckAtEpochMillis = now
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            runCatching { repository.checkForUpdate() }
                .onSuccess { result ->
                    latestCheckResult = result
                    applyCheckResult(result, sessionProvider.session.first())
                }
                .onFailure { throwable ->
                    throwable.rethrowIfCancellation()
                    latestCheckResult = UpdateCheckResult.Unavailable
                    val appError = AppErrorMapper.map("检查更新", throwable)
                    AppErrorTelemetry.report(appError)
                    val isForceUpdate = sessionProvider.session.first()?.updatePolicy == "FORCED"
                    _uiState.update { current ->
                        val shouldShowManualResult = current.isManualCheckDialogVisible
                        current.copy(
                            isChecking = false,
                            isForceUpdate = isForceUpdate,
                            isForceUpdateUnavailable = isForceUpdate && queryScreenVisible,
                            isUpdateDialogVisible = current.isUpdateDialogVisible || (isForceUpdate && queryScreenVisible),
                            isManualCheckDialogVisible = shouldShowManualResult && !isForceUpdate,
                            manualCheckState = if (shouldShowManualResult && !isForceUpdate) {
                                ManualUpdateCheckState.Failed(appError.displayText())
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
        if (_uiState.value.update == null && !_uiState.value.isForceUpdateUnavailable) return
        _uiState.update { it.copy(isUpdateDialogVisible = true) }
    }

    fun downloadUpdate() {
        val update = _uiState.value.update ?: return
        if (_uiState.value.downloadState is UpdateDownloadState.Downloading) return
        viewModelScope.launch {
            val session = sessionProvider.session.first()
            if (!_uiState.value.isForceUpdate && session != null) {
                promptStateRepository.markHandled(session.userId, update.versionName)
            }
            _uiState.update { it.copy(downloadState = UpdateDownloadState.Downloading(UpdateDownloadProgress(0, null))) }
            runCatching {
                repository.download(update) { progress ->
                    _uiState.update { it.copy(downloadState = UpdateDownloadState.Downloading(progress)) }
                }
            }.onSuccess { apkFile ->
                _uiState.update { it.copy(downloadState = UpdateDownloadState.ReadyToInstall(apkFile)) }
            }.onFailure { throwable ->
                throwable.rethrowIfCancellation()
                val appError = AppErrorMapper.map("下载更新", throwable)
                AppErrorTelemetry.report(appError)
                _uiState.update {
                    it.copy(downloadState = UpdateDownloadState.Failed(appError.displayText()))
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        val state = _uiState.value
        if (state.isForceUpdate || state.downloadState is UpdateDownloadState.Downloading) return
        viewModelScope.launch {
            val update = _uiState.value.update
            sessionProvider.session.first()?.let { session ->
                update?.let { promptStateRepository.markHandled(session.userId, it.versionName) }
            }
            _uiState.update { it.copy(isUpdateDialogVisible = false) }
        }
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

    fun retryForcedUpdateCheck() {
        lastCheckAtEpochMillis = 0L
        checkForUpdate()
    }

    private suspend fun applyCheckResult(result: UpdateCheckResult, session: AuthSession?) {
        val forcedSession = session?.takeIf { it.updatePolicy == "FORCED" }
        val isForceUpdate = forcedSession != null
        when (result) {
            is UpdateCheckResult.Available -> {
                forcedSession?.let { promptStateRepository.cacheForcedUpdate(it.userId, result.update) }
                val shouldPrompt = shouldPromptFor(result.update, session, isForceUpdate)
                _uiState.update { current ->
                    val manualCheck = current.isManualCheckDialogVisible
                    current.copy(
                        update = result.update,
                        isChecking = false,
                        isForceUpdate = isForceUpdate,
                        isForceUpdateUnavailable = false,
                        isUpdateDialogVisible = current.isUpdateDialogVisible || manualCheck || shouldPrompt,
                        isManualCheckDialogVisible = false,
                        manualCheckState = ManualUpdateCheckState.Idle,
                        downloadState = if (current.update == result.update) current.downloadState else UpdateDownloadState.Idle,
                    )
                }
            }

            UpdateCheckResult.UpToDate -> {
                if (session != null) promptStateRepository.clearCachedForcedUpdate(session.userId)
                _uiState.update { current ->
                    val manualCheck = current.isManualCheckDialogVisible
                    current.copy(
                        update = null,
                        isChecking = false,
                        isForceUpdate = false,
                        isForceUpdateUnavailable = false,
                        isUpdateDialogVisible = false,
                        isManualCheckDialogVisible = manualCheck,
                        manualCheckState = if (manualCheck) ManualUpdateCheckState.Latest else ManualUpdateCheckState.Idle,
                        downloadState = UpdateDownloadState.Idle,
                    )
                }
            }

            UpdateCheckResult.Unavailable -> {
                val cached = forcedSession?.let { promptStateRepository.cachedForcedUpdate(it.userId) }
                _uiState.update { current ->
                    val manualCheck = current.isManualCheckDialogVisible
                    current.copy(
                        update = cached ?: current.update,
                        isChecking = false,
                        isForceUpdate = isForceUpdate,
                        isForceUpdateUnavailable = isForceUpdate && cached == null && queryScreenVisible,
                        isUpdateDialogVisible = current.isUpdateDialogVisible || (isForceUpdate && queryScreenVisible),
                        isManualCheckDialogVisible = manualCheck && !isForceUpdate,
                        manualCheckState = if (manualCheck && !isForceUpdate) ManualUpdateCheckState.Failed("无法连接更新服务，请检查网络后重试") else ManualUpdateCheckState.Idle,
                    )
                }
            }
        }
    }

    private suspend fun presentLatestCheckResultIfNeeded(session: AuthSession?) {
        if (!queryScreenVisible || session == null) return
        latestCheckResult?.let { result ->
            applyCheckResult(result, session)
            return
        }
        val isForceUpdate = session.updatePolicy == "FORCED"
        val knownUpdate = _uiState.value.update
        if (knownUpdate != null) {
            if (shouldPromptFor(knownUpdate, session, isForceUpdate)) {
                _uiState.update {
                    it.copy(isForceUpdate = isForceUpdate, isForceUpdateUnavailable = false, isUpdateDialogVisible = true)
                }
            }
            return
        }
        if (isForceUpdate) {
            val cached = promptStateRepository.cachedForcedUpdate(session.userId) ?: return
            _uiState.update {
                it.copy(update = cached, isForceUpdate = true, isForceUpdateUnavailable = false, isUpdateDialogVisible = true)
            }
        }
    }

    private suspend fun shouldPromptFor(update: AppUpdate, session: AuthSession?, isForceUpdate: Boolean): Boolean {
        if (!queryScreenVisible || session == null) return false
        if (isForceUpdate) return true
        return promptStateRepository.handledVersion(session.userId) != update.versionName
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}
